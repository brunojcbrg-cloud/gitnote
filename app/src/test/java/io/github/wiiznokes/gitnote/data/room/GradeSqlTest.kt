package io.github.wiiznokes.gitnote.data.room

import android.app.Application
import android.database.Cursor
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import io.github.wiiznokes.gitnote.ui.model.SortOrder
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fase K.2 do handoff 10: a listagem da grade volta a ser recursiva (a pasta **e** as
 * descendentes), segurada pelo `LIMIT` da Fase D.
 *
 * **Este e o primeiro teste do repositorio que roda a consulta da grade de verdade.**
 * Ate a Fase B ela dependia de `parentPath()`, funcao SQLite customizada que so carrega
 * pelo requery e nao existe na JVM do CI (ver ESTADO_10.md, divergencias das Fases A e
 * B) — por isso os testes de B e D conferiam o SQL como texto. Trocando o filtro por
 * `LIKE`, a listagem na ordem padrao deixou de usar qualquer funcao customizada, entao
 * o MESMO texto que vai pro banco em producao ([GradeSql]) e executado aqui num SQLite
 * comum, com a arvore sintetica do criterio de aceitacao da Fase B.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class GradeSqlTest {

    private val db = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(),
        RepoDatabase::class.java,
    )
        // `query()` direto e sincrono: sem isto o Room barra a chamada na thread
        // principal do Robolectric (medido no run 35535567673).
        .allowMainThreadQueries()
        .build()

    @AfterTest
    fun fechar() = db.close()

    private fun inserir(vararg notas: Note) = runBlocking {
        db.repoDatabaseDao.insertNotes(notas.toList())
    }

    private fun arvoreSintetica() = inserir(
        *List(3) { Note.new(relativePath = "raiz_$it.md") }.toTypedArray(),
        *List(5) { Note.new(relativePath = "A/nota_$it.md") }.toTypedArray(),
        *List(7) { Note.new(relativePath = "A/B/nota_$it.md") }.toTypedArray(),
        *List(2) { Note.new(relativePath = "A2/nota_$it.md") }.toTypedArray(),
    )

    private fun <T> consultar(sql: String, pasta: String, leitor: (Cursor) -> T): T =
        db.query(SimpleSQLiteQuery(sql, arrayOf(pasta))).use(leitor)

    private fun caminhos(pasta: String, teto: Int? = null): List<String> = consultar(
        GradeSql.notasDaPasta(SortOrder.UltimaVisualizacao, teto),
        pasta,
    ) { cursor ->
        val coluna = cursor.getColumnIndexOrThrow("relativePath")
        buildList { while (cursor.moveToNext()) add(cursor.getString(coluna)) }
    }

    private fun contagem(pasta: String): Int =
        consultar(GradeSql.contagemDaPasta(), pasta) { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    @Test
    fun abrirUmaPastaTrazAsNotasDelaEDasDescendentes() {
        arvoreSintetica()

        val emA = caminhos("A")

        assertEquals(12, emA.size, "esperava as 5 de A mais as 7 de A/B")
        assertTrue(emA.any { it.startsWith("A/B/") }, "as notas da subpasta tinham que aparecer")
    }

    @Test
    fun abrirUmaPastaNaoTrazNadaDaIrmaDeNomeParecido() {
        arvoreSintetica()

        val emA = caminhos("A")

        assertFalse(
            emA.any { it.startsWith("A2") },
            "a barra no filtro existe justamente para A2 nao entrar: $emA",
        )
    }

    /**
     * O defeito latente que a Fase B corrigiu e a K.2 nao pode trazer de volta, no nome
     * real do vault: `Medicina` nao pode casar com `Medicina2/`, nem com um arquivo
     * `Medicina.md` solto ao lado.
     */
    @Test
    fun medicinaNaoCasaComMedicina2NemComUmArquivoDeMesmoPrefixo() {
        inserir(
            Note.new(relativePath = "Medicina/nota.md"),
            Note.new(relativePath = "Medicina/Basicas/outra.md"),
            Note.new(relativePath = "Medicina2/nota.md"),
            Note.new(relativePath = "Medicina.md"),
        )

        assertEquals(
            listOf("Medicina/Basicas/outra.md", "Medicina/nota.md"),
            caminhos("Medicina").sorted(),
        )
        assertEquals(2, contagem("Medicina"))
    }

    @Test
    fun oTetoDeDezCortaAListagemRecursiva() {
        arvoreSintetica()

        assertEquals(10, caminhos("A", teto = 10).size)
        assertEquals(12, caminhos("A", teto = null).size, "sem teto, a pasta inteira")
    }

    @Test
    fun aRaizEnxergaOVaultInteiro() {
        arvoreSintetica()

        assertEquals(17, caminhos("").size)
        assertEquals(17, contagem(""))
        assertEquals(10, caminhos("", teto = 10).size)
    }

    /**
     * Uma pasta que so tem subpastas — o caso que a Fase B quebrou e que motivou a
     * Fase K: `06_Conhecimento` tem 6 subpastas e **nenhuma nota direta**.
     */
    @Test
    fun pastaSemNotaDiretaDeixaDeVoltarVazia() {
        inserir(
            Note.new(relativePath = "06_Conhecimento/Medicina/Basicas/nota.md"),
            Note.new(relativePath = "06_Conhecimento/Direito/outra.md"),
        )

        assertEquals(2, caminhos("06_Conhecimento").size)
        assertEquals(2, contagem("06_Conhecimento"))
    }

    /**
     * Criterio 7 da Fase K: o ganho da Fase B continua de pe. Prova pelo cursor, nao
     * pelo texto — a consulta devolve exatamente 4 colunas e nenhuma delas e conteudo.
     */
    @Test
    fun aProjecaoContinuaSemAColunaDeConteudo() {
        inserir(Note.new(relativePath = "A/nota.md", content = "x".repeat(336 * 1024)))

        val colunas = consultar(
            GradeSql.notasDaPasta(SortOrder.UltimaVisualizacao, teto = 10),
            "A",
        ) { it.columnNames.toList() }

        assertEquals(listOf("relativePath", "id", "lastModifiedTimeMillis", "isUnique"), colunas)
    }

    @Test
    fun aConsultaDaGradeNaoTemFuncaoDeJanelaNemConteudo() {
        val sql = GradeSql.notasDaPasta(SortOrder.UltimaVisualizacao, teto = 10)

        assertFalse(sql.contains("COUNT(*) OVER"), "a funcao de janela saiu na Fase B e nao volta")
        assertFalse(sql.contains("content"), "a projecao nao pode trazer o conteudo do arquivo")
        assertTrue(sql.contains("LIKE :currentNoteFolderRelativePath || '/%'"), "a barra e obrigatoria")
    }

    /**
     * A ordem da Fase D (ultima visualizacao ou modificacao) continua valendo com o
     * filtro recursivo: as tres notas estao em pastas diferentes da mesma arvore.
     */
    @Test
    fun aOrdemDaFaseDContinuaValendoEntreDescendentes() {
        inserir(
            Note.new(relativePath = "A/primeira.md", lastModifiedTimeMillis = 3, lastOpenedTimeMillis = 3),
            Note.new(relativePath = "A/B/segunda.md", lastModifiedTimeMillis = 1, lastOpenedTimeMillis = 5),
            Note.new(relativePath = "A/B/C/terceira.md", lastModifiedTimeMillis = 2, lastOpenedTimeMillis = 2),
        )

        assertEquals(
            listOf("A/B/segunda.md", "A/primeira.md", "A/B/C/terceira.md"),
            caminhos("A"),
        )
    }
}
