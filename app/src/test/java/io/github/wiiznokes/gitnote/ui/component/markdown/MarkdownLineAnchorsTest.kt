package io.github.wiiznokes.gitnote.ui.component.markdown

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MarkdownLineAnchorsTest {

    @Test
    fun lineStartsCoverLfCrlfEmptyLinesFinalLineAndEmptyText() {
        assertContentEquals(intArrayOf(0, 2, 3, 5), lineStartOffsets("a\n\nb\nc"))
        assertContentEquals(intArrayOf(0, 3, 5, 8), lineStartOffsets("a\r\n\r\nb\r\nc"))
        assertContentEquals(intArrayOf(0), lineStartOffsets(""))
        assertContentEquals(intArrayOf(0, 2), lineStartOffsets("a\n"))
    }

    @Test
    fun lineLookupHandlesStartLastCharacterAndTextEnd() {
        val text = "ab\ncd"

        assertEquals(0, lineOfOffset(text, 0))
        assertEquals(1, lineOfOffset(text, text.lastIndex))
        assertEquals(1, lineOfOffset(text, text.length))
        assertEquals(0, lineOfOffset("", 0))
    }

    @Test
    fun lineAndStartOffsetAreInverseForEveryLine() {
        listOf(
            "",
            "sem quebra final",
            "a\n\nb\nc",
            "a\r\n\r\nb\r\nc",
            "linha final\n",
        ).forEach { text ->
            lineStartOffsets(text).indices.forEach { line ->
                assertEquals(line, lineOfOffset(text, offsetOfLineStart(text, line)), text)
            }
        }
    }

    @Test
    fun outOfRangeLinesClampToTheNearestExistingLine() {
        val text = "primeira\nultima"

        assertEquals(0, offsetOfLineStart(text, -1))
        assertEquals("primeira\n".length, offsetOfLineStart(text, 99))
    }

    @Test
    fun nearestAnchorUsesExactOrPreviousLine() {
        val anchors = mapOf(2 to 20, 5 to 50, 9 to 90)

        assertEquals(50, nearestAnchorAtOrBefore(5, anchors))
        assertEquals(50, nearestAnchorAtOrBefore(7, anchors))
        assertNull(nearestAnchorAtOrBefore(1, anchors))
        assertNull(nearestAnchorAtOrBefore(5, emptyMap()))
    }

    @Test
    fun touchPositionUsesTheBlockAboveOrTheFirstBlock() {
        val anchors = mapOf(2 to 20, 5 to 50, 9 to 90)

        assertEquals(5, nearestLineAtOrBefore(65, anchors))
        assertEquals(2, nearestLineAtOrBefore(5, anchors))
        assertNull(nearestLineAtOrBefore(5, emptyMap()))
    }

    @Test
    fun viewportTopUsesTheFirstBlockAtOrAfterIt() {
        val anchors = mapOf(2 to 20, 5 to 50, 9 to 90)

        assertEquals(5, firstLineAtOrAfter(35, anchors))
        assertEquals(2, firstLineAtOrAfter(5, anchors))
        assertEquals(9, firstLineAtOrAfter(100, anchors))
        assertNull(firstLineAtOrAfter(5, emptyMap()))
    }
}
