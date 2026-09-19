package io.github.wiiznokes.gitnote.ui.component.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.sp
import io.github.wiiznokes.gitnote.ui.theme.MarkdownColorScheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MathRenderingTest {
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

    private fun transformar(fonte: String, linhasAtivas: Set<Int> = emptySet()): TransformedText =
        MarkdownLivePreviewTransformation(colors, linhasAtivas, 16.sp)
            .filter(AnnotatedString(fonte))

    // --- scanner -------------------------------------------------------------

    @Test
    fun reconheceAsDuasFormasDeDelimitador() {
        // Medido na aula de Microbiologia: 57 simples e 76 duplas no mesmo arquivo.
        val simples = MarkdownScanner.scan("halo de \\(\\ge 14\\) mm")
        val dupla = MarkdownScanner.scan("halo de \\\\(\\ge 14\\\\) mm")

        listOf(simples, dupla).forEach { spans ->
            val formula = spans.single { it.kind == MdKind.MATH }
            assertEquals("≥ 14", formula.math?.text)
        }
    }

    @Test
    fun indiceDentroDaFormulaNaoAbreItalico() {
        // Sem isto o "_" de \text{H}_2 abriria ITALIC e comeria metade da linha.
        val spans = MarkdownScanner.scan("agua oxigenada \\(\\text{H}_2\\text{O}_2\\) reage")

        assertEquals(listOf(MdKind.MATH), spans.map { it.kind })
        assertEquals("H2O2", spans.single().math?.text)
    }

    @Test
    fun delimitadorSemFechamentoFicaCru() {
        val spans = MarkdownScanner.scan("comecou \\(\\beta mas nao fechou")

        assertTrue(spans.none { it.kind == MdKind.MATH })
    }

    @Test
    fun formulaNaoAtravessaAOutraLinha() {
        val spans = MarkdownScanner.scan("abre \\(\\beta\nfecha \\) aqui")

        assertTrue(spans.none { it.kind == MdKind.MATH })
    }

    @Test
    fun formulaDentroDeCodigoEmLinhaFicaIntocada() {
        val spans = MarkdownScanner.scan("use `\\(\\beta\\)` no texto")

        assertTrue(spans.none { it.kind == MdKind.MATH })
        assertEquals(MdKind.INLINE_CODE, spans.single().kind)
    }

    // --- transformacao -------------------------------------------------------

    @Test
    fun formulaEhSubstituidaPeloTextoConvertido() {
        val fonte = "antibioticos \\(\\beta\\)-lactamicos"
        val resultado = transformar(fonte)

        assertEquals("antibioticos β-lactamicos", resultado.text.text)
    }

    @Test
    fun linhaDoCursorMostraAFormulaCrua() {
        val fonte = "antibioticos \\(\\beta\\)-lactamicos"
        val resultado = transformar(fonte, linhasAtivas = setOf(0))

        assertEquals(fonte, resultado.text.text)
    }

    @Test
    fun oDocumentoNuncaMuda() {
        // A transformacao e visual: o texto de origem nao e tocado em lugar nenhum.
        val fonte = "\\(\\text{H}_2\\text{O}_2\\) e \\\\(121^\\circ\\text{C}\\\\)"
        val antes = AnnotatedString(fonte)
        transformar(fonte)

        assertEquals(fonte, antes.text)
    }

    @Test
    fun indicesRecebemDeslocamentoDeLinhaDeBase() {
        val resultado = transformar("\\(\\text{H}_2\\text{O}_2\\)")

        assertEquals("H2O2", resultado.text.text)
        val deslocados = resultado.text.spanStyles.filter { it.item.baselineShift != null }
        assertEquals(2, deslocados.size)
        deslocados.forEach { assertEquals(BaselineShift.Subscript, it.item.baselineShift) }
        assertEquals(listOf(1, 3), deslocados.map { it.start }.sorted())
    }

    @Test
    fun grauNaoGeraDeslocamento() {
        val resultado = transformar("esterilizar a \\(121^\\circ\\text{C}\\) por 15 min")

        assertEquals("esterilizar a 121°C por 15 min", resultado.text.text)
        assertTrue(resultado.text.spanStyles.none { it.item.baselineShift != null })
    }

    @Test
    fun oMapaDePosicoesContinuaCoerenteEmTodaFaixa() {
        val fonte = "a \\(\\beta\\) b \\\\(\\text{K}^+\\\\) c"
        val resultado = transformar(fonte)
        val mapa = resultado.offsetMapping
        val tamanhoVisivel = resultado.text.text.length

        for (offset in 0..fonte.length) {
            val visivel = mapa.originalToTransformed(offset)
            assertTrue(
                visivel in 0..tamanhoVisivel,
                "posicao $offset saiu da faixa: $visivel",
            )
        }
        for (offset in 0..tamanhoVisivel) {
            val original = mapa.transformedToOriginal(offset)
            assertTrue(
                original in 0..fonte.length,
                "posicao visivel $offset voltou para fora: $original",
            )
        }
    }

    @Test
    fun formulaConviveComNegritoNaMesmaLinha() {
        val fonte = "**forte** e \\(\\alpha\\) junto"
        val resultado = transformar(fonte)

        assertEquals("forte e α junto", resultado.text.text)
    }

    @Test
    fun formulaSemConteudoNaoQuebra() {
        val resultado = transformar("vazia \\(\\) aqui")

        assertTrue(resultado.text.text.contains("vazia"))
    }

    @Test
    fun spanDeFormulaNaoTemWikilink() {
        val formula = MarkdownScanner.scan("\\(\\beta\\)").single()

        assertEquals(MdKind.MATH, formula.kind)
        assertNull(formula.wikilink)
    }

    // --- modo leitura --------------------------------------------------------

    @Test
    fun leituraConverteAFormulaEmTextoPuro() {
        assertEquals("β", preprocessWikilinksForReading("\(\beta\)"))
        assertEquals("agua H₂O₂ aqui", preprocessWikilinksForReading("agua \(\text{H}_2\text{O}_2\) aqui"))
    }

    @Test
    fun leituraUsaIndicesUnicodeJaQueNaoHaLinhaDeBase() {
        // O renderizador da leitura recebe String: o indice tem de virar caractere.
        assertEquals("1:10⁸", preprocessWikilinksForReading("\(1:10^8\)"))
        assertEquals("Ca²⁺", preprocessWikilinksForReading("\(\text{Ca}^{2+}\)"))
        assertEquals("AUC₂₄/CIM", preprocessWikilinksForReading("\(\text{AUC}_{24}/\text{CIM}\)"))
        assertEquals("Vₘₐₓ", preprocessWikilinksForReading("\(V_{max}\)"))
    }

    @Test
    fun leituraAceitaAFormaComBarraDupla() {
        assertEquals("≥ 14 mm", preprocessWikilinksForReading("\\(\ge 14\text{ mm}\\)"))
    }

    @Test
    fun leituraEEditorConcordamSobreOConteudo() {
        val fonte = "halo \(\ge 14\text{ mm}\) e \\(121^\circ\text{C}\\)"
        val naLeitura = preprocessWikilinksForReading(fonte)
        val noEditor = transformar(fonte).text.text

        // O texto e o mesmo; so a tecnica do indice muda entre os dois modos.
        assertEquals(naLeitura, noEditor)
    }

    @Test
    fun leituraNaoTocaFormulaDentroDeCodigo() {
        val fonte = "use `\(\beta\)` assim"

        assertEquals(fonte, preprocessWikilinksForReading(fonte))
    }

    @Test
    fun leituraNaoAlteraAFonte() {
        val fonte = "\(\beta\) e [[Nota]]"
        preprocessWikilinksForReading(fonte)

        assertEquals("\(\beta\) e [[Nota]]", fonte)
    }

    @Test
    fun formulaEWikilinkConvivemNaMesmaLinha() {
        val saida = preprocessWikilinksForReading("\(\alpha\) ver [[Nota]]")

        assertTrue(saida.startsWith("α ver ["), "saiu: $saida")
    }

    @Test
    fun grauNaLeituraNaoViraIndice() {
        assertEquals("121°C", preprocessWikilinksForReading("\(121^\circ\text{C}\)"))
    }

    @Test
    fun indiceSemEquivalenteUnicodeFicaNaLinha() {
        // Meio indice convertido leria pior que nenhum.
        assertEquals("Hβ", preprocessWikilinksForReading("\(\text{H}_{\beta}\)"))
    }
}
