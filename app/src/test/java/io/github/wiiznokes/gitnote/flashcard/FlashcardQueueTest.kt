package io.github.wiiznokes.gitnote.flashcard

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlashcardQueueTest {
    private data class TestNote(
        val path: String,
        val title: String,
        val content: String,
    )

    private val laterSchedule = FlashcardSchedule(LocalDate.of(2026, 9, 15), 0, 210)
    private val earlierSchedule = FlashcardSchedule(LocalDate.of(2026, 9, 25), 10, 230)
    private val finalLaterSchedule = FlashcardSchedule(LocalDate.of(2026, 9, 20), 5, 210)

    @Test
    fun `39 updating the later card keeps the earlier card range valid`() {
        val note = noteWithTwoCards()
        val engine = engine()
        val result = engine.review(
            queue = engine.orderForReview(cards(note)),
            expectedCardCount = 2,
            schedule = laterSchedule,
            requeueCurrent = false,
        )

        val updated = assertNotNull(result.updatedNote)
        val earlier = result.queue.single()
        assertEquals("A::um", updated.content.substring(earlier.card.sourceRange))
        assertEquals("A::um", earlier.card.question + "::" + earlier.card.answer)
    }

    @Test
    fun `40 a stale range after an earlier insertion corrupts the result`() {
        val note = noteWithTwoCards()
        val parsed = FlashcardParser.parse(note.content, note.title)
        val afterEarlier = FlashcardNoteUpdater.update(note.content, parsed[0], earlierSchedule)
        val withStaleLaterRange = FlashcardNoteUpdater.update(
            afterEarlier,
            parsed[1],
            finalLaterSchedule,
        )

        assertNotEquals(expectedFinalContent(), withStaleLaterRange)
    }

    @Test
    fun `41 remapping by index after an earlier insertion preserves all non SR bytes`() {
        val note = noteWithTwoCards()
        val first = engine().review(
            queue = cards(note),
            expectedCardCount = 2,
            schedule = earlierSchedule,
            requeueCurrent = false,
        )
        val second = engine().review(
            queue = first.queue,
            expectedCardCount = 2,
            schedule = finalLaterSchedule,
            requeueCurrent = false,
        )

        val finalContent = assertNotNull(second.updatedNote).content
        assertEquals(expectedFinalContent(), finalContent)
        assertEquals(note.content, withoutScheduleLines(finalContent))
    }

    @Test
    fun `42 reparsing after schedule insertion preserves card identity by index`() {
        val note = noteWithTwoCards()
        val before = FlashcardParser.parse(note.content, note.title)
        val result = engine().review(
            queue = cards(note),
            expectedCardCount = before.size,
            schedule = earlierSchedule,
            requeueCurrent = false,
        )
        val after = result.cardsInUpdatedNote.map { it.card }

        assertEquals(before.size, after.size)
        before.indices.forEach { index ->
            assertEquals(before[index].question, after[index].question)
            assertEquals(before[index].answer, after[index].answer)
        }
    }

    @Test
    fun `43 a card count mismatch drops the note and produces nothing to persist`() {
        val note = noteWithTwoCards()
        val mismatchingEngine = engine { content, title ->
            FlashcardParser.parse(content, title).dropLast(1)
        }
        val result = mismatchingEngine.review(
            queue = cards(note),
            expectedCardCount = 2,
            schedule = earlierSchedule,
            requeueCurrent = false,
        )

        assertNull(result.updatedNote)
        assertTrue(result.queue.isEmpty())
        assertEquals(note.content, noteWithTwoCards().content)
        assertEquals(2, result.cardCountMismatch?.expected)
        assertEquals(1, result.cardCountMismatch?.actual)
    }

    @Test
    fun `44 Again on late card then early card then requeued card preserves note`() {
        val note = noteWithTwoCards()
        val engine = engine()
        val initialQueue = engine.orderForReview(cards(note))

        val first = engine.review(
            initialQueue,
            expectedCardCount = 2,
            schedule = laterSchedule,
            requeueCurrent = true,
        )
        assertEquals(
            "#flashcards\nA::um\nB::dois\n${laterSchedule.asComment()}\nRodape",
            assertNotNull(first.updatedNote).content,
        )

        val second = engine.review(
            first.queue,
            expectedCardCount = 2,
            schedule = earlierSchedule,
            requeueCurrent = false,
        )
        assertEquals(
            "#flashcards\nA::um\n${earlierSchedule.asComment()}\n" +
                "B::dois\n${laterSchedule.asComment()}\nRodape",
            assertNotNull(second.updatedNote).content,
        )

        val third = engine.review(
            second.queue,
            expectedCardCount = 2,
            schedule = finalLaterSchedule,
            requeueCurrent = false,
        )
        assertEquals(expectedFinalContent(), assertNotNull(third.updatedNote).content)
    }

    private fun noteWithTwoCards() = TestNote(
        path = "estudo.md",
        title = "Estudo",
        content = "#flashcards\nA::um\nB::dois\nRodape",
    )

    private fun cards(note: TestNote): List<ReviewCard<TestNote>> =
        FlashcardParser.parse(note.content, note.title)
            .mapIndexed { index, card -> ReviewCard(note, card, index) }

    private fun engine(
        parser: (String, String) -> List<ParsedFlashcard> = FlashcardParser::parse,
    ) = FlashcardQueue(
        noteKey = TestNote::path,
        noteContent = TestNote::content,
        noteTitle = TestNote::title,
        withContent = { note, content -> note.copy(content = content) },
        parse = parser,
    )

    private fun expectedFinalContent() =
        "#flashcards\nA::um\n${earlierSchedule.asComment()}\n" +
            "B::dois\n${finalLaterSchedule.asComment()}\nRodape"

    private fun withoutScheduleLines(content: String): String = content.lineSequence()
        .filterNot { it.startsWith("<!--SR:!") }
        .joinToString("\n")
}
