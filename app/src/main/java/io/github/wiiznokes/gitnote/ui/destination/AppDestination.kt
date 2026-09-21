package io.github.wiiznokes.gitnote.ui.destination

import android.os.Parcelable
import io.github.wiiznokes.gitnote.data.room.Note
import io.github.wiiznokes.gitnote.ui.model.EditType
import io.github.wiiznokes.gitnote.ui.model.FileExtension
import kotlinx.parcelize.Parcelize


sealed interface AppDestination : Parcelable {
    /** Tela inicial do Life SO: as notas sao uma funcao do app, nao o app inteiro. */
    @Parcelize
    data object Home : AppDestination

    @Parcelize
    data object SendLesson : AppDestination

    @Parcelize
    data object LessonHistory : AppDestination

    @Parcelize
    data object Grid : AppDestination

    @Parcelize
    data object FlashcardDecks : AppDestination

    @Parcelize
    data class FlashcardReview(
        val deckPath: String,
        val folderPath: String?,
    ) : AppDestination

    @Parcelize
    data class Edit(val params: EditParams) : AppDestination

    @Parcelize
    data class Settings(val settingsDestination: SettingsDestination) : AppDestination

}

@Parcelize
sealed class EditParams : Parcelable {
    data class Saved(
        val note: Note,
        val editType: EditType,
        val name: String,
        val content: String,
    ) : EditParams()

    data class Idle(
        val relativePath: String,
        val editType: EditType,
        val section: String? = null,
    ) : EditParams()

    fun fileExtension(): FileExtension {
        return when (this) {
            is Idle -> FileExtension.match(this.relativePath.substringAfterLast('.', missingDelimiterValue = ""))
            is Saved -> this.note.fileExtension()
        }
    }
}

internal suspend fun resolveEditNote(
    params: EditParams,
    noteByRelativePath: suspend (String) -> Note?,
): Note? = when (params) {
    is EditParams.Idle -> when (params.editType) {
        EditType.Create -> Note.new(relativePath = params.relativePath)
        EditType.Update -> noteByRelativePath(params.relativePath)
    }
    is EditParams.Saved -> params.note
}
