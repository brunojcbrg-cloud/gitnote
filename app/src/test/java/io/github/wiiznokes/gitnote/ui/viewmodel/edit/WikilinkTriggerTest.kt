package io.github.wiiznokes.gitnote.ui.viewmodel.edit

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WikilinkTriggerTest {
    private fun gatilho(texto: String, cursor: Int = texto.length): GatilhoSugestaoWikilink? =
        gatilhoSugestaoWikilink(texto, TextRange(cursor))

    @Test
    fun `acha nota aberta na linha do cursor`() {
        assertEquals(
            GatilhoSugestaoWikilink(
                abertura = 5,
                inicioSubstituicao = 7,
                cursor = 11,
                alvo = "medu",
                consulta = "medu",
                secao = false,
            ),
            gatilho("Veja [[medu"),
        )
    }

    @Test
    fun `separa secao local e remota`() {
        val local = gatilho("[[#subst")
        assertEquals("", local?.alvo)
        assertEquals("subst", local?.consulta)
        assertEquals(3, local?.inicioSubstituicao)
        assertEquals(true, local?.secao)

        val remota = gatilho("[[Medula Espinal#subst")
        assertEquals("Medula Espinal", remota?.alvo)
        assertEquals("subst", remota?.consulta)
        assertEquals(17, remota?.inicioSubstituicao)
    }

    @Test
    fun `recusa fechamento embed apelido e selecao`() {
        assertNull(gatilho("[[Fechada]] depois"))
        assertNull(gatilho("![[imagem"))
        assertNull(gatilho("[[Nota|apelido"))
        assertNull(gatilhoSugestaoWikilink("[[medu", TextRange(2, 6)))
    }

    @Test
    fun `nao reaproveita abertura de outra linha`() {
        assertNull(gatilho("[[aberta\noutra linha"))
    }

    @Test
    fun `recusa cerca e codigo inline`() {
        assertNull(gatilho("```md\n[[medu"))
        assertNull(gatilho("`[[medu`", cursor = 7))
        assertEquals("medu", gatilho("`codigo` [[medu")?.consulta)
    }

    @Test
    fun `aceite troca so o trecho digitado e fecha com texto do contrato`() {
        val texto = "antes\r\n[[medu"
        val gatilho = requireNotNull(gatilho(texto))
        val resultado = aplicarSugestaoWikilink(
            EdicaoDeTexto(texto, TextRange(texto.length)),
            gatilho,
            "Medula Espinal",
        )

        assertEquals("antes\r\n[[Medula Espinal]]", resultado.texto)
        assertEquals(TextRange(resultado.texto.length), resultado.selecao)
    }

    @Test
    fun `tecla comum nao entra no caminho da sugestao`() {
        val anterior = EdicaoDeTexto("texto", TextRange(5))
        assertEquals(
            false,
            deveAtualizarSugestaoWikilink(
                anterior.texto,
                anterior.selecao,
                "textox",
                TextRange(6),
                false,
            ),
        )
        assertEquals(
            true,
            deveAtualizarSugestaoWikilink(
                anterior.texto,
                anterior.selecao,
                "texto[",
                TextRange(6),
                false,
            ),
        )
        assertEquals(
            true,
            deveAtualizarSugestaoWikilink(
                anterior.texto,
                anterior.selecao,
                "textox",
                TextRange(6),
                true,
            ),
        )
    }
}
