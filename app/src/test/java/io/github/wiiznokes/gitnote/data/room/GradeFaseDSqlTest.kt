package io.github.wiiznokes.gitnote.data.room

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * O requery que registra parentPath/rank/fullName nao carrega no Robolectric do CI.
 * Esta verificacao acompanha o SQL usado em producao; a ordem escalar tambem e
 * executada no SQLite do teste de durabilidade.
 */
class GradeFaseDSqlTest {
    private val modulo = File(System.getProperty("user.dir") ?: ".")

    private fun codigo(caminho: String): String = File(modulo, "src/main/java/$caminho").readText()

    @Test
    fun listagemDaPastaOrdenaPelaDataMaisRecenteEAplicaLimite() {
        val dao = codigo("io/github/wiiznokes/gitnote/data/room/Dao.kt")
        val listagem = dao.substringAfter("fun gridNotes(").substringBefore("fun gridNotesWithQuery(")

        assertTrue(listagem.contains("MAX(lastOpenedTimeMillis, lastModifiedTimeMillis)"))
        assertTrue(listagem.contains("LIMIT ${'$'}{teto.coerceAtLeast(0)}"))
        assertTrue(listagem.contains("parentPath(relativePath) = :currentNoteFolderRelativePath"))
        assertFalse(listagem.contains("COUNT(*) OVER"))
    }

    @Test
    fun buscaNaoTemLimiteEFluxoDaGradeReiniciaOTetoAoTrocarPasta() {
        val dao = codigo("io/github/wiiznokes/gitnote/data/room/Dao.kt")
        val busca = dao.substringAfter("fun gridNotesWithQuery(").substringBefore("fun drawerFolders(")
        val vm = codigo("io/github/wiiznokes/gitnote/ui/viewmodel/GridViewModel.kt")

        assertFalse(busca.contains("LIMIT "))
        assertTrue(vm.contains("_teto.emit(TetoDeNotas.PRIMEIRA_LEVA)"))
        assertTrue(vm.contains("_teto.value = null"))
        assertTrue(vm.contains("dao.gridNotesWithQuery(condicoes.pasta, condicoes.ordem, condicoes.query)"))
        assertTrue(vm.contains("dao.countNotesInFolder(pasta)"))
    }
}
