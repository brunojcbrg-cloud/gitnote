package io.github.wiiznokes.gitnote.ui.viewmodel.edit

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarkdownTableDetectionTest {
    private val table = "| head | value |\n| --- | --- |\n| first | 1 |\n| last | 2 |"

    @Test
    fun detectsEveryTableLineButNotFollowingBlankLine() {
        val text = "$table\n\nafter"
        listOf("head", "---", "first", "last").forEach { needle ->
            val region = assertNotNull(tableRegionAt(text, text.indexOf(needle)))
            assertEquals(MdTableRegion(0, 1, 3), region)
        }
        assertNull(tableRegionAt(text, table.length + 1))
    }

    @Test
    fun rejectsProseWithLonePipeAndMissingSeparator() {
        assertNull(tableRegionAt("prose with a | pipe\nnext line", 12))
        assertNull(tableRegionAt("| a | b |\n| body | row |", 3))
    }

    @Test
    fun rejectsHorizontalRuleAsSeparator() {
        val text = "prose | fragment\n---\nmore | prose"
        assertNull(tableRegionAt(text, text.indexOf('|')))
    }

    @Test
    fun rejectsPipesThatExistOnlyInsideInlineCode() {
        val text = "`a|b`\n`---|---`\n`c|d`"
        assertNull(tableRegionAt(text, text.indexOf('|')))
    }

    @Test
    fun rejectsTableShapedTextInsideFencedCodeBlock() {
        val text = "```\n$table\n```"
        assertNull(tableRegionAt(text, text.indexOf("head")))
    }

    @Test
    fun detectsTableAtEndOfFileWithoutFinalLineBreak() {
        val region = assertNotNull(tableRegionAt(table, table.length))
        assertEquals(MdTableRegion(0, 1, 3), region)
    }

    @Test
    fun twoTablesSeparatedByBlankLineResolveToSecondTable() {
        val text = "$table\n\n$table"
        val secondStart = text.lastIndexOf("| head")
        val region = assertNotNull(tableRegionAt(text, secondStart + 3))
        assertEquals(MdTableRegion(5, 6, 8), region)
    }

    @Test
    fun documentResizeReplacesOnlyTableAndMovesCursorIntoSurvivingFirstCell() {
        val text = "before\n\n$table\n\nafter"
        val value = TextFieldValue(text, selection = TextRange(text.indexOf("first")))
        val result = assertNotNull(resizeTableAt(value, columns = 3, bodyRows = 3))

        assertEquals(0, result.lostNonEmptyCells)
        assertTrue(result.value.text.startsWith("before\n\n| head | value |  |"))
        assertTrue(result.value.text.endsWith("\n\nafter"))
        assertEquals('h', result.value.text[result.value.selection.start])
    }

    @Test
    fun documentResizeReportsExactLossBeforeApplying() {
        val value = TextFieldValue(table, selection = TextRange(table.indexOf("head")))
        val result = assertNotNull(resizeTableAt(value, columns = 1, bodyRows = 1))
        assertEquals(4, result.lostNonEmptyCells)
        assertEquals("| head |\n| --- |\n| first |", result.value.text)
    }
}
