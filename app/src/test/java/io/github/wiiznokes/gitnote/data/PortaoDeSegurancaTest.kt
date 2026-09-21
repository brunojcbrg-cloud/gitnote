package io.github.wiiznokes.gitnote.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortaoDeSegurancaTest {
    @Test fun travaImediatamenteDepoisDeVoltar() {
        val portao = PortaoDeSeguranca()
        portao.aoParar(100, true)
        assertTrue(portao.deveRetravar(101, PrazoDaTrava.Imediato, true))
        assertFalse(portao.deveRetravar(102, PrazoDaTrava.Imediato, true))
    }

    @Test fun esperaPrazoETravaDesligadaNaoBloqueia() {
        val portao = PortaoDeSeguranca()
        portao.aoParar(100, true)
        assertFalse(portao.deveRetravar(60_099, PrazoDaTrava.UmMinuto, true))
        portao.aoParar(100, true)
        assertTrue(portao.deveRetravar(60_100, PrazoDaTrava.UmMinuto, true))
        portao.aoParar(100, true)
        assertFalse(portao.deveRetravar(300_100, PrazoDaTrava.Imediato, false))
    }

    @Test fun naoContaParadaAntesDaAutenticacao() {
        val portao = PortaoDeSeguranca()
        portao.aoParar(100, false)
        assertFalse(portao.deveRetravar(200, PrazoDaTrava.Imediato, true))
    }

    /**
     * EnumPreference grava `value.name`, nunca o rótulo: o rótulo agora vem de um recurso e
     * mudaria com o idioma. Renomear uma constante aqui perde a preferência já gravada.
     */
    @Test fun oPrazoEhPersistidoPeloNomeDaConstante() {
        assertEquals(
            listOf("Imediato", "UmMinuto", "CincoMinutos"),
            PrazoDaTrava.entries.map { it.name }
        )
        assertEquals(
            listOf(0L, 60_000L, 300_000L),
            PrazoDaTrava.entries.map { it.milissegundos }
        )
    }
}
