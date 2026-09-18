package io.github.wiiznokes.gitnote.ui.viewmodel.edit

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownTableInsertionTest {
    private val table = "|  |  |  |\n| --- | --- | --- |\n|  |  |  |\n|  |  |  |"

    @Test
    fun insertsThreeByTwoIntoEmptyDocumentAndSelectsFirstHeaderCell() {
        val result = insertTable(TextFieldValue("", selection = TextRange(0)), 3, 2)
        assertEquals("$table\n\n", result.text)
        assertEquals(TextRange(2), result.selection)
    }

    @Test
    fun insertsAfterWholeParagraphWhenCursorIsInItsMiddle() {
        val source = "before paragraph\nafter"
        val result = insertTable(TextFieldValue(source, selection = TextRange(4)), 3, 2)
        assertEquals("before paragraph\n\n$table\n\nafter", result.text)
        assertEquals(TextRange("before paragraph\n\n".length + 2), result.selection)
    }

    @Test
    fun insertsAtEndOfFileWithoutFinalLineBreak() {
        val source = "paragraph"
        val result = insertTable(TextFieldValue(source, selection = TextRange(source.length)), 3, 2)
        assertEquals("paragraph\n\n$table\n\n", result.text)
    }

    @Test
    fun reusesBlankLineBetweenParagraphsWithoutDuplicatingIt() {
        val source = "before\n\nafter"
        val blankLineOffset = "before\n".length
        val result = insertTable(TextFieldValue(source, selection = TextRange(blankLineOffset)), 3, 2)
        assertEquals("before\n\n$table\n\nafter", result.text)
        assertEquals(1, result.text.substringBefore(table).windowed(2).count { it == "\n\n" })
    }

    @Test
    fun insideFenceUsesSingleLineBreaksAndNoInventedBlankLines() {
        val source = "```\ncode here\n```"
        val cursor = source.indexOf("here")
        val result = insertTable(TextFieldValue(source, selection = TextRange(cursor)), 3, 2)
        assertEquals("```\ncode here\n$table\n```", result.text)
        assertTrue(!result.text.contains("code here\n\n"))
        assertTrue(!result.text.contains("$table\n\n```"))
    }

    @Test
    fun crlfDocumentReceivesOnlyCrlfInInsertedTableAndSpacing() {
        val source = "before\r\n\r\nafter"
        val result = insertTable(
            TextFieldValue(source, selection = TextRange("before\r\n".length)),
            3,
            2,
        )
        val expectedTable = table.replace("\n", "\r\n")
        assertEquals("before\r\n\r\n$expectedTable\r\n\r\nafter", result.text)
        assertTrue(result.text.replace("\r\n", "").contains('\n').not())
    }

    @Test
    fun insertionPreservesEveryCharacterOutsideItsSingleSplice() {
        val source = "alpha\nbeta\ngamma"
        val result = insertTable(TextFieldValue(source, selection = TextRange(8)), 3, 2)
        assertTrue(result.text.startsWith("alpha\nbeta"))
        assertTrue(result.text.endsWith("\ngamma"))
        assertEquals(source, result.text.replace("\n\n$table\n", ""))
    }
}
