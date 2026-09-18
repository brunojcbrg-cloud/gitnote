package io.github.wiiznokes.gitnote.ui.viewmodel.edit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MarkdownTableTest {
    @Test
    fun parseAndRenderRoundTripTwelveRealFormsByteForByte() {
        val nineColumns = "| a | b | c | d | e | f | g | h | i |\n" +
            "| --- | --- | --- | --- | --- | --- | --- | --- | --- |\n" +
            "| 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 |"
        val tenRows = (1..10).joinToString("\n") { "| row $it | value $it |" }
        val samples = listOf(
            "| a | b |\n| --- | --- |\n| 1 | 2 |",
            "|a|b|\n|---|---|\n|1|2|",
            "a | b\n--- | ---\n1 | 2",
            "| left | value |\n| :-- | --- |\n| a | b |",
            "| right | value |\n| --: | --- |\n| a | b |",
            "| center | value |\n| :-: | --- |\n| a | b |",
            "| a | b |\n| --- | --- |\n|  | value |",
            "| a \\| b | c |\n| --- | --- |\n| d | e |",
            "| `a|b` | c |\n| --- | --- |\n| d | e |",
            nineColumns,
            "| a | b |\n| --- | --- |\n$tenRows",
            "| a | b |\r\n| --- | --- |\r\n| 1 | 2 |",
        )

        assertEquals(12, samples.size)
        samples.forEachIndexed { index, source ->
            val parsed = assertNotNull(parseTable(source, 0), "sample ${index + 1}")
            assertEquals(source, renderTable(parsed), "sample ${index + 1}")
        }
    }

    @Test
    fun cellsOfIgnoresEscapedAndInlineCodePipes() {
        assertEquals(listOf("a", "b"), cellsOf("|a|b|"))
        assertEquals(listOf(" a ", " b "), cellsOf("| a | b |"))
        assertEquals(listOf("a ", " b"), cellsOf("a | b"))
        assertEquals(listOf(" a \\| b "), cellsOf("| a \\| b |"))
        assertEquals(listOf(" `x|y` ", " z "), cellsOf("| `x|y` | z |"))
        assertEquals(listOf(""), cellsOf("||"))
        assertEquals(listOf("", ""), cellsOf("|||"))
        assertEquals(listOf("   "), cellsOf("|   |"))
        assertEquals(listOf("a", "b"), cellsOf("a|b|"))
    }

    @Test
    fun buildTableCreatesRequestedDimensionsAndParsesBack() {
        listOf(3 to 2, 1 to 1, 10 to 50).forEach { (columns, rows) ->
            val rendered = buildTable(columns, rows, "\n")
            val parsed = assertNotNull(parseTable(rendered, 0))
            assertEquals(columns, parsed.header.size)
            assertEquals(columns, parsed.aligns.size)
            assertEquals(rows, parsed.rows.size)
            assertTrue(rendered.lineSequence().elementAt(1).let { cellsOf(it).size == columns })
        }
        assertEquals(
            "|  |  |  |\n| --- | --- | --- |\n|  |  |  |\n|  |  |  |",
            buildTable(3, 2, "\n"),
        )
    }

    @Test
    fun resizeGrowsColumnsAndRowsWithoutChangingExistingContentOrAlignment() {
        val source = "| a | b |\n| :-- | --: |\n| 1 | 2 |"
        val parsed = assertNotNull(parseTable(source, 0))
        val result = resizeTable(parsed, columns = 3, bodyRows = 2)

        assertEquals(0, result.lostNonEmptyCells)
        assertEquals(listOf(" a ", " b ", "  "), result.table.header)
        assertEquals(listOf(MdAlign.LEFT, MdAlign.RIGHT, MdAlign.NONE), result.table.aligns)
        assertEquals(listOf(" 1 ", " 2 ", "  "), result.table.rows[0])
        assertEquals(listOf("  ", "  ", "  "), result.table.rows[1])
    }

    @Test
    fun resizeReportsOnlyNonEmptyCellsActuallyRemoved() {
        val emptyTail = assertNotNull(parseTable("| a |  |\n| --- | --- |\n| 1 |  |", 0))
        assertEquals(0, resizeTable(emptyTail, columns = 1, bodyRows = 1).lostNonEmptyCells)

        val contentTail = assertNotNull(
            parseTable("| a | heading |\n| --- | --- |\n| 1 | body |\n| 2 | lower |", 0),
        )
        val result = resizeTable(contentTail, columns = 1, bodyRows = 1)
        assertEquals(4, result.lostNonEmptyCells)
        assertEquals(1, result.table.header.size)
        assertEquals(1, result.table.rows.size)
        assertNotNull(parseTable(renderTable(result.table), 0))
    }

    @Test
    fun resizeNormalizesIrregularRowsWithoutDroppingText() {
        val tooShort = assertNotNull(parseTable("| a | b | c |\n| --- | --- | --- |\n| one |", 0))
        val padded = resizeTable(tooShort, columns = 3, bodyRows = 1).table.rows.single()
        assertEquals(listOf(" one ", "  ", "  "), padded)

        val tooLong = assertNotNull(parseTable("| a | b |\n| --- | --- |\n| one | two | three |", 0))
        val merged = resizeTable(tooLong, columns = 2, bodyRows = 1).table.rows.single()
        assertEquals(" one ", merged[0])
        assertTrue(merged[1].contains("two"))
        assertTrue(merged[1].contains("three"))
        assertTrue(merged[1].contains(" | "))
    }

    @Test
    fun resizeKeepsSurvivingAlignmentAndPipeStyle() {
        val source = "left | center | right\n:-- | :-: | --:\na | b | c"
        val parsed = assertNotNull(parseTable(source, 0))
        val result = resizeTable(parsed, columns = 2, bodyRows = 1)

        assertEquals(listOf(MdAlign.LEFT, MdAlign.CENTER), result.table.aligns)
        assertTrue(!result.table.style.outerPipes)
        val rendered = renderTable(result.table)
        assertTrue(!rendered.lineSequence().first().startsWith('|'))
        assertTrue(!rendered.lineSequence().first().endsWith('|'))
    }
}
