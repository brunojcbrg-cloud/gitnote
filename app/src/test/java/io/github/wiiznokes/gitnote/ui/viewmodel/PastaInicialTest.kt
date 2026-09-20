package io.github.wiiznokes.gitnote.ui.viewmodel

import org.junit.Test
import kotlin.test.assertEquals

/**
 * Fase C do handoff 10: qual pasta a grade abre no inicio. Cobre a prioridade pedida
 * pelo Bruno — lembrar a ultima pasta aberta vence a pasta padrao, que por sua vez
 * vence a raiz — sem precisar construir [GridViewModel] (bloqueado pelo GitManager,
 * ver ESTADO_10.md).
 */
class PastaInicialTest {

    @Test
    fun semPastaPadraoENaoLembrandoAbreARaiz() {
        assertEquals(
            "",
            PastaInicial.escolher(
                rememberLastOpenedFolder = false,
                lastOpenedFolder = "",
                pastaPadrao = "",
            )
        )
    }

    @Test
    fun comPastaPadraoENaoLembrandoAbreAPastaPadrao() {
        assertEquals(
            "06_Conhecimento",
            PastaInicial.escolher(
                rememberLastOpenedFolder = false,
                lastOpenedFolder = "",
                pastaPadrao = "06_Conhecimento",
            )
        )
    }

    @Test
    fun lembrandoAUltimaPastaVenceAPastaPadrao() {
        assertEquals(
            "Trabalho/Contratos",
            PastaInicial.escolher(
                rememberLastOpenedFolder = true,
                lastOpenedFolder = "Trabalho/Contratos",
                pastaPadrao = "06_Conhecimento",
            )
        )
    }

    @Test
    fun lembrandoMasSemUltimaPastaCaiParaAPastaPadrao() {
        assertEquals(
            "06_Conhecimento",
            PastaInicial.escolher(
                rememberLastOpenedFolder = true,
                lastOpenedFolder = "",
                pastaPadrao = "06_Conhecimento",
            )
        )
    }

    @Test
    fun lembrandoMasSemUltimaPastaNemPadraoAbreARaiz() {
        assertEquals(
            "",
            PastaInicial.escolher(
                rememberLastOpenedFolder = true,
                lastOpenedFolder = "",
                pastaPadrao = "",
            )
        )
    }
}
