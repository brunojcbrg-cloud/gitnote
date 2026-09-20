package io.github.wiiznokes.gitnote.ui.destination

import android.app.Application
import android.os.Parcel
import androidx.room.Room
import io.github.wiiznokes.gitnote.data.room.Note
import io.github.wiiznokes.gitnote.data.room.RepoDatabase
import io.github.wiiznokes.gitnote.ui.model.EditType
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class EditDestinationStateTest {
    @Test
    fun idleDestinationParcelIsSmallAndLooksUpTheNoteByPath() = runBlocking {
        val note = Note.new("pasta/grande.md", content = "x".repeat(336 * 1024))
        val destination = AppDestination.Edit(EditParams.Idle(note.relativePath, EditType.Update))
        val (restored, bytes) = roundTrip(destination)

        assertTrue(bytes < 1_024, "Idle no backstack ocupou $bytes bytes")
        assertEquals("md", restored.params.fileExtension().text)
        val repo = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            RepoDatabase::class.java,
        ).build()
        try {
            val dao = repo.repoDatabaseDao
            dao.insertNotes(listOf(note))
            val resolved = resolveEditNote(restored.params, dao::noteByRelativePath)
            assertEquals(note.relativePath, resolved?.relativePath)
            assertEquals(note.content, resolved?.content)
        } finally {
            repo.close()
        }
    }

    @Test
    fun createUsesThePathWithoutLookingUpANonexistentNote() = runBlocking {
        val params = EditParams.Idle("pasta/Nova.md", EditType.Create)
        val resolved = resolveEditNote(params) { error("nota nova ainda nao existe no DAO") }

        assertEquals("pasta/Nova.md", resolved?.relativePath)
        assertEquals("", resolved?.content)
        assertNull(resolveEditNote(EditParams.Idle("sumiu.md", EditType.Update)) { null })
    }

    @Test
    fun savedDestinationKeepsUnsavedTextAfterParcelRoundTrip() = runBlocking {
        val previous = Note.new("pasta/rascunho.md", content = "conteudo antigo")
        val unsaved = "edicao ainda nao gravada"
        val destination = AppDestination.Edit(
            EditParams.Saved(previous, EditType.Update, "novo nome", unsaved),
        )
        val (restored) = roundTrip(destination)
        val params = restored.params as EditParams.Saved

        assertEquals(unsaved, params.content)
        assertEquals("novo nome", params.name)
        assertEquals("conteudo antigo", params.note.content)
        val resolved = resolveEditNote(params) { error("Saved nao consulta o DAO") }
        assertSame(params.note, resolved)
    }

    private fun roundTrip(destination: AppDestination.Edit): Pair<AppDestination.Edit, Int> {
        val parcel = Parcel.obtain()
        try {
            parcel.writeParcelable(destination, 0)
            val bytes = parcel.dataSize()
            parcel.setDataPosition(0)
            val restored = parcel.readParcelable(
                AppDestination.Edit::class.java.classLoader,
                AppDestination.Edit::class.java,
            ) ?: error("destino nao restaurado")
            return restored to bytes
        } finally {
            parcel.recycle()
        }
    }
}
