package io.github.wiiznokes.gitnote.ui.viewmodel.edit

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.wiiznokes.gitnote.MyApp
import io.github.wiiznokes.gitnote.R
import io.github.wiiznokes.gitnote.data.room.Note
import io.github.wiiznokes.gitnote.ui.component.markdown.resolveWikilinkTargets
import io.github.wiiznokes.gitnote.ui.destination.EditParams
import io.github.wiiznokes.gitnote.ui.model.EditType
import io.github.wiiznokes.gitnote.ui.viewmodel.viewModelFactory
import kotlinx.coroutines.launch

private const val TAG = "MarkDownVM"


class MarkDownVM : TextVM {

    private val dao = MyApp.appModule.repoDatabase.repoDatabaseDao
    private val uiHelper = MyApp.appModule.uiHelper

    private var initialSectionConsumed = false
    private var initialSection: String? = null

    constructor(
        editType: EditType,
        previousNote: Note,
        initialSection: String? = null,
    ) : super(editType, previousNote) {
        this.initialSection = initialSection
    }

    constructor(
        editType: EditType,
        previousNote: Note,
        name: String,
        content: String,
    ) : super(editType, previousNote, name, content)

    override fun onValueChange(v: TextFieldValue) {
        val newValue = markdownSmartEditor(content.value, v)
        super.onValueChange(newValue)
    }

    fun onTitle() {
        val newValue = onTitle(content.value)
        super.onValueChange(newValue)
    }

    fun onBold() {
        val newValue = addOrRemovePatternAtTheExtremitiesOfSelection(content.value, "**")
        super.onValueChange(newValue)
    }

    fun onItalic() {
        val newValue = addOrRemovePatternAtTheExtremitiesOfSelection(content.value, "_")
        super.onValueChange(newValue)
    }

    fun onCode() {
        val newValue = onCode(content.value)
        super.onValueChange(newValue)
    }

    fun onQuote() {
        val newValue = onQuote(content.value)
        //Log.d(TAG, "onQuote result: text=\"${v.text.replace("\n", "\\n")}\", start=${v.selection.start}, end=${v.selection.end}")
        super.onValueChange(newValue)
    }

    fun onLink() {
        val newValue = onLink(content.value)
        super.onValueChange(newValue)
    }

    fun onUnorderedList() {
        val newValue = onUnorderedList(content.value)
        super.onValueChange(newValue)
    }

    fun onNumberedList() {
        val newValue = onNumberedList(content.value)
        super.onValueChange(newValue)
    }

    fun onTaskList() {
        val newValue = onTaskList(content.value)
        super.onValueChange(newValue)
    }

    suspend fun resolveWikilinks(names: Set<String>): Map<String, String?> {
        val candidates = dao.wikilinkCandidates(names)
        return resolveWikilinkTargets(
            names = names,
            currentParentPath = previousNote.parentPath(),
            candidatePaths = candidates.map { it.relativePath },
        )
    }

    fun openResolvedWikilink(
        relativePath: String,
        name: String,
        section: String?,
        onOpenNote: (Note, String?) -> Unit,
    ) {
        viewModelScope.launch {
            val note = dao.noteByRelativePath(relativePath)
            if (note == null) {
                showMissingWikilink(name)
            } else {
                onOpenNote(note, section)
            }
        }
    }

    fun showMissingWikilink(name: String) {
        uiHelper.makeToast(uiHelper.getString(R.string.error_wikilink_not_found, name))
    }

    fun showSectionNotFound(name: String) {
        uiHelper.makeToast(uiHelper.getString(R.string.error_wikilink_section_not_found, name))
    }

    fun pendingInitialSection(): String? = initialSection.takeUnless { initialSectionConsumed }

    fun consumeInitialSection() {
        initialSectionConsumed = true
    }
}


@Composable
fun newMarkDownVM(editParams: EditParams): MarkDownVM {

    return when (editParams) {
        is EditParams.Idle -> viewModel<MarkDownVM>(
            factory = viewModelFactory {
                MarkDownVM(editParams.editType, editParams.note, editParams.section)
            }
        )

        is EditParams.Saved -> {
            viewModel<MarkDownVM>(
                factory = viewModelFactory {
                    MarkDownVM(
                        editType = editParams.editType,
                        previousNote = editParams.note,
                        name = editParams.name,
                        content = editParams.content,
                    )
                }
            )
        }
    }
}
