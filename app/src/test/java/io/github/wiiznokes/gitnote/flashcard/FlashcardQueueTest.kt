package io.github.wiiznokes.gitnote.flashcard

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class FlashcardQueueTest {
    private data class TestNote(
        val path: String,
        val title: String,
        val content: String,
    )

    private val queue = FlashcardQueue<TestNote>(
        noteKey = TestNote::path,
        noteContent = TestNote::content,
        noteTitle = TestNote::title,
        withContent = { note, content -> note.copy(content = content) },
    )

    @Test
    fun `44 Again on late card then early card then requeued card preserves note`() {
        val original = "#flashcards\nA::um\nB::dois\nRodape"
        val note = TestNote("estudo.md", "Estudo", original)
        val initialQueue = FlashcardParser.parse(original, note.title)
            .mapIndexed { index, card -> ReviewCard(note, card, index) }
            .sortedByDescending { it.card.sourceRange.first }

        val again = FlashcardSchedule(LocalDate.of(2026, 9, 15), 0, 210)
        val earlyGood = FlashcardSchedule(LocalDate.of(2026, 9, 25), 10, 230)
        val lateGood = FlashcardSchedule(LocalDate.of(2026, 9, 20), 5, 210)

        val first = queue.review(initialQueue, again, requeueCurrent = true)
        assertEquals(
            "#flashcards\nA::um\nB::dois\n${again.asComment()}\nRodape",
            first.updatedNote.content,
        )

        val second = queue.review(first.queue, earlyGood, requeueCurrent = false)
        assertEquals(
            "#flashcards\nA::um\n${earlyGood.asComment()}\n" +
                "B::dois\n${again.asComment()}\nRodape",
            second.updatedNote.content,
        )

        val third = queue.review(second.queue, lateGood, requeueCurrent = false)
        assertEquals(
            "#flashcards\nA::um\n${earlyGood.asComment()}\n" +
                "B::dois\n${lateGood.asComment()}\nRodape",
            third.updatedNote.content,
        )
    }
}
