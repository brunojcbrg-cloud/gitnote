package io.github.wiiznokes.gitnote.ui.component.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Os casos vem das formulas que existem de verdade na aula de Microbiologia:
 * 133 ocorrencias, 35 formas distintas. Nada aqui e inventado.
 */
class LatexToUnicodeTest {

    private fun texto(latex: String) = LatexToUnicode.converter(latex).text

    @Test
    fun letrasGregasEhOCasoMaisComum() {
        assertEquals("\u03B2", texto("\\beta"))
        assertEquals("\u03B1", texto("\\alpha"))
        assertEquals("\u03B3", texto("\\gamma"))
        assertEquals("\u03BC", texto("\\mu"))
    }

    @Test
    fun relacoesMantemOEspacoQueSeguiuOComando() {
        // "\ge 14" colado leria pior na tela estreita do celular.
        assertEquals("\u2265 14 mm", texto("\\ge 14\\text{ mm}"))
        assertEquals("CIM \u2264 1 \u03BCg/mL", texto("\\text{CIM} \\le 1\\ \\mu\\text{g/mL}"))
        assertEquals("\u2192", texto("\\rightarrow"))
    }

    @Test
    fun textoLiteralNaoEhConvertido() {
        assertEquals("UFC/mL", texto("\\text{UFC/mL}"))
        assertEquals("AmpC", texto("\\text{AmpC}"))
        assertEquals("4 a 8 \u03BCg/mL", texto("4\\text{ a }8\\ \\mu\\text{g/mL}"))
    }

    @Test
    fun indicesViramFaixasDeSubscritoESobrescrito() {
        val agua = LatexToUnicode.converter("\\text{H}_2\\text{O}_2")
        assertEquals("H2O2", agua.text)
        assertEquals(listOf(1..1, 3..3), agua.subscripts)
        assertTrue(agua.superscripts.isEmpty())

        val calcio = LatexToUnicode.converter("\\text{Ca}^{2+}")
        assertEquals("Ca2+", calcio.text)
        assertEquals(listOf(2..3), calcio.superscripts)

        val auc = LatexToUnicode.converter("\\text{AUC}_{24}/\\text{CIM}")
        assertEquals("AUC24/CIM", auc.text)
        assertEquals(listOf(3..4), auc.subscripts)
    }

    @Test
    fun grauNaoViraSobrescritoDeCirculo() {
        val esterilizacao = LatexToUnicode.converter("121^\\circ\\text{C}")
        assertEquals("121\u00B0C", esterilizacao.text)
        assertTrue(esterilizacao.superscripts.isEmpty(), "O grau e um caractere, nao um indice")
    }

    @Test
    fun chavesDeAgrupamentoSomemEVirgulaProtegidaFica() {
        assertEquals("1,5 a 2 \u03BCg/mL", texto("1{,}5\\text{ a }2\\ \\mu\\text{g/mL}"))
        assertEquals("Vmax", texto("V_{max}"))
    }

    @Test
    fun fracaoESqrtTemFormaLegivel() {
        assertEquals("a/b", texto("\\frac{a}{b}"))
        assertEquals("\u221A2", texto("\\sqrt{2}"))
    }

    @Test
    fun setaComRotuloMantemORotulo() {
        val reacao = LatexToUnicode.converter("\\xrightarrow{\\text{Catalase}}")
        assertEquals("\u2192Catalase", reacao.text)
        assertEquals(listOf(1..8), reacao.superscripts)
    }

    @Test
    fun comandoDesconhecidoVoltaComoNomeEnuncaSomeNemLanca() {
        assertEquals("foobar", texto("\\foobar"))
        assertEquals("x", texto("\\foobar{x}").takeLast(1))
        assertTrue(texto("\\").isNotEmpty(), "Barra solta no fim nao pode virar excecao")
    }

    @Test
    fun nenhumaFormulaRealSobraComBarra() {
        val reais = listOf(
            "\\beta", "\\alpha", "\\gamma", "\\rightarrow", "O_2", "O_2^-", "H_2O_2",
            "\\text{O}_2", "\\text{K}^+", "10^5", "10^{10}", "1:10^8", "\\log_{10}",
            "\\text{Eh}", "T > \\text{CIM}", "\\sim 10^3", "80^\\circ\\text{C}",
            "\\text{AUC}_{24}/\\text{CIM} \\ge 400",
        )
        reais.forEach { formula ->
            val saida = texto(formula)
            assertTrue(saida.isNotBlank(), "Formula virou vazio: $formula")
            assertFalse(saida.contains('\\'), "Sobrou barra em: $formula -> $saida")
        }
    }
}
