package io.github.wiiznokes.gitnote.ui.screen.app.grid

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Fase A do handoff 10: pediu para tirar o botao de flashcards da tela de Notas porque
 * `FlashcardViewModel.init` varre os 27 MB do vault (`notesContainingTag`) so para
 * pintar o numero no botao. Este teste nao compoe `GridScreen` de verdade porque
 * nenhum teste deste repositorio consegue: `GridViewModel` -> `StorageManager` ->
 * `GitManager` carrega `System.loadLibrary("git_wrapper")` no init do companion, e essa
 * lib nativa so existe dentro do APK. Guardamos a mesma garantia na fonte: abrir Notas
 * so pode disparar a varredura se `GridScreen`/`BottomGrid` ainda mencionarem
 * `FlashcardViewModel` ou `onFlashcardsClick`, e esse teste falha se alguem reintroduzir
 * qualquer um dos dois.
 */
class GridScreenFlashcardsRemovedTest {

    private val raizModulo = File(System.getProperty("user.dir") ?: ".")

    private fun lerFonte(caminhoRelativo: String): String {
        val arquivo = File(raizModulo, caminhoRelativo)
        check(arquivo.exists()) { "arquivo nao encontrado: ${arquivo.absolutePath}" }
        return arquivo.readText()
    }

    @Test
    fun gridScreenNaoReferenciaFlashcardViewModel() {
        val fonte = lerFonte("src/main/java/io/github/wiiznokes/gitnote/ui/screen/app/grid/GridScreen.kt")

        assertFalse(fonte.contains("FlashcardViewModel"), "GridScreen voltou a referenciar FlashcardViewModel")
        assertFalse(fonte.contains("onFlashcardsClick"), "GridScreen voltou a receber onFlashcardsClick")
        assertFalse(fonte.contains("notesContainingTag"), "GridScreen chama notesContainingTag diretamente")
    }

    @Test
    fun bottomGridNaoTemBotaoDeFlashcards() {
        val fonte = lerFonte("src/main/java/io/github/wiiznokes/gitnote/ui/screen/app/grid/BottomGrid.kt")

        assertFalse(fonte.contains("Flashcard"), "BottomGrid ainda tem algo de flashcards")
        assertFalse(fonte.contains("onFlashcardsClick"))
    }

    @Test
    fun aEntradaPelosFlashcardsContinuaSoNaHome() {
        val fonte = lerFonte("src/main/java/io/github/wiiznokes/gitnote/ui/screen/app/AppNav.kt")

        val ocorrencias = Regex("onFlashcardsClick").findAll(fonte).count()
        assertEquals(
            1,
            ocorrencias,
            "esperava 1 ocorrencia de onFlashcardsClick em AppNav.kt (so a Home), achei $ocorrencias",
        )
    }
}
