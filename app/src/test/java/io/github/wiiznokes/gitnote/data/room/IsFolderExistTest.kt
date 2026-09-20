package io.github.wiiznokes.gitnote.data.room

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fase C do handoff 10: `isFolderExist` e a guarda que evita a grade abrir vazia
 * quando a pasta padrao configurada (`AppPreferences.pastaPadrao`) nao existe mais no
 * banco (caso da Parte V, quando `06_Conhecimento` vira `NOTAS`). E um `LIKE`/`EXISTS`
 * puro sobre `NoteFolders`, sem as funcoes SQLite customizadas do requery — roda no
 * SQLite padrao do Room, como `isNoteExist` (ver a mesma ressalva em
 * `NotesContainingTagPerfTest`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class IsFolderExistTest {

    @Test
    fun pastaExistenteEDetectada() {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, RepoDatabase::class.java).build()

        try {
            val dao = db.repoDatabaseDao
            runBlocking {
                dao.insertFolders(listOf(NoteFolder.new(relativePath = "06_Conhecimento")))

                assertTrue(dao.isFolderExist("06_Conhecimento"))
                assertFalse(dao.isFolderExist("NOTAS"))
            }
        } finally {
            db.close()
        }
    }
}
