package io.github.wiiznokes.gitnote.data.room

import android.app.Application
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import io.github.wiiznokes.gitnote.ui.model.SortOrder
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.time.measureTime

/**
 * `PERF_GRID_QUERY` — o numero que a Fase B nao conseguiu medir.
 *
 * O `RESULTADO_10_B.md` registra, com todas as letras, que o `PERF_GRID_QUERY` pedido no
 * criterio de aceitacao da Fase B **nao foi escrito**: tanto a consulta antiga quanto a
 * da Fase B dependiam de `fullName()`/`parentPath()`, funcoes SQLite customizadas que so
 * carregam pelo requery e nao rodam na JVM do CI. Com o filtro recursivo da Fase K.2 a
 * listagem na ordem padrao deixou de usar funcao customizada, e a medicao passou a ser
 * possivel pela primeira vez.
 *
 * Mede os tres cenarios do criterio 6 da Fase K, na raiz do vault sintetico de 4.309
 * notas somando 27 MB (as mesmas medidas do vault real, secao 1 do handoff):
 * com teto de 10, sem teto (primeira pagina) e a contagem de "Mostrar todas".
 *
 * A primeira pagina e medida como o Room de paginacao a monta de verdade —
 * `SELECT * FROM (<consulta>) LIMIT 50 OFFSET 0` (`PagingConfig(pageSize = 50)`) — e o
 * cursor e percorrido ate o fim, porque o SQLite so faz o trabalho quando alguem le as
 * linhas. Sem assercao de tempo: o runner do CI e ruidoso (ver ESTADO_10.md, ressalva da
 * H.2); o numero serve de registro no relatorio.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class GridQueryPerfTest {

    private val totalNotas = 4_309
    private val totalBytes = 27L * 1024 * 1024

    @Test
    fun medeAConsultaDaGradeNaRaizDoVaultSintetico() {
        val db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            RepoDatabase::class.java,
        ).build()

        try {
            runBlocking {
                gerarVaultSintetico().chunked(1000).forEach { db.repoDatabaseDao.insertNotes(it) }
            }

            val comTeto = paginaDeConsulta(GradeSql.notasDaPasta(SortOrder.UltimaVisualizacao, teto = 10))
            val semTeto = paginaDeConsulta(GradeSql.notasDaPasta(SortOrder.UltimaVisualizacao, teto = null))
            val contagem = GradeSql.contagemDaPasta()

            // aquece o banco: a primeira execucao paga o plano de consulta
            repeat(2) {
                linhas(db, comTeto)
                linhas(db, semTeto)
                linhas(db, contagem)
            }

            registrar("teto_10", medir { linhas(db, comTeto) })
            registrar("sem_teto_primeira_pagina", medir { linhas(db, semTeto) })
            registrar("contagem_mostrar_todas", medir { linhas(db, contagem) })

            assertEquals(10, linhas(db, comTeto), "com teto, a primeira pagina tem as 10 da Fase D")
            assertEquals(50, linhas(db, semTeto), "sem teto, a primeira pagina do Paging tem 50")
        } finally {
            db.close()
        }
    }

    /** O embrulho que o Room de paginacao poe em volta da consulta da grade. */
    private fun paginaDeConsulta(sql: String): String = "SELECT * FROM ($sql) LIMIT 50 OFFSET 0"

    private fun linhas(db: RepoDatabase, sql: String): Int =
        db.query(SimpleSQLiteQuery(sql, arrayOf(""))).use { cursor ->
            var lidas = 0
            while (cursor.moveToNext()) lidas++
            lidas
        }

    private fun medir(bloco: () -> Unit): List<Double> = List(5) {
        measureTime { bloco() }.inWholeMicroseconds / 1_000.0
    }

    private fun registrar(cenario: String, amostras: List<Double>) {
        val mediana = amostras.sorted()[amostras.size / 2]
        println(
            "PERF_GRID_QUERY cenario=$cenario notas=$totalNotas bytes=$totalBytes " +
                "amostras_ms=$amostras mediana_ms=$mediana",
        )
    }

    private fun gerarVaultSintetico(): List<Note> {
        val tamanhoBase = (totalBytes / totalNotas).toInt()
        val recheio = "lorem ipsum dolor sit amet consectetur adipiscing elit "

        return List(totalNotas) { indice ->
            val bruto = recheio.repeat(tamanhoBase / recheio.length + 1)
            Note.new(
                // arvore parecida com a do vault: notas espalhadas por subpastas, nao soltas na raiz
                relativePath = "pasta_%02d/sub_%02d/nota_%04d.md".format(indice % 40, indice % 7, indice),
                content = bruto.take(tamanhoBase),
                lastModifiedTimeMillis = indice.toLong(),
                lastOpenedTimeMillis = indice.toLong(),
            )
        }
    }
}
