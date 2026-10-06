package io.github.wiiznokes.gitnote.ui.component.markdown

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reconhecimento de callout em cima da arvore do parser GFM, com as linhas
 * exatamente como estao na nota `P1 Pratica` de Parasitologia.
 */
class CalloutsTest {

    @Test
    fun calloutDeSucessoComTituloDaNota() {
        val fonte = "> [!success] ✅ Caiu em prova: 115 T1 Q1 · 112 T1 Q9 · Bizu A Q10\n"
        val callout = assertNotNull(detectarCallout(fonte, primeiraCitacao(fonte)))

        assertEquals("success", callout.tipo)
        assertEquals(FamiliaDeCallout.SUCESSO, callout.familia)
        assertFalse(callout.tituloVazio)
        assertEquals("✅ Caiu", fonte.substring(callout.fimDoMarcador, callout.fimDoMarcador + 6))
    }

    @Test
    fun citacaoComumNaoEhCallout() {
        val fonte = "> so uma citacao [!success] no meio\n"
        assertNull(detectarCallout(fonte, primeiraCitacao(fonte)))
    }

    @Test
    fun tipoEmMaiusculaEDobraSaoAceitos() {
        val fonte = "> [!WARNING]- Cuidado\n> corpo\n"
        val callout = assertNotNull(detectarCallout(fonte, primeiraCitacao(fonte)))
        assertEquals("warning", callout.tipo)
        assertEquals(FamiliaDeCallout.AVISO, callout.familia)
        assertTrue(fonte.substring(callout.fimDoMarcador).startsWith("Cuidado"))
    }

    @Test
    fun marcadorSemTituloMarcaTituloVazio() {
        val fonte = "> [!note]\n> corpo da nota\n"
        val callout = assertNotNull(detectarCallout(fonte, primeiraCitacao(fonte)))
        assertTrue(callout.tituloVazio)
    }

    @Test
    fun apelidosCaemNaFamiliaDoObsidian() {
        assertEquals(FamiliaDeCallout.SUCESSO, familiaDoTipo("done"))
        assertEquals(FamiliaDeCallout.AVISO, familiaDoTipo("caution"))
        assertEquals(FamiliaDeCallout.PERIGO, familiaDoTipo("error"))
        assertEquals(FamiliaDeCallout.NOTA, familiaDoTipo("inventado"))
    }

    @Test
    fun soOsNosDoMarcadorSaoEngolidos() {
        val fonte = "> [!success] ✅ Caiu em prova\n"
        val paragrafo = primeiraCitacao(fonte).children.first {
            it.type == MarkdownElementTypes.PARAGRAPH
        }
        val engolidos = paragrafo.children.filter { ehMarcadorDeCallout(fonte, it) }
        val restantes = paragrafo.children.filterNot { ehMarcadorDeCallout(fonte, it) }

        assertTrue(engolidos.isNotEmpty())
        assertEquals(
            "[!success]",
            engolidos.joinToString("") { fonte.substring(it.startOffset, it.endOffset) }.trim(),
        )
        assertTrue(
            restantes.joinToString("") { fonte.substring(it.startOffset, it.endOffset) }
                .trimStart().startsWith("✅ Caiu"),
        )
    }

    @Test
    fun paragrafoForaDeCitacaoNuncaPerdeTexto() {
        val fonte = "[!success] fora de citacao\n"
        val paragrafo = parse(fonte).children.first { it.type == MarkdownElementTypes.PARAGRAPH }
        assertTrue(paragrafo.children.none { ehMarcadorDeCallout(fonte, it) })
    }

    private fun primeiraCitacao(fonte: String): ASTNode =
        parse(fonte).children.first { it.type == MarkdownElementTypes.BLOCK_QUOTE }

    private fun parse(fonte: String): ASTNode =
        MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(fonte)
}
