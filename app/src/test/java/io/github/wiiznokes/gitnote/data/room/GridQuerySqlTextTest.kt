package io.github.wiiznokes.gitnote.data.room

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fase B do handoff 10 (B.1/B.2/B.3). `gridNotes`/`gridNotesWithQuery` so funcionam
 * com as funcoes SQLite customizadas `parentPath`/`fullName`/`rank`, que so carregam
 * via `RepoDatabase.buildFactory` (requery) — e essa lib nativa nao roda na JVM de
 * teste (ver ESTADO_10.md, divergencia da Fase B). Sem poder executar a consulta de
 * verdade, este teste le o texto do SQL de producao em `Dao.kt` (mesmo estilo de
 * `GridScreenFlashcardsRemovedTest`, da Fase A) e confere que o texto que vai pro
 * banco tem exatamente as mudancas da Fase B — a logica de particionamento em si
 * esta provada em `NoteFolderFilterLogicTest`.
 */
class GridQuerySqlTextTest {

    private val raizModulo = File(System.getProperty("user.dir") ?: ".")

    private fun lerDaoFonte(): String {
        val arquivo = File(
            raizModulo,
            "src/main/java/io/github/wiiznokes/gitnote/data/room/Dao.kt",
        )
        check(arquivo.exists()) { "arquivo nao encontrado: ${arquivo.absolutePath}" }
        return arquivo.readText()
    }

    private fun blocoDaFuncao(fonte: String, inicio: String, fim: String): String {
        val comeco = fonte.indexOf(inicio)
        check(comeco >= 0) { "nao achei o inicio da funcao: $inicio" }
        val termino = fonte.indexOf(fim, startIndex = comeco)
        check(termino >= 0) { "nao achei o fim da funcao: $fim" }
        return fonte.substring(comeco, termino)
    }

    @Test
    fun gridNotesRawDevolveGridRowNaoGridNote() {
        val fonte = lerDaoFonte()

        assertTrue(fonte.contains("PagingSource<Int, GridRow>"))
        assertFalse(fonte.contains("GridNote"), "Dao.kt ainda referencia o tipo GridNote, removido na Fase B")
    }

    @Test
    fun gridNotesFiltraPastaExataNaoPrefixo() {
        val fonte = lerDaoFonte()
        val bloco = blocoDaFuncao(fonte, "fun gridNotes(", "fun gridNotesWithQuery(")

        assertTrue(
            bloco.contains("parentPath(relativePath) = :currentNoteFolderRelativePath"),
            "gridNotes deveria filtrar por parentPath(relativePath), nao por prefixo",
        )
        assertFalse(
            bloco.contains("LIKE :currentNoteFolderRelativePath || '%'"),
            "o LIKE de prefixo antigo (que trazia notas de todas as subpastas) nao pode sobrar em gridNotes",
        )
    }

    @Test
    fun gridNotesNaoTemMaisFuncaoDeJanelaNemColunaDeConteudo() {
        val fonte = lerDaoFonte()
        val bloco = blocoDaFuncao(fonte, "fun gridNotes(", "fun gridNotesWithQuery(")

        assertFalse(bloco.contains("COUNT(*) OVER"), "a janela por particao devia ter sido removida de gridNotes")
        assertFalse(bloco.contains("content"), "gridNotes nao pode selecionar a coluna content")
        assertTrue(bloco.contains("1 AS isUnique"), "dentro de uma unica pasta o nome e sempre unico")
    }

    @Test
    fun gridNotesWithQueryContinuaRecursivaComoAmbiguadorEComOBugDeMedicinaCorrigido() {
        val fonte = lerDaoFonte()
        val bloco = blocoDaFuncao(fonte, "fun gridNotesWithQuery(", "@RawQuery(observedEntities = [Note::class, NoteFolder::class])")

        assertTrue(
            bloco.contains(":currentNoteFolderRelativePath = '' OR Notes.relativePath LIKE :currentNoteFolderRelativePath || '/%'"),
            "faltou a excecao de raiz + o '/' que impede Medicina de casar com Medicina2",
        )
        assertTrue(
            bloco.contains("COUNT(*) OVER (PARTITION BY fileName)"),
            "a busca recursiva ainda pode ter nomes repetidos em pastas diferentes; o desambiguador tem que continuar",
        )
        assertTrue(bloco.contains("fullName(Notes.relativePath) as fileName"))
        assertFalse(bloco.contains("Notes.*"), "gridNotesWithQuery nao pode mais selecionar a nota inteira (traz content)")
    }
}
