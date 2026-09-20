package io.github.wiiznokes.gitnote.ui.viewmodel.edit

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.wiiznokes.gitnote.MyApp
import io.github.wiiznokes.gitnote.R
import io.github.wiiznokes.gitnote.data.room.Note
import io.github.wiiznokes.gitnote.ui.component.markdown.resolveWikilinkTargets
import io.github.wiiznokes.gitnote.ui.component.markdown.offsetOfLineStart
import io.github.wiiznokes.gitnote.ui.destination.EditParams
import io.github.wiiznokes.gitnote.ui.model.EditType
import io.github.wiiznokes.gitnote.ui.viewmodel.viewModelFactory
import kotlinx.coroutines.launch

private const val TAG = "MarkDownVM"

internal fun editMarkdownValue(previous: TextFieldValue, value: TextFieldValue): TextFieldValue {
    val edited = markdownSmartEditor(previous.toEdicaoDeTexto(), value.toEdicaoDeTexto())
    // Automatic continuation clears the IME composition; padding deletion keeps it.
    val continuedLine = edited.texto != value.text &&
        value.selection.collapsed && value.text.getOrNull(value.selection.start - 1) == '\n'
    return edited.toTextFieldValue(value, clearComposition = continuedLine)
}


class MarkDownVM : TextVM {

    private val dao = MyApp.appModule.repoDatabase.repoDatabaseDao
    private val uiHelper = MyApp.appModule.uiHelper

    private var initialSectionConsumed = false
    private var initialSection: String? = null
    private var anchorLine: Int? = null

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
        super.onValueChange(editMarkdownValue(content.value, v))
    }

    private fun applyEdit(transform: (EdicaoDeTexto) -> EdicaoDeTexto) {
        val original = content.value
        super.onValueChange(transform(original.toEdicaoDeTexto()).toTextFieldValue(original))
    }

    fun onTitle() {
        applyEdit { onTitle(it) }
    }

    fun onBold() {
        applyEdit { addOrRemovePatternAtTheExtremitiesOfSelection(it, "**") }
    }

    fun onItalic() {
        applyEdit { addOrRemovePatternAtTheExtremitiesOfSelection(it, "_") }
    }

    fun onCode() {
        applyEdit { onCode(it) }
    }

    fun onQuote() {
        applyEdit { onQuote(it) }
    }

    fun onLink() {
        applyEdit { onLink(it) }
    }

    fun onUnorderedList() {
        applyEdit { onUnorderedList(it) }
    }

    fun onNumberedList() {
        applyEdit { onNumberedList(it) }
    }

    fun onTaskList() {
        applyEdit { onTaskList(it) }
    }

    fun onTableInsert(columns: Int, bodyRows: Int) {
        val original = content.value
        val edited = insertTable(original.toEdicaoDeTexto(), columns, bodyRows)
        super.onValueChange(edited.toTextFieldValue(original, clearComposition = true))
    }

    fun onTableResize(columns: Int, bodyRows: Int) {
        val original = content.value
        val result = resizeTableAt(original.toEdicaoDeTexto(), columns, bodyRows) ?: return
        super.onValueChange(result.value.toTextFieldValue(original, clearComposition = true))
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

    fun rememberAnchor(line: Int) {
        anchorLine = line.coerceAtLeast(0)
    }

    fun consumeAnchor(): Int? = anchorLine.also { anchorLine = null }

    fun moveCursorToLine(line: Int) {
        updateSelection(TextRange(offsetOfLineStart(content.value.text, line)))
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
