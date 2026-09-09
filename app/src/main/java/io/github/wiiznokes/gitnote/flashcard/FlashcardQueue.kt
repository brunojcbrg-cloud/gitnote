package io.github.wiiznokes.gitnote.flashcard

data class ReviewCard<N>(
    val note: N,
    val card: ParsedFlashcard,
    val indexInNote: Int,
)

data class FlashcardQueueUpdate<N>(
    val updatedNote: N?,
    val queue: List<ReviewCard<N>>,
    val cardsInUpdatedNote: List<ReviewCard<N>>,
    val cardCountMismatch: FlashcardCardCountMismatch? = null,
)

data class FlashcardCardCountMismatch(
    val noteKey: String,
    val expected: Int,
    val actual: Int,
)

/**
 * Pure review-queue transition. Every range belonging to an updated note is discarded and
 * rebuilt from a single parse before the caller is allowed to persist the new content.
 */
class FlashcardQueue<N>(
    private val noteKey: (N) -> String,
    private val noteContent: (N) -> String,
    private val noteTitle: (N) -> String,
    private val withContent: (N, String) -> N,
    private val parse: (String, String) -> List<ParsedFlashcard> = FlashcardParser::parse,
) {
    fun orderForReview(cards: List<ReviewCard<N>>): List<ReviewCard<N>> = cards.sortedWith(
        compareBy<ReviewCard<N>> { noteKey(it.note) }
            .thenByDescending { it.card.sourceRange.first },
    )

    fun review(
        queue: List<ReviewCard<N>>,
        expectedCardCount: Int,
        schedule: FlashcardSchedule,
        requeueCurrent: Boolean,
    ): FlashcardQueueUpdate<N> {
        val current = requireNotNull(queue.firstOrNull())
        val currentNoteKey = noteKey(current.note)
        val newContent = FlashcardNoteUpdater.update(
            noteContent(current.note),
            current.card,
            schedule,
        )
        val updatedNote = withContent(current.note, newContent)
        val reparsed = parse(newContent, noteTitle(updatedNote))
        val remaining = queue.drop(1).let { cards ->
            if (requeueCurrent) cards + current else cards
        }

        if (reparsed.size != expectedCardCount) {
            return FlashcardQueueUpdate(
                updatedNote = null,
                queue = remaining.filterNot { noteKey(it.note) == currentNoteKey },
                cardsInUpdatedNote = emptyList(),
                cardCountMismatch = FlashcardCardCountMismatch(
                    noteKey = currentNoteKey,
                    expected = expectedCardCount,
                    actual = reparsed.size,
                ),
            )
        }

        val remappedQueue = remaining.map { queued ->
            if (noteKey(queued.note) != currentNoteKey) {
                queued
            } else {
                ReviewCard(
                    note = updatedNote,
                    card = reparsed[queued.indexInNote],
                    indexInNote = queued.indexInNote,
                )
            }
        }
        val cardsInUpdatedNote = reparsed
            .mapIndexed { index, card -> ReviewCard(updatedNote, card, index) }
        return FlashcardQueueUpdate(updatedNote, remappedQueue, cardsInUpdatedNote)
    }
}
