package io.github.wiiznokes.gitnote.ui.viewmodel.edit

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Exercises the same pure transformation called by MarkDownVM.onValueChange.
 *
 * Constructing MarkDownVM in a local JVM test would also construct TextVM and reach Android-backed
 * application/database state. Calling markdownSmartEditor directly keeps this suite on the exact
 * production edit path without introducing an Android test harness.
 */
@RunWith(Parameterized::class)
class MarkdownSmartEditorTest(
    private val ending: LineEnding,
) {

    enum class LineEnding(val text: String) {
        LF("\n"),
        CRLF("\r\n"),
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun lineEndings(): List<Array<LineEnding>> = LineEnding.entries.map { arrayOf(it) }
    }

    @Test
    fun `01 continues dash item`() {
        assertEnterAtEnd("- item", "- item${ending.text}- ")
    }

    @Test
    fun `02 continues asterisk item`() {
        assertEnterAtEnd("* item", "* item${ending.text}* ")
    }

    @Test
    fun `03 increments one`() {
        assertEnterAtEnd("1. item", "1. item${ending.text}2. ")
    }

    @Test
    fun `04 increments nine across digit boundary`() {
        assertEnterAtEnd("9. item", "9. item${ending.text}10. ")
    }

    @Test
    fun `05 continues unchecked task unchecked`() {
        assertEnterAtEnd("- [ ] tarefa", "- [ ] tarefa${ending.text}- [ ] ")
    }

    @Test
    fun `06 continues checked task unchecked`() {
        assertEnterAtEnd("- [x] tarefa", "- [x] tarefa${ending.text}- [ ] ")
    }

    @Test
    fun `07 continues quote`() {
        assertEnterAtEnd("> citacao", "> citacao${ending.text}> ")
    }

    @Test
    fun `08 preserves space indentation`() {
        assertEnterAtEnd("  - item", "  - item${ending.text}  - ")
    }

    @Test
    fun `09 preserves tab indentation`() {
        assertEnterAtEnd("\t- item", "\t- item${ending.text}\t- ")
    }

    @Test
    fun `10 continues item when enter is pressed in the middle`() {
        val input = "- itemrest"
        val cursor = "- item".length
        assertEnter(
            input = input,
            selection = TextRange(cursor),
            expected = "- item${ending.text}- rest",
            expectedCursor = "- item${ending.text}- ".length,
        )
    }

    @Test
    fun `11 continues item before a following line`() {
        val input = "- item${ending.text}following"
        assertEnter(
            input = input,
            selection = TextRange("- item".length),
            expected = "- item${ending.text}- ${ending.text}following",
            expectedCursor = "- item${ending.text}- ".length,
        )
    }

    @Test
    fun `12 continues item inside a document with consistent line endings`() {
        val prefix = "before${ending.text}- item"
        val input = "$prefix${ending.text}after"
        assertEnter(
            input = input,
            selection = TextRange(prefix.length),
            expected = "$prefix${ending.text}- ${ending.text}after",
            expectedCursor = "$prefix${ending.text}- ".length,
        )
    }

    @Test
    fun `13 removes empty dash marker`() {
        assertEnterAtEnd("- ", "")
    }

    @Test
    fun `14 removes empty task marker`() {
        assertEnterAtEnd("- [ ] ", "")
    }

    @Test
    fun `15 removes empty quote marker`() {
        assertEnterAtEnd("> ", "")
    }

    @Test
    fun `16 removes empty indented marker and indentation`() {
        assertEnterAtEnd("  - ", "")
    }

    @Test
    fun `17 leaves plain text without a prefix`() {
        assertEnterAtEnd("texto comum", "texto comum${ending.text}")
    }

    @Test
    fun `18 does not treat dash without space as a list`() {
        assertEnterAtEnd("-sem espaco", "-sem espaco${ending.text}")
    }

    @Test
    fun `19 does not continue list syntax inside a fenced code block`() {
        val input = "```${ending.text}- "
        assertEnterAtEnd(input, "$input${ending.text}")
    }

    @Test
    fun `20 does not treat date prefix as an ordered list`() {
        assertEnterAtEnd("2026-09-17 comecei", "2026-09-17 comecei${ending.text}")
    }

    @Test
    fun `21 backspace can remove a generated marker`() {
        val previousText = "- item${ending.text}- "
        val editedText = "- item${ending.text}"
        val actual = markdownSmartEditor(
            prev = EdicaoDeTexto(previousText, selecao = TextRange(previousText.length)),
            v = EdicaoDeTexto(editedText, selecao = TextRange(editedText.length)),
        )

        assertValue(editedText, editedText.length, actual)
    }

    @Test
    fun `22 enter replacing a selection still continues the item`() {
        val input = "- one two three"
        val selectionStart = input.indexOf("two")
        val selectionEnd = selectionStart + "two ".length
        assertEnter(
            input = input,
            selection = TextRange(selectionStart, selectionEnd),
            expected = "- one ${ending.text}- three",
            expectedCursor = "- one ${ending.text}- ".length,
        )
    }

    @Test
    fun `23 programmatic continuation clears stale IME composition`() {
        val input = "- item"
        val rawText = "$input${ending.text}"
        val actual = editMarkdownValue(
            previous = TextFieldValue(
                text = input,
                selection = TextRange(input.length),
                composition = TextRange(2, input.length),
            ),
            value = TextFieldValue(
                text = rawText,
                selection = TextRange(rawText.length),
                composition = TextRange(2, input.length),
            ),
        )

        assertEquals(null, actual.composition)
    }

    private fun assertEnterAtEnd(input: String, expected: String) {
        assertEnter(input, TextRange(input.length), expected)
    }

    private fun assertEnter(
        input: String,
        selection: TextRange,
        expected: String,
        expectedCursor: Int = expected.length,
    ) {
        val rawText = input.replaceRange(selection.start, selection.end, ending.text)
        val rawCursor = selection.start + ending.text.length
        val actual = markdownSmartEditor(
            prev = EdicaoDeTexto(input, selecao = selection),
            v = EdicaoDeTexto(rawText, selecao = TextRange(rawCursor)),
        )

        assertValue(expected, expectedCursor, actual)
    }

    private fun assertValue(expectedText: String, expectedCursor: Int, actual: EdicaoDeTexto) {
        assertEquals(expectedText, actual.texto, "text for ${ending.name}")
        assertEquals(TextRange(expectedCursor), actual.selecao, "cursor for ${ending.name}")
    }
}
