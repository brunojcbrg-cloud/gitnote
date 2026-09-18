package io.github.wiiznokes.gitnote.ui.component.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.sp
import io.github.wiiznokes.gitnote.ui.theme.MarkdownColorScheme
import kotlin.test.Test

/**
 * Diagnostico do caso real: nota que comeca com DUAS crases (o Bruno quis tres).
 * So imprime; o conserto vem depois de saber o que a tela mostra de verdade.
 */
class BacktickDiagnosticTest {
    private val colors = MarkdownColorScheme(
        h1 = Color(0xFF000001),
        h2 = Color(0xFF000002),
        h3 = Color(0xFF000003),
        h4 = Color(0xFF000004),
        emphasis = Color(0xFF000005),
        emphasis2 = Color(0xFF000006),
        code = Color(0xFF000007),
        codeBackground = Color(0xFF000008),
        quote = Color(0xFF000009),
        listMarker = Color(0xFF00000A),
        link = Color(0xFF00000B),
        highlight = Color(0xFF00000C),
        highlightBackground = Color(0xFF00000D),
    )

    private val nota = "`` - lembrar de no final fazer uma tabela\n``\n# Diferencas entre paredes"

    @Test
    fun diagnosticoDaNotaComDuasCrases() {
        report("DUAS_CRASES", nota)
        report("TRES_CRASES", nota.replace("``", "```"))
        report("UMA_CRASE", "`x` texto")
    }

    private fun report(rotulo: String, fonte: String) {
        println("=== $rotulo fonte=${fonte.take(46).replace("\n", "\\n")}")

        MarkdownScanner.scan(fonte).forEach { span ->
            println(
                "  SPAN $rotulo kind=${span.kind} range=${span.range} " +
                    "markers=${span.markers} line=${span.line}",
            )
        }

        val resultado = MarkdownLivePreviewTransformation(
            colors = colors,
            activeLines = emptySet(),
            baseFontSize = 16.sp,
        ).filter(AnnotatedString(fonte))

        println("  TELA $rotulo = ${resultado.text.text.take(46).replace("\n", "\\n")}")
        println("  LEITURA $rotulo = ${preprocessWikilinksForReading(fonte).take(46).replace("\n", "\\n")}")

        val mapeamento = (0..minOf(8, resultado.text.length)).joinToString(" ") { visual ->
            "$visual->${resultado.offsetMapping.transformedToOriginal(visual)}"
        }
        println("  T2O $rotulo $mapeamento")

        // O que o Backspace apaga, para cada posicao visual do cursor.
        val apagados = (1..minOf(6, resultado.text.length)).joinToString(" ") { visual ->
            val original = resultado.offsetMapping.transformedToOriginal(visual)
            val alvo = original - 1
            val caractere = if (alvo in fonte.indices) fonte[alvo].toString() else "?"
            "cursor$visual:apaga[$alvo]='$caractere'"
        }
        println("  BACKSPACE $rotulo $apagados")
    }
}
