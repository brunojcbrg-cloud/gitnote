package io.github.wiiznokes.gitnote.data.room

import io.github.wiiznokes.gitnote.ui.model.SortOrder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fase B do handoff 10 (B.1/B.3), atualizada pela Fase K.2.
 *
 * O que a Fase B ganhou e nao pode voltar continua garantido aqui: **sem coluna
 * `content`** e **sem funcao de janela** na consulta da grade. O que mudou na Fase K.2 e
 * so o filtro de pasta: a listagem voltou a ser recursiva (a pasta e as descendentes),
 * agora com a barra obrigatoria, e quem segura o custo e o `LIMIT` da Fase D — por isso
 * as assercoes de `parentPath(relativePath) = :path` sairam.
 *
 * `gridNotesWithQuery` continua so verificavel como texto (usa `rank`/`fullName`, que so
 * carregam pelo requery — ver ESTADO_10.md, divergencia da Fase B). A listagem da grade
 * ja nao: desde a K.2 ela roda de verdade em `GradeSqlTest`.
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
    fun gridNotesFiltraAPastaEAsDescendentesSemPegarAIrmaDeNomeParecido() {
        val sql = GradeSql.notasDaPasta(SortOrder.UltimaVisualizacao, teto = 10)

        assertTrue(
            sql.contains("LIKE :currentNoteFolderRelativePath || '/%'"),
            "a Fase K.2 voltou ao filtro recursivo, e a barra impede Medicina de casar com Medicina2",
        )
        assertFalse(
            sql.contains("LIKE :currentNoteFolderRelativePath || '%'"),
            "o LIKE de prefixo SEM a barra e o defeito latente que a Fase B corrigiu; nao pode voltar",
        )
        assertTrue(
            lerDaoFonte().contains("GradeSql.notasDaPasta(sortOrder, teto)"),
            "o DAO tem que usar exatamente o SQL que este teste verifica",
        )
    }

    @Test
    fun gridNotesNaoTemMaisFuncaoDeJanelaNemColunaDeConteudo() {
        val sql = GradeSql.notasDaPasta(SortOrder.UltimaVisualizacao, teto = null)

        assertFalse(sql.contains("COUNT(*) OVER"), "a janela por particao saiu na Fase B e nao volta")
        assertFalse(sql.contains("content"), "gridNotes nao pode selecionar a coluna content")
        assertTrue(sql.contains("1 AS isUnique"), "o desambiguador de nome nao pode voltar a ser calculado")
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
