package io.github.wiiznokes.gitnote.ui.viewmodel.edit

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.wiiznokes.gitnote.MyApp
import io.github.wiiznokes.gitnote.R
import io.github.wiiznokes.gitnote.data.PortaoDaAbertura
import io.github.wiiznokes.gitnote.data.room.Note
import io.github.wiiznokes.gitnote.data.room.WikilinkSuggestionCandidate
import io.github.wiiznokes.gitnote.ui.component.markdown.listarAnexos
import io.github.wiiznokes.gitnote.ui.component.markdown.resolveWikilinkTargets
import io.github.wiiznokes.gitnote.ui.component.markdown.sugerirNotasParaWikilink
import io.github.wiiznokes.gitnote.ui.component.markdown.resolverAnexoNoRepo
import io.github.wiiznokes.gitnote.ui.component.markdown.offsetOfLineStart
import io.github.wiiznokes.gitnote.ui.destination.EditParams
import io.github.wiiznokes.gitnote.ui.model.EditType
import io.github.wiiznokes.gitnote.ui.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "MarkDownVM"
private const val ESPERA_GRAVACAO_MS = 400L

internal fun editMarkdownValue(previous: TextFieldValue, value: TextFieldValue): TextFieldValue {
    val edited = markdownSmartEditor(previous.toEdicaoDeTexto(), value.toEdicaoDeTexto())
    // Automatic continuation clears the IME composition; padding deletion keeps it.
    val continuedLine = edited.texto != value.text &&
        value.selection.collapsed && value.text.getOrNull(value.selection.start - 1) == '\n'
    return edited.toTextFieldValue(value, clearComposition = continuedLine)
}

data class ItemSugestaoWikilink(
    val texto: String,
    val detalhe: String,
    val caminho: String,
)

data class EstadoSugestaoWikilink(
    val gatilho: GatilhoSugestaoWikilink? = null,
    val itens: List<ItemSugestaoWikilink> = emptyList(),
    val selecionado: Int = 0,
    val carregando: Boolean = false,
) {
    val visivel: Boolean get() = gatilho != null && (itens.isNotEmpty() || carregando)
}


class MarkDownVM : TextVM {

    private val dao = MyApp.appModule.repoDatabase.repoDatabaseDao
    private val uiHelper = MyApp.appModule.uiHelper

    private val posicoes = MyApp.appModule.posicoesDeLeitura

    private var initialSectionConsumed = false
    private var initialSection: String? = null
    private var anchorLine: Int? = null

    /** A posicao guardada so e lida uma vez: depois disso manda o que esta na tela. */
    private var posicaoDoDiscoLida = false

    /** Segura os zeros que a montagem da tela anuncia antes da retomada. */
    private val portaoDaAbertura = PortaoDaAbertura()
    private val linhaParaGravar = MutableStateFlow<Int?>(null)

    private val _posicaoTardia = MutableStateFlow<Int?>(null)

    private val _sugestaoWikilink = MutableStateFlow(EstadoSugestaoWikilink())
    val sugestaoWikilink: StateFlow<EstadoSugestaoWikilink> = _sugestaoWikilink.asStateFlow()
    private var candidatosDeWikilink: List<WikilinkSuggestionCandidate> = emptyList()
    private var caminhosDeWikilink: List<String> = emptyList()

    init {
        viewModelScope.launch {
            candidatosDeWikilink = withContext(Dispatchers.IO) {
                dao.wikilinkSuggestionCandidates()
            }
            caminhosDeWikilink = candidatosDeWikilink.map { it.relativePath }
            atualizarSugestaoWikilink(content.value)
        }
    }

    /**
     * Posicao que chegou tarde demais para a primeira composicao.
     *
     * No arranque frio o arquivo pode ainda nao ter aberto quando a tela monta, e
     * a leitura bloqueante volta vazia. A tela observa isto e rola quando o valor
     * chega -- desde que ele nao tenha mexido na rolagem nesse meio tempo.
     */
    val posicaoTardia: StateFlow<Int?> = _posicaoTardia.asStateFlow()

    @OptIn(FlowPreview::class)
    private val gravador = viewModelScope.launch {
        // Sem a espera, cada quadro de rolagem viraria uma escrita em disco.
        linhaParaGravar.filterNotNull().debounce(ESPERA_GRAVACAO_MS).collect { linha ->
            val caminho = caminhoDaNota()
            if (caminho.isNotBlank()) posicoes.guardar(caminho, linha)
        }
    }

    private fun caminhoDaNota(): String =
        runCatching { previousNote.relativePath }.getOrDefault("")

    constructor(
        editType: EditType,
        previousNote: Note,
        initialSection: String? = null,
    ) : super(editType, previousNote) {
        this.initialSection = initialSection
    }

    constructor(
        editType: EditType,
        previousNote: Note,
        name: String,
        content: String,
    ) : super(editType, previousNote, name, content)

    override fun onValueChange(v: TextFieldValue) {
        val editado = editMarkdownValue(content.value, v)
        super.onValueChange(editado)
        atualizarSugestaoWikilink(editado)
    }

    private fun atualizarSugestaoWikilink(valor: TextFieldValue) {
        val gatilho = gatilhoSugestaoWikilink(valor.text, valor.selection)
        if (gatilho == null || gatilho.secao) {
            _sugestaoWikilink.value = EstadoSugestaoWikilink()
            return
        }
        val itens = sugerirNotasParaWikilink(
            caminhos = caminhosDeWikilink,
            caminhoAtual = previousNote.relativePath,
            digitado = gatilho.consulta,
        ).map {
            ItemSugestaoWikilink(texto = it.nome, detalhe = it.pasta, caminho = it.caminho)
        }
        _sugestaoWikilink.value = EstadoSugestaoWikilink(gatilho = gatilho, itens = itens)
    }

    fun fecharSugestaoWikilink() {
        _sugestaoWikilink.value = EstadoSugestaoWikilink()
    }

    fun moverSelecaoWikilink(delta: Int) {
        val estado = _sugestaoWikilink.value
        if (!estado.visivel || estado.itens.isEmpty()) return
        val proximo = (estado.selecionado + delta).mod(estado.itens.size)
        _sugestaoWikilink.value = estado.copy(selecionado = proximo)
    }

    fun aceitarSugestaoWikilink(indice: Int = _sugestaoWikilink.value.selecionado) {
        val estado = _sugestaoWikilink.value
        val gatilho = estado.gatilho ?: return
        val item = estado.itens.getOrNull(indice) ?: return
        val original = content.value
        val editado = aplicarSugestaoWikilink(original.toEdicaoDeTexto(), gatilho, item.texto)
        super.onValueChange(editado.toTextFieldValue(original, clearComposition = true))
        fecharSugestaoWikilink()
    }

    private fun applyEdit(transform: (EdicaoDeTexto) -> EdicaoDeTexto) {
        val original = content.value
        super.onValueChange(transform(original.toEdicaoDeTexto()).toTextFieldValue(original))
    }

    fun onTitle() {
        applyEdit { onTitle(it) }
    }

    fun onBold() {
        applyEdit { addOrRemovePatternAtTheExtremitiesOfSelection(it, "**") }
    }

    fun onItalic() {
        applyEdit { addOrRemovePatternAtTheExtremitiesOfSelection(it, "_") }
    }

    fun onCode() {
        applyEdit { onCode(it) }
    }

    fun onQuote() {
        applyEdit { onQuote(it) }
    }

    fun onLink() {
        applyEdit { onLink(it) }
    }

    fun onUnorderedList() {
        applyEdit { onUnorderedList(it) }
    }

    fun onNumberedList() {
        applyEdit { onNumberedList(it) }
    }

    fun onTaskList() {
        applyEdit { onTaskList(it) }
    }

    fun onTableInsert(columns: Int, bodyRows: Int) {
        val original = content.value
        val edited = insertTable(original.toEdicaoDeTexto(), columns, bodyRows)
        super.onValueChange(edited.toTextFieldValue(original, clearComposition = true))
    }

    fun onTableResize(columns: Int, bodyRows: Int) {
        val original = content.value
        val result = resizeTableAt(original.toEdicaoDeTexto(), columns, bodyRows) ?: return
        super.onValueChange(result.value.toTextFieldValue(original, clearComposition = true))
    }

    /**
     * Raiz do repositorio, lida uma vez. A leitura bloqueante ja e o padrao da
     * casa (`PosicoesDeLeitura`, `SetupNav`): a tela precisa dela na composicao.
     */
    val raizDoRepo: String by lazy { prefs.repoPathSafely() }

    /** Anexos publicados, para resolver `![[nome.png]]` pelo nome (Fase I.2). */
    suspend fun anexosDisponiveis(): List<String> =
        withContext(Dispatchers.IO) { listarAnexos(raizDoRepo) }

    fun resolverAnexo(alvo: String, anexos: List<String>): String? =
        resolverAnexoNoRepo(raizDoRepo, anexos, alvo)

    suspend fun resolveWikilinks(names: Set<String>): Map<String, String?> {
        val candidates = dao.wikilinkCandidates(names)
        return resolveWikilinkTargets(
            names = names,
            currentParentPath = previousNote.parentPath(),
            candidatePaths = candidates.map { it.relativePath },
        )
    }

    fun openResolvedWikilink(
        relativePath: String,
        name: String,
        section: String?,
        onOpenNote: (Note, String?) -> Unit,
    ) {
        viewModelScope.launch {
            val note = dao.noteByRelativePath(relativePath)
            if (note == null) {
                showMissingWikilink(name)
            } else {
                onOpenNote(note, section)
            }
        }
    }

    fun showMissingWikilink(name: String) {
        uiHelper.makeToast(uiHelper.getString(R.string.error_wikilink_not_found, name))
    }

    fun showSectionNotFound(name: String) {
        uiHelper.makeToast(uiHelper.getString(R.string.error_wikilink_section_not_found, name))
    }

    fun pendingInitialSection(): String? = initialSection.takeUnless { initialSectionConsumed }

    fun consumeInitialSection() {
        initialSectionConsumed = true
    }

    fun rememberAnchor(line: Int) {
        val segura = line.coerceAtLeast(0)
        anchorLine = segura
        if (!portaoDaAbertura.deveGravar(segura)) return
        linhaParaGravar.value = segura
    }

    /**
     * Devolve a ancora da sessao; na primeira vez, cai para a que foi guardada em
     * disco. E o que faz a nota reabrir onde ele parou depois de o Android matar o
     * app -- que e o caso comum de trocar de aplicativo e voltar.
     */
    fun consumeAnchor(): Int? {
        val daSessao = anchorLine
        anchorLine = null
        if (daSessao != null) return daSessao
        if (posicaoDoDiscoLida) return null
        posicaoDoDiscoLida = true
        if (editType == EditType.Create) return null
        val doDisco = posicoes.linhaBloqueante(caminhoDaNota())
        portaoDaAbertura.retomouEm(doDisco)
        if (doDisco == null) buscarPosicaoTardia()
        return doDisco
    }

    private fun buscarPosicaoTardia() {
        viewModelScope.launch {
            val caminho = caminhoDaNota()
            if (caminho.isBlank()) return@launch
            val tardia = posicoes.linha(caminho) ?: return@launch
            if (tardia <= 0) return@launch
            // Ainda vale proteger o zero da montagem: a tela nem rolou ainda.
            portaoDaAbertura.retomouEm(tardia)
            _posicaoTardia.value = tardia
        }
    }

    fun moveCursorToLine(line: Int) {
        updateSelection(TextRange(offsetOfLineStart(content.value.text, line)))
    }
}


@Composable
fun newMarkDownVM(editParams: EditParams, note: Note): MarkDownVM {

    return when (editParams) {
        is EditParams.Idle -> viewModel<MarkDownVM>(
            factory = viewModelFactory {
                MarkDownVM(editParams.editType, note, editParams.section)
            }
        )

        is EditParams.Saved -> {
            viewModel<MarkDownVM>(
                factory = viewModelFactory {
                    MarkDownVM(
                        editType = editParams.editType,
                        previousNote = editParams.note,
                        name = editParams.name,
                        content = editParams.content,
                    )
                }
            )
        }
    }
}
