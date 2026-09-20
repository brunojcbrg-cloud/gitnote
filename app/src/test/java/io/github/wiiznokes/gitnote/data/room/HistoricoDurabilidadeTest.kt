package io.github.wiiznokes.gitnote.data.room

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class HistoricoDurabilidadeTest {

    @Test
    fun aberturaSobreviveAReindexacaoEOrdenaComDataDeModificacao() {
        val context = RuntimeEnvironment.getApplication()
        val pasta = File(context.cacheDir, "fase-d-${System.nanoTime()}").apply { mkdirs() }
        val nomeHistorico = "historico-fase-d-${System.nanoTime()}"
        listOf("primeira.md", "segunda.md", "terceira.md").forEach {
            File(pasta, it).writeText("# $it")
        }

        val repo = Room.inMemoryDatabaseBuilder(context, RepoDatabase::class.java).build()
        var historico = Room.databaseBuilder(context, HistoricoDatabase::class.java, nomeHistorico).build()

        try {
            val dao = repo.repoDatabaseDao
            val timestamps = hashMapOf("primeira.md" to 3L, "segunda.md" to 1L, "terceira.md" to 2L)

            runBlocking {
                dao.clearAndInit(pasta.absolutePath, timestamps, emptyMap(), isSupportedExtension = { it == "md" })

                historico.dao.upsert(Abertura("segunda.md", 5L))
                dao.updateLastOpened("segunda.md", 5L)
                assertEquals(listOf("segunda.md", "primeira.md", "terceira.md"), ordem(repo))
            }

            historico.close()
            historico = Room.databaseBuilder(context, HistoricoDatabase::class.java, nomeHistorico).build()

            runBlocking {
                val aberturas = historico.dao.todas().associate { it.relativePath to it.abertaEmMillis }
                assertEquals(mapOf("segunda.md" to 5L), aberturas)

                dao.clearAndInit(pasta.absolutePath, timestamps, aberturas, isSupportedExtension = { it == "md" })
                assertEquals(listOf("segunda.md", "primeira.md", "terceira.md"), ordem(repo))
                assertEquals(5L, dao.noteByRelativePath("segunda.md")?.lastOpenedTimeMillis)
                assertEquals(3L, dao.noteByRelativePath("primeira.md")?.lastOpenedTimeMillis)
            }
        } finally {
            historico.close()
            repo.close()
            context.deleteDatabase(nomeHistorico)
            pasta.deleteRecursively()
        }
    }

    private fun ordem(repo: RepoDatabase): List<String> =
        repo.openHelper.readableDatabase.query(
            "SELECT relativePath FROM Notes " +
                "ORDER BY MAX(lastOpenedTimeMillis, lastModifiedTimeMillis) DESC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
}
