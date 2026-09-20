package io.github.wiiznokes.gitnote.ui.screen.app.grid

import android.app.Application
import io.github.wiiznokes.gitnote.data.room.NoteFolder
import io.github.wiiznokes.gitnote.ui.screen.app.DrawerFolderModel
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fase K.1 do handoff 10: quais itens de navegacao aparecem acima das notas.
 *
 * Os numeros vem das tres piores pastas medidas no vault, que sao o caminho de uso
 * diario do Bruno e hoje abrem em branco: `06_Conhecimento` (6 subpastas, 0 notas
 * diretas — e a pasta padrao dele), `06_Conhecimento/Medicina` (5) e
 * `06_Conhecimento/Medicina/Materias Basicas` (10).
 *
 * A decisao e pura de proposito: construir o `GridViewModel` de verdade em teste
 * continua impossivel neste repositorio (`GitManager` carrega `git_wrapper` no init —
 * ver ESTADO_10.md, divergencia da Fase A).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PastasNaGradeTest {

    private fun subpastas(pastaMae: String, vararg nomes: String) = nomes.map { nome ->
        DrawerFolderModel(
            noteFolder = NoteFolder.new(relativePath = if (pastaMae.isEmpty()) nome else "$pastaMae/$nome"),
            noteCount = 0,
        )
    }

    private fun seisSubpastasDe06(): List<DrawerFolderModel> = subpastas(
        "06_Conhecimento",
        "Medicina", "Direito", "Idiomas", "Tecnologia", "Financas", "Diversos",
    )

    @Test
    fun pastaSoComSubpastasDeixaDeAbrirEmBranco() {
        val itens = PastasNaGrade.itens(
            pastaAtual = "06_Conhecimento",
            query = "",
            pastas = seisSubpastasDe06(),
        )

        assertEquals(6, itens.filterIsInstance<ItemDeNavegacao.Pasta>().size)
        assertTrue(
            itens.first() is ItemDeNavegacao.SubirUmNivel,
            "fora da raiz, o primeiro item e o de subir um nivel",
        )
        assertFalse(
            PastasNaGrade.mostrarVazio(itens, quantidadeDeNotas = 0, notasCarregando = false),
            "com 6 pastas na tela nao ha estado vazio",
        )
    }

    @Test
    fun asOutrasDuasPastasDoCaminhoDiarioTambem() {
        val medicina = PastasNaGrade.itens(
            pastaAtual = "06_Conhecimento/Medicina",
            query = "",
            pastas = subpastas(
                "06_Conhecimento/Medicina",
                "Materias Basicas", "Pre-Clinico", "Clinico", "Internato", "Residencia",
            ),
        )
        assertEquals(5, medicina.filterIsInstance<ItemDeNavegacao.Pasta>().size)

        val basicas = PastasNaGrade.itens(
            pastaAtual = "06_Conhecimento/Medicina/Materias Basicas",
            query = "",
            pastas = subpastas(
                "06_Conhecimento/Medicina/Materias Basicas",
                "Anatomia", "Fisiologia", "Bioquimica", "Histologia", "Embriologia",
                "Microbiologia", "Imunologia", "Patologia", "Farmacologia", "Genetica",
            ),
        )
        assertEquals(10, basicas.filterIsInstance<ItemDeNavegacao.Pasta>().size)
    }

    @Test
    fun oItemDeSubirUmNivelVoltaParaAMae() {
        val itens = PastasNaGrade.itens(
            pastaAtual = "06_Conhecimento/Medicina/Materias Basicas",
            query = "",
            pastas = emptyList(),
        )

        val subir = itens.filterIsInstance<ItemDeNavegacao.SubirUmNivel>().single()
        assertEquals("06_Conhecimento/Medicina", subir.destino)
    }

    @Test
    fun naRaizNaoHaItemDeSubirUmNivel() {
        val itens = PastasNaGrade.itens(
            pastaAtual = "",
            query = "",
            pastas = subpastas("", "06_Conhecimento", "00_Inbox"),
        )

        assertTrue(itens.none { it is ItemDeNavegacao.SubirUmNivel })
        assertEquals(2, itens.size)
    }

    /** K.1: buscando, as pastas somem — o resultado da busca e de notas e e recursivo. */
    @Test
    fun buscandoAsPastasSomem() {
        val itens = PastasNaGrade.itens(
            pastaAtual = "06_Conhecimento",
            query = "cranio",
            pastas = seisSubpastasDe06(),
        )

        assertTrue(itens.isEmpty(), "nem pasta nem item de subir durante a busca: $itens")
    }

    @Test
    fun pastaSemNadaMostraAMensagemDeVazio() {
        val itens = PastasNaGrade.itens(pastaAtual = "A/vazia", query = "", pastas = emptyList())

        assertTrue(
            PastasNaGrade.mostrarVazio(itens, quantidadeDeNotas = 0, notasCarregando = false),
            "sem pasta e sem nota, a mensagem tem que aparecer — tela em branco nunca mais",
        )
    }

    @Test
    fun aMensagemDeVazioNaoAparecePorEnquantoAsNotasCarregam() {
        val itens = PastasNaGrade.itens(pastaAtual = "A", query = "", pastas = emptyList())

        assertFalse(PastasNaGrade.mostrarVazio(itens, quantidadeDeNotas = 0, notasCarregando = true))
        assertFalse(PastasNaGrade.mostrarVazio(itens, quantidadeDeNotas = 3, notasCarregando = false))
    }

    @Test
    fun cadaItemTemChaveEstavelEUnica() {
        val itens = PastasNaGrade.itens(
            pastaAtual = "06_Conhecimento",
            query = "",
            pastas = seisSubpastasDe06(),
        )

        val chaves = itens.map { it.chave }
        assertEquals(chaves.size, chaves.toSet().size, "chave repetida quebra a lista preguicosa")
    }
}
