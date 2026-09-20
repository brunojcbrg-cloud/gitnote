package io.github.wiiznokes.gitnote.ui.viewmodel.edit

import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownTableInsertionTest {
    private val table = "|  |  |  |\n| --- | --- | --- |\n|  |  |  |\n|  |  |  |"

    @Test
    fun insertsThreeByTwoIntoEmptyDocumentAndSelectsFirstHeaderCell() {
        val result = insertTable(EdicaoDeTexto("", selecao = TextRange(0)), 3, 2)
        assertEquals("$table\n\n", result.texto)
        assertEquals(TextRange(2), result.selecao)
    }

    @Test
    fun insertsAfterWholeParagraphWhenCursorIsInItsMiddle() {
        val source = "before paragraph\nafter"
        val result = insertTable(EdicaoDeTexto(source, selecao = TextRange(4)), 3, 2)
        assertEquals("before paragraph\n\n$table\n\nafter", result.texto)
        assertEquals(TextRange("before paragraph\n\n".length + 2), result.selecao)
    }

    @Test
    fun insertsAtEndOfFileWithoutFinalLineBreak() {
        val source = "paragraph"
        val result = insertTable(EdicaoDeTexto(source, selecao = TextRange(source.length)), 3, 2)
        assertEquals("paragraph\n\n$table\n\n", result.texto)
    }

    @Test
    fun reusesBlankLineBetweenParagraphsWithoutDuplicatingIt() {
        val source = "before\n\nafter"
        val blankLineOffset = "before\n".length
        val result = insertTable(EdicaoDeTexto(source, selecao = TextRange(blankLineOffset)), 3, 2)
        assertEquals("before\n\n$table\n\nafter", result.texto)
        assertEquals(1, result.texto.substringBefore(table).windowed(2).count { it == "\n\n" })
    }

    @Test
    fun insideFenceUsesSingleLineBreaksAndNoInventedBlankLines() {
        val source = "```\ncode here\n```"
        val cursor = source.indexOf("here")
        val result = insertTable(EdicaoDeTexto(source, selecao = TextRange(cursor)), 3, 2)
        assertEquals("```\ncode here\n$table\n```", result.texto)
        assertTrue(!result.texto.contains("code here\n\n"))
        assertTrue(!result.texto.contains("$table\n\n```"))
    }

    @Test
    fun crlfDocumentReceivesOnlyCrlfInInsertedTableAndSpacing() {
        val source = "before\r\n\r\nafter"
        val result = insertTable(
            EdicaoDeTexto(source, selecao = TextRange("before\r\n".length)),
            3,
            2,
        )
        val expectedTable = table.replace("\n", "\r\n")
        assertEquals("before\r\n\r\n$expectedTable\r\n\r\nafter", result.texto)
        assertTrue(result.texto.replace("\r\n", "").contains('\n').not())
    }

    @Test
    fun insertionPreservesEveryCharacterOutsideItsSingleSplice() {
        val source = "alpha\nbeta\ngamma"
        val result = insertTable(EdicaoDeTexto(source, selecao = TextRange(8)), 3, 2)
        assertTrue(result.texto.startsWith("alpha\nbeta"))
        assertTrue(result.texto.endsWith("\ngamma"))
        assertEquals(source, result.texto.replace("\n\n$table\n", ""))
    }
}
