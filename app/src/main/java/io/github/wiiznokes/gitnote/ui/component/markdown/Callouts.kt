package io.github.wiiznokes.gitnote.ui.component.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.LocalMarkdownComponents
import com.mikepenz.markdown.compose.LocalMarkdownTypography
import com.mikepenz.markdown.compose.MarkdownElement
import com.mikepenz.markdown.compose.components.MarkdownComponent
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.elements.MarkdownParagraph
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode

/**
 * Callouts do Obsidian (`> [!success] Titulo`) no modo leitura.
 *
 * O parser GFM nao conhece callout: para ele e uma citacao comum cujo primeiro
 * paragrafo comeca com `[!tipo]`. Por isso o reconhecimento e feito em cima da
 * arvore ja pronta -- o componente de citacao pergunta [detectarCallout] e, se
 * for callout, desenha a caixa; o anotador pergunta [ehMarcadorDeCallout] e
 * engole o `[!tipo]`, que antes aparecia cru na tela.
 *
 * As familias e as cores seguem as do Obsidian, inclusive os apelidos
 * (`check`/`done` sao `success`, `caution` e `warning`, etc.). Tipo desconhecido
 * cai em nota, como no Obsidian.
 */
enum class FamiliaDeCallout {
    NOTA, RESUMO, INFO, TODO, DICA, SUCESSO, PERGUNTA, AVISO, FALHA, PERIGO, BUG, EXEMPLO, CITACAO,
}

/**
 * @param inicioDoParagrafo onde comeca o paragrafo que carrega o marcador.
 * @param fimDoMarcador primeiro offset depois do `[!tipo]`, do `+`/`-` de dobra e
 * dos espacos que o seguem -- e, quando a linha nao tem titulo, da quebra de linha.
 * @param tituloVazio a linha do marcador nao tem titulo: o Obsidian mostra o nome do tipo.
 */
data class Callout(
    val tipo: String,
    val familia: FamiliaDeCallout,
    val inicioDoParagrafo: Int,
    val fimDoMarcador: Int,
    val tituloVazio: Boolean,
)

private val MARCADOR_DE_CALLOUT = Regex("""^\[!([A-Za-z][\w-]*)\][+-]?[ \t]*(\r?\n)?""")

fun familiaDoTipo(tipo: String): FamiliaDeCallout = when (tipo.lowercase()) {
    "abstract", "summary", "tldr" -> FamiliaDeCallout.RESUMO
    "info" -> FamiliaDeCallout.INFO
    "todo" -> FamiliaDeCallout.TODO
    "tip", "hint", "important" -> FamiliaDeCallout.DICA
    "success", "check", "done" -> FamiliaDeCallout.SUCESSO
    "question", "help", "faq" -> FamiliaDeCallout.PERGUNTA
    "warning", "caution", "attention" -> FamiliaDeCallout.AVISO
    "failure", "fail", "missing" -> FamiliaDeCallout.FALHA
    "danger", "error" -> FamiliaDeCallout.PERIGO
    "bug" -> FamiliaDeCallout.BUG
    "example" -> FamiliaDeCallout.EXEMPLO
    "quote", "cite" -> FamiliaDeCallout.CITACAO
    else -> FamiliaDeCallout.NOTA
}

/** Callout que [citacao] representa, ou nulo se for citacao comum. */
fun detectarCallout(content: CharSequence, citacao: ASTNode): Callout? {
    if (citacao.type != MarkdownElementTypes.BLOCK_QUOTE) return null
    val primeiro = citacao.children.firstOrNull {
        it.type != MarkdownTokenTypes.BLOCK_QUOTE && it.type != MarkdownTokenTypes.WHITE_SPACE
    } ?: return null
    if (primeiro.type != MarkdownElementTypes.PARAGRAPH) return null
    val texto = content.subSequence(primeiro.startOffset, primeiro.endOffset)
    val achado = MARCADOR_DE_CALLOUT.find(texto) ?: return null
    val tipo = achado.groupValues[1].lowercase()
    val semResto = achado.range.last + 1 >= texto.length
    return Callout(
        tipo = tipo,
        familia = familiaDoTipo(tipo),
        inicioDoParagrafo = primeiro.startOffset,
        fimDoMarcador = primeiro.startOffset + achado.range.last + 1,
        tituloVazio = achado.groups[2] != null || semResto,
    )
}

/**
 * Se [child] (filho de um paragrafo, como o anotador o recebe) faz parte do
 * `[!tipo]` de um callout -- e portanto nao deve ser escrito.
 */
fun ehMarcadorDeCallout(content: CharSequence, child: ASTNode): Boolean {
    val paragrafo = child.parent ?: return false
    if (paragrafo.type != MarkdownElementTypes.PARAGRAPH) return false
    if (child.startOffset < paragrafo.startOffset) return false
    val citacao = paragrafo.parent ?: return false
    val callout = detectarCallout(content, citacao) ?: return false
    return callout.inicioDoParagrafo == paragrafo.startOffset &&
        child.endOffset <= callout.fimDoMarcador
}

/** Cores padrao do Obsidian (`--callout-*`), legiveis no tema claro e no escuro. */
fun corDoCallout(familia: FamiliaDeCallout): Color = when (familia) {
    FamiliaDeCallout.NOTA, FamiliaDeCallout.INFO, FamiliaDeCallout.TODO -> Color(0xFF086DDD)
    FamiliaDeCallout.RESUMO, FamiliaDeCallout.DICA -> Color(0xFF00BFBC)
    FamiliaDeCallout.SUCESSO -> Color(0xFF08B94E)
    FamiliaDeCallout.PERGUNTA, FamiliaDeCallout.AVISO -> Color(0xFFEC7500)
    FamiliaDeCallout.FALHA, FamiliaDeCallout.PERIGO, FamiliaDeCallout.BUG -> Color(0xFFE93147)
    FamiliaDeCallout.EXEMPLO -> Color(0xFF7852EE)
    FamiliaDeCallout.CITACAO -> Color(0xFF9E9E9E)
}

private fun iconeDoCallout(familia: FamiliaDeCallout): ImageVector = when (familia) {
    FamiliaDeCallout.NOTA -> Icons.Default.Edit
    FamiliaDeCallout.RESUMO -> Icons.Default.Description
    FamiliaDeCallout.INFO -> Icons.Default.Info
    FamiliaDeCallout.TODO -> Icons.Default.Checklist
    FamiliaDeCallout.DICA -> Icons.Default.Lightbulb
    FamiliaDeCallout.SUCESSO -> Icons.Default.Done
    FamiliaDeCallout.PERGUNTA -> Icons.Default.QuestionMark
    FamiliaDeCallout.AVISO -> Icons.Default.Warning
    FamiliaDeCallout.FALHA -> Icons.Default.Close
    FamiliaDeCallout.PERIGO -> Icons.Default.Error
    FamiliaDeCallout.BUG -> Icons.Default.BugReport
    FamiliaDeCallout.EXEMPLO -> Icons.AutoMirrored.Filled.List
    FamiliaDeCallout.CITACAO -> Icons.Default.FormatQuote
}

/** Envolve o componente de citacao: callout vira caixa, citacao comum segue igual. */
fun calloutOuCitacao(citacao: MarkdownComponent): MarkdownComponent = { model ->
    val callout = detectarCallout(model.content, model.node)
    if (callout == null) {
        citacao(model)
    } else {
        MarkdownCallout(model.content, model.node, callout)
    }
}

@Composable
fun MarkdownCallout(content: String, node: ASTNode, callout: Callout) {
    val cor = corDoCallout(callout.familia)
    val components = LocalMarkdownComponents.current
    val typography = LocalMarkdownTypography.current
    val estiloDoTitulo = typography.paragraph.copy(color = cor, fontWeight = FontWeight.SemiBold)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(cor.copy(alpha = 0.12f))
            .drawBehind { drawRect(cor, size = Size(3.dp.toPx(), size.height)) }
            .padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        node.children.forEach { child ->
            when {
                child.startOffset == callout.inicioDoParagrafo &&
                    child.type == MarkdownElementTypes.PARAGRAPH -> {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = iconeDoCallout(callout.familia),
                            contentDescription = callout.tipo,
                            tint = cor,
                            modifier = Modifier.padding(top = 2.dp).size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        if (callout.tituloVazio) {
                            Text(
                                text = callout.tipo.replaceFirstChar { it.uppercase() },
                                style = estiloDoTitulo,
                            )
                        } else {
                            MarkdownParagraph(content, child, style = estiloDoTitulo)
                        }
                    }
                    // Sem titulo, o resto do paragrafo (se houver) ja e o corpo.
                    if (callout.tituloVazio && child.endOffset > callout.fimDoMarcador) {
                        MarkdownParagraph(content, child)
                    }
                }

                child.type == MarkdownElementTypes.BLOCK_QUOTE ->
                    components.blockQuote(MarkdownComponentModel(content, child, typography))

                child.type == MarkdownTokenTypes.EOL ||
                    child.type == MarkdownTokenTypes.BLOCK_QUOTE ||
                    child.type == MarkdownTokenTypes.WHITE_SPACE -> Unit

                else -> MarkdownElement(child, components, content, includeSpacer = false)
            }
        }
    }
}
