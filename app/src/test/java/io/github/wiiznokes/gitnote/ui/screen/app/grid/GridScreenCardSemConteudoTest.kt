package io.github.wiiznokes.gitnote.ui.screen.app.grid

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fase B (B.4) do handoff 10: o card da grade deixa de renderizar o markdown/texto
 * do arquivo inteiro (`gridNote.note.content`) e passa a mostrar so titulo + data,
 * igual a `ListView.kt:NoteListRow`. Nao da para compor `GridScreen`/`NoteCard` de
 * verdade neste repositorio porque `GridViewModel` -> `StorageManager` -> `GitManager`
 * carrega `System.loadLibrary("git_wrapper")` no init (mesma divergencia registrada
 * na Fase A, em `GridScreenFlashcardsRemovedTest`). Este teste segue o mesmo caminho:
 * ler o codigo-fonte e garantir que o bloco de conteudo nao pode voltar.
 */
class GridScreenCardSemConteudoTest {

    private val raizModulo = File(System.getProperty("user.dir") ?: ".")

    private fun lerFonte(caminhoRelativo: String): String {
        val arquivo = File(raizModulo, caminhoRelativo)
        check(arquivo.exists()) { "arquivo nao encontrado: ${arquivo.absolutePath}" }
        return arquivo.readText()
    }

    @Test
    fun noteCardNaoRenderizaMaisOConteudoDaNota() {
        val fonte = lerFonte("src/main/java/io/github/wiiznokes/gitnote/ui/screen/app/grid/GridScreen.kt")

        assertFalse(fonte.contains("MarkdownCustom("), "NoteCard nao pode mais chamar MarkdownCustom")
        assertFalse(fonte.contains(".note.content"), "GridScreen nao pode mais ler o conteudo da nota")
        assertFalse(fonte.contains("gridNote.note."), "GridRow nao tem mais um Note embutido (era gridNote.note.*)")
    }

    @Test
    fun noteCardMostraDataFormatada() {
        val fonte = lerFonte("src/main/java/io/github/wiiznokes/gitnote/ui/screen/app/grid/GridScreen.kt")

        assertTrue(
            fonte.contains("DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)"),
            "o card devia formatar a data igual a ListView.kt:NoteListRow",
        )
    }

    @Test
    fun cliqueNoCardBuscaANotaSobDemanda() {
        val fonte = lerFonte("src/main/java/io/github/wiiznokes/gitnote/ui/screen/app/grid/GridScreen.kt")

        assertTrue(
            fonte.contains("vm.abrirNota(gridNote.relativePath)"),
            "abrir a nota deveria buscar o Note inteiro sob demanda, nao guarda-lo na linha da grade",
        )
    }

    @Test
    fun markdownCustomFoiApagadoMasAVersaoDoModoLeituraContinua() {
        val fonte = lerFonte("src/main/java/io/github/wiiznokes/gitnote/ui/screen/app/grid/markdownHelper.kt")

        assertFalse(fonte.contains("fun MarkdownCustom("), "MarkdownCustom ficou sem chamador na Fase B, tinha que ser apagada")
        assertTrue(fonte.contains("fun MarkdownCustomInner("), "MarkdownCustomInner e do modo leitura, nao pode ser tocada")
    }
}
