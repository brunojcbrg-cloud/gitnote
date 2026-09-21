package io.github.wiiznokes.gitnote.ui.component.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WikilinkSuggestionTest {
    private val caminhos = listOf(
        "06_Conhecimento/Neuro/Medula Espinal.md",
        "06_Conhecimento/Neuro/Sem título.md",
        "06_Conhecimento/Outra/Meninges.md",
        "06_Conhecimento/Outra/Sem título.md",
        "06_Conhecimento/Outra/Árvore brônquica.md",
        "06_Conhecimento/Outra/Zinco.md",
    )

    @Test
    fun `prioriza comeco depois ocorrencia e depois o restante`() {
        val nomes = sugerirNotasParaWikilink(
            caminhos,
            "06_Conhecimento/Neuro/Atual.md",
            "med",
        ).map { it.nome }

        assertEquals(
            listOf(
                "Medula Espinal",
                "Sem título",
                "Árvore brônquica",
                "Meninges",
                "Sem título",
                "Zinco",
            ),
            nomes,
        )
    }

    @Test
    fun `ignora caixa e acentos sem fuzzy de letras soltas`() {
        assertEquals("Árvore brônquica", sugerirNotasParaWikilink(caminhos, "", "arvore").first().nome)
        assertEquals("Sem título", sugerirNotasParaWikilink(caminhos, "", "TÍTULO").first().nome)
        assertNotEquals("Medula Espinal", sugerirNotasParaWikilink(caminhos, "", "mdu").first().nome)
    }

    @Test
    fun `mantem homonimos e prefere a pasta atual sem esconder a outra`() {
        val repetidas = sugerirNotasParaWikilink(
            caminhos,
            "06_Conhecimento/Neuro/Atual.md",
            "sem título",
        ).filter { it.nome == "Sem título" }

        assertEquals(
            listOf(
                SugestaoDeNotaWikilink(
                    nome = "Sem título",
                    caminho = "06_Conhecimento/Neuro/Sem título.md",
                    pasta = "06_Conhecimento/Neuro",
                ),
                SugestaoDeNotaWikilink(
                    nome = "Sem título",
                    caminho = "06_Conhecimento/Outra/Sem título.md",
                    pasta = "06_Conhecimento/Outra",
                ),
            ),
            repetidas,
        )
    }
}
