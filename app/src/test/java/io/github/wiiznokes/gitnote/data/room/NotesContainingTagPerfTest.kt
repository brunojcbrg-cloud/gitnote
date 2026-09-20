package io.github.wiiznokes.gitnote.data.room

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.time.measureTime

/**
 * Fase A do handoff 10: mede o custo de `notesContainingTag`, a consulta que a Fase A
 * tira do caminho de abrir Notas (handoff, causa 3). O numero fica registrado no
 * relatorio; nao ha assercao de limite porque o ponto da fase e o custo sumir, nao
 * ficar rapido.
 *
 * Usa o SQLite padrao do Room (Robolectric o sombreia com uma libsqlite nativa do
 * host), nao `RepoDatabase.buildFactory` — o `.so` do requery e especifico de Android
 * e nao carrega na JVM do teste (UnsatisfiedLinkError, medido no CI). `notesContainingTag`
 * e um `LIKE` puro, sem as funcoes customizadas que so o factory do requery registra.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NotesContainingTagPerfTest {

    @Test
    fun medeCustoDaVarreduraDeFlashcardsNoVaultSintetico() {
        val context = RuntimeEnvironment.getApplication()

        val db = Room.inMemoryDatabaseBuilder(context, RepoDatabase::class.java)
            .build()

        try {
            val dao = db.repoDatabaseDao

            val totalNotas = 4_309
            val totalBytes = 27L * 1024 * 1024

            runBlocking {
                gerarNotasSinteticas(totalNotas, totalBytes).chunked(1000).forEach {
                    dao.insertNotes(it)
                }

                repeat(2) { dao.notesContainingTag("#flashcards") }

                val amostras = List(5) {
                    measureTime { dao.notesContainingTag("#flashcards") }.inWholeMicroseconds / 1_000.0
                }
                val mediana = amostras.sorted()[amostras.size / 2]

                println(
                    "PERF_NOTES_CONTAINING_TAG notas=$totalNotas bytes=$totalBytes " +
                        "amostras_ms=$amostras mediana_ms=$mediana",
                )

                assertEquals(1, dao.notesContainingTag("#flashcards").size)
            }
        } finally {
            db.close()
        }
    }

    private fun gerarNotasSinteticas(quantidade: Int, totalBytes: Long): List<Note> {
        val tamanhoBase = (totalBytes / quantidade).toInt()
        val recheio = "lorem ipsum dolor sit amet consectetur adipiscing elit "

        return List(quantidade) { indice ->
            val marcador = if (indice == 0) "#flashcards " else ""
            val tamanhoAlvo = if (indice == quantidade - 1) {
                (totalBytes - tamanhoBase.toLong() * (quantidade - 1)).toInt()
            } else {
                tamanhoBase
            }
            val bruto = marcador + recheio.repeat(tamanhoAlvo / recheio.length + 1)
            val corpo = if (bruto.length > tamanhoAlvo) bruto.take(tamanhoAlvo) else bruto

            Note.new(
                relativePath = "nota_%04d.md".format(indice),
                content = corpo,
            )
        }
    }
}
