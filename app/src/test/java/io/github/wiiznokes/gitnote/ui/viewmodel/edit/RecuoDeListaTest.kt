package io.github.wiiznokes.gitnote.ui.viewmodel.edit

import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pedido do Bruno em 21/09/2026: *"no app nao tem nenhum botao que me permita dar
 * o espaco semelhante ao dado com a tecla Tab, que faz uma linha ser organizada em
 * topico."*
 *
 * O recuo e uma tabulacao porque e o que o vault usa: medido em 21/09/2026, 1.489
 * itens de lista aninhados com tabulacao contra 515 com espacos, e e o que a tecla
 * Tab do Obsidian insere. Recuar com espacos faria a mesma nota aninhar de um jeito
 * no celular e de outro no computador.
 */
class RecuoDeListaTest {

    private fun edicao(texto: String, cursor: Int) = EdicaoDeTexto(texto, TextRange(cursor))

    private fun edicao(texto: String, inicio: Int, fim: Int) =
        EdicaoDeTexto(texto, TextRange(inicio, fim))

    @Test
    fun recuarUsaTabulacaoEDesrecuarDevolve() {
        val original = edicao("- item", 6)
        val recuado = onAumentarRecuo(original)
        assertEquals("\t- item", recuado.texto)
        assertEquals("- item", onDiminuirRecuo(recuado).texto)
    }

    @Test
    fun oCursorAcompanhaOTextoQueAndou() {
        val recuado = onAumentarRecuo(edicao("- item", 6))
        assertEquals(7, recuado.selecao.start, "o cursor tem de continuar depois de 'item'")
        assertEquals("\t- item", recuado.texto)
    }

    @Test
    fun recuaVariosNiveis() {
        var v = edicao("- item", 6)
        repeat(3) { v = onAumentarRecuo(v) }
        assertEquals("\t\t\t- item", v.texto)
        repeat(3) { v = onDiminuirRecuo(v) }
        assertEquals("- item", v.texto)
    }

    @Test
    fun desrecuarNoNivelZeroNaoMexeNaLinha() {
        assertEquals("- item", onDiminuirRecuo(edicao("- item", 0)).texto)
    }

    /** O vault tem 515 itens recuados com espaco; o botao tem de saber desfazer esses. */
    @Test
    fun desrecuaTambemOQueVeioEscritoComEspacos() {
        assertEquals("- item", onDiminuirRecuo(edicao("    - item", 0)).texto)
        assertEquals("* item", onDiminuirRecuo(edicao("  * item", 0)).texto)
    }

    /**
     * Recuar paragrafo o transformaria em bloco de codigo no markdown -- quatro
     * espacos ou uma tabulacao e a sintaxe de codigo indentado. O botao nao mexe.
     */
    @Test
    fun paragrafoComumNaoRecua() {
        assertEquals("texto normal", onAumentarRecuo(edicao("texto normal", 3)).texto)
        assertEquals("# Titulo", onAumentarRecuo(edicao("# Titulo", 3)).texto)
    }

    /** `\t> x` nao e citacao aninhada, e codigo. */
    @Test
    fun citacaoNaoRecua() {
        assertEquals("> citado", onAumentarRecuo(edicao("> citado", 3)).texto)
    }

    @Test
    fun valeParaOsQuatroTiposDeItem() {
        listOf("- a", "* a", "1. a", "- [ ] a", "- [x] a").forEach { linha ->
            assertEquals(
                "\t$linha",
                onAumentarRecuo(edicao(linha, linha.length)).texto,
                "tipo de item: $linha",
            )
        }
    }

    /** Selecao de varias linhas: todas andam juntas, como no Tab do Obsidian. */
    @Test
    fun aSelecaoInteiraRecuaDeUmaVez() {
        val texto = "- um\n- dois\n- tres"
        val recuado = onAumentarRecuo(edicao(texto, 0, texto.length))
        assertEquals("\t- um\n\t- dois\n\t- tres", recuado.texto)
        assertEquals(texto, onDiminuirRecuo(recuado).texto)
    }

    /** Linha em branco e paragrafo no meio da selecao ficam onde estao. */
    @Test
    fun oQueNaoEhItemFicaParadoNoMeioDaSelecao() {
        val texto = "- um\n\ntexto\n- dois"
        val recuado = onAumentarRecuo(edicao(texto, 0, texto.length))
        assertEquals("\t- um\n\ntexto\n\t- dois", recuado.texto)
    }

    /** O resto da nota nao pode se mexer. */
    @Test
    fun soALinhaDoCursorMuda() {
        val texto = "antes\n- item\ndepois"
        val recuado = onAumentarRecuo(edicao(texto, 8))
        assertEquals("antes\n\t- item\ndepois", recuado.texto)
    }
}
