package io.github.wiiznokes.gitnote.data.room

import io.github.wiiznokes.gitnote.ui.model.SortOrder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * O requery que registra parentPath/rank/fullName nao carrega no Robolectric do CI.
 * Esta verificacao acompanha o SQL usado em producao; a ordem escalar tambem e
 * executada no SQLite do teste de durabilidade.
 *
 * Atualizado pela Fase K.2: a listagem da pasta voltou a ser recursiva e o texto do SQL
 * mudou de `Dao.kt` para `GradeSql.kt`. A ordem da Fase D e o teto continuam sendo o que
 * este teste guarda — agora contra o proprio construtor da consulta, e nao contra o
 * codigo-fonte como texto.
 */
class GradeFaseDSqlTest {
    private val modulo = File(System.getProperty("user.dir") ?: ".")

    private fun codigo(caminho: String): String = File(modulo, "src/main/java/$caminho").readText()

    @Test
    fun listagemDaPastaOrdenaPelaDataMaisRecenteEAplicaLimite() {
        val comTeto = GradeSql.notasDaPasta(SortOrder.UltimaVisualizacao, teto = 10)
        val semTeto = GradeSql.notasDaPasta(SortOrder.UltimaVisualizacao, teto = null)

        assertTrue(comTeto.contains("MAX(lastOpenedTimeMillis, lastModifiedTimeMillis)"))
        assertTrue(comTeto.trimEnd().endsWith("LIMIT 10"))
        assertFalse(semTeto.contains("LIMIT"), "\"Mostrar todas\" tira o teto")
        assertFalse(comTeto.contains("COUNT(*) OVER"))
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
        assertTrue(
            codigo("io/github/wiiznokes/gitnote/data/room/Dao.kt")
                .contains("GradeSql.contagemDaPasta()"),
            "o total do rodape tem que sair do mesmo filtro recursivo da listagem",
        )
    }
}
