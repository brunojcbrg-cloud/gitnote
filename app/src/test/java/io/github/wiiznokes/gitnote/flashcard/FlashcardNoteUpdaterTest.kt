package io.github.wiiznokes.gitnote.flashcard

import java.time.LocalDate
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FlashcardNoteUpdaterTest {
    private val schedule = FlashcardSchedule(LocalDate.of(2026, 9, 11), 3, 250)

    @Test
    fun `25 preserves CRLF`() {
        val before = "#flashcards\r\nPergunta::Resposta\r\nTexto\r\n"
        val after = update(before)
        assertEquals(
            "#flashcards\r\nPergunta::Resposta\r\n${schedule.asComment()}\r\nTexto\r\n",
            after,
        )
        assertFalse(after.replace("\r\n", "").contains('\n'))
    }

    @Test
    fun `26 preserves mixed line endings exactly`() {
        val before = "#flashcards\r\nPergunta::Resposta\nUm\rDois\r\n"
        val after = update(before)
        assertEquals(
            "#flashcards\r\nPergunta::Resposta\n${schedule.asComment()}\nUm\rDois\r\n",
            after,
        )
    }

    @Test
    fun `27 preserves absence of final newline`() {
        val before = "#flashcards\nPergunta::Resposta"
        val after = update(before)
        assertEquals("#flashcards\nPergunta::Resposta\n${schedule.asComment()}", after)
        assertFalse(after.endsWith("\n"))
    }

    @Test
    fun `28 preserves BOM`() {
        val before = "\uFEFF#flashcards\nPergunta::Resposta"
        val after = update(before)
        assertTrue(after.startsWith("\uFEFF"))
        assertEquals(1, after.count { it == '\uFEFF' })
    }

    @Test
    fun `29 preserves isolated CR`() {
        val before = "#flashcards\rPergunta::Resposta\rTexto"
        val after = update(before)
        assertEquals("#flashcards\rPergunta::Resposta\r${schedule.asComment()}\rTexto", after)
    }

    @Test
    fun `30 inserts a schedule immediately after a new card`() {
        val before = "#flashcards\nPergunta::Resposta\nTexto"
        assertEquals(
            "#flashcards\nPergunta::Resposta\n${schedule.asComment()}\nTexto",
            update(before),
        )
    }

    @Test
    fun `31 replaces rather than duplicates an existing schedule`() {
        val before = "#flashcards\nPergunta::Resposta\n<!--SR:!2026-09-10,2,210-->\nTexto"
        val after = update(before)
        assertEquals(1, Regex("<!--SR:").findAll(after).count())
        assertTrue(after.contains(schedule.asComment()))
    }

    @Test
    fun `32 reviewing only the second card leaves the first byte-identical`() {
        val before = "#flashcards\nPrimeira::Um\n<!--SR:!2026-09-10,2,210-->\nSegunda::Dois"
        val second = FlashcardParser.parse(before).last()
        val after = FlashcardNoteUpdater.update(before, second, schedule)
        assertTrue(after.startsWith("#flashcards\nPrimeira::Um\n<!--SR:!2026-09-10,2,210-->\n"))
    }

    @Test
    fun `33 only the SR line differs when a schedule exists`() {
        val before = "\uFEFF#flashcards\r\nPergunta::Resposta\n  <!--SR:!2026-09-10,2,210-->  \rRodapé"
        val after = update(before)
        val oldRange = before.indexOf("  <!--SR:") until before.indexOf("  \rRodapé") + 2
        val newRange = after.indexOf(schedule.asComment()) until
            after.indexOf(schedule.asComment()) + schedule.asComment().length
        assertEquals(before.removeRange(oldRange), after.removeRange(newRange))
    }

    @Test
    fun `34 refuses to overwrite a note changed on disk`() {
        val expected = "#flashcards\nPergunta::Resposta"
        val card = FlashcardParser.parse(expected).single()
        val result = FlashcardNoteUpdater.updateIfUnchanged(
            expectedContent = expected,
            diskContent = "$expected\nmudança externa",
            card = card,
            schedule = schedule,
        )
        assertTrue(result.isFailure)
        assertIs<ConcurrentNoteChangeException>(result.exceptionOrNull())
    }

    @Test
    fun `35 updates a 24000-character note in linear time`() {
        val filler = "texto sem card\r\n".repeat(1_600)
        val before = ("#flashcards\r\n$filler" + "Pergunta::Resposta").take(24_000)
        val source = if (before.contains("Pergunta::Resposta")) before else
            before.dropLast(20) + "Pergunta::Resposta"
        val card = FlashcardParser.parse(source).single()
        lateinit var after: String
        val elapsed = measureTimeMillis {
            after = FlashcardNoteUpdater.update(source, card, schedule)
        }
        assertTrue(after.contains(schedule.asComment()))
        assertTrue(elapsed < 2_000, "24k update took ${elapsed}ms")
    }

    private fun update(source: String): String {
        val card = FlashcardParser.parse(source).single()
        return FlashcardNoteUpdater.update(source, card, schedule)
    }
}
