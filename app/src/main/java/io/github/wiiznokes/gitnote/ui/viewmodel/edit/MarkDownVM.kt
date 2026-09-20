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
import io.github.wiiznokes.gitnote.ui.component.markdown.resolveWikilinkTargets
import io.github.wiiznokes.gitnote.ui.component.markdown.offsetOfLineStart
import io.github.wiiznokes.gitnote.ui.destination.EditParams
import io.github.wiiznokes.gitnote.ui.model.EditType
import io.github.wiiznokes.gitnote.ui.viewmodel.viewModelFactory
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

private const val TAG = "MarkDownVM"
private const val ESPERA_GRAVACAO_MS = 400L


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
        val newValue = markdownSmartEditor(content.value, v)
        super.onValueChange(newValue)
    }

    fun onTitle() {
        val newValue = onTitle(content.value)
        super.onValueChange(newValue)
    }

    fun onBold() {
        val newValue = addOrRemovePatternAtTheExtremitiesOfSelection(content.value, "**")
        super.onValueChange(newValue)
    }

    fun onItalic() {
        val newValue = addOrRemovePatternAtTheExtremitiesOfSelection(content.value, "_")
        super.onValueChange(newValue)
    }

    fun onCode() {
        val newValue = onCode(content.value)
        super.onValueChange(newValue)
    }

    fun onQuote() {
        val newValue = onQuote(content.value)
        //Log.d(TAG, "onQuote result: text=\"${v.text.replace("\n", "\\n")}\", start=${v.selection.start}, end=${v.selection.end}")
        super.onValueChange(newValue)
    }

    fun onLink() {
        val newValue = onLink(content.value)
        super.onValueChange(newValue)
    }

    fun onUnorderedList() {
        val newValue = onUnorderedList(content.value)
        super.onValueChange(newValue)
    }

    fun onNumberedList() {
        val newValue = onNumberedList(content.value)
        super.onValueChange(newValue)
    }

    fun onTaskList() {
        val newValue = onTaskList(content.value)
        super.onValueChange(newValue)
    }

    fun onTableInsert(columns: Int, bodyRows: Int) {
        val newValue = insertTable(content.value, columns, bodyRows)
        super.onValueChange(newValue)
    }

    fun onTableResize(columns: Int, bodyRows: Int) {
        val result = resizeTableAt(content.value, columns, bodyRows) ?: return
        super.onValueChange(result.value)
    }

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
        return doDisco
    }

    fun moveCursorToLine(line: Int) {
        updateSelection(TextRange(offsetOfLineStart(content.value.text, line)))
    }
}


@Composable
fun newMarkDownVM(editParams: EditParams): MarkDownVM {

    return when (editParams) {
        is EditParams.Idle -> viewModel<MarkDownVM>(
            factory = viewModelFactory {
                MarkDownVM(editParams.editType, editParams.note, editParams.section)
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
