package io.github.wiiznokes.gitnote.flashcard

data class ReviewCard<N>(
    val note: N,
    val card: ParsedFlashcard,
    val indexInNote: Int,
)

data class FlashcardQueueUpdate<N>(
    val updatedNote: N,
    val queue: List<ReviewCard<N>>,
    val cardsInUpdatedNote: List<ReviewCard<N>>,
)

/** Pure queue transition extracted from the review ViewModel. */
class FlashcardQueue<N>(
    private val noteKey: (N) -> String,
    private val noteContent: (N) -> String,
    private val noteTitle: (N) -> String,
    private val withContent: (N, String) -> N,
) {
    fun review(
        queue: List<ReviewCard<N>>,
        schedule: FlashcardSchedule,
        requeueCurrent: Boolean,
    ): FlashcardQueueUpdate<N> {
        val current = requireNotNull(queue.firstOrNull())
        val newContent = FlashcardNoteUpdater.update(
            noteContent(current.note),
            current.card,
            schedule,
        )
        val updatedNote = withContent(current.note, newContent)
        val remaining = queue.drop(1).map { queued ->
            if (noteKey(queued.note) == noteKey(updatedNote)) queued.copy(note = updatedNote)
            else queued
        }.toMutableList()

        if (requeueCurrent) {
            val reparsed = FlashcardParser.parse(newContent, noteTitle(updatedNote))
                .firstOrNull {
                    it.sourceRange == current.card.sourceRange &&
                        it.question == current.card.question && it.answer == current.card.answer
                }
            if (reparsed != null) {
                remaining += ReviewCard(updatedNote, reparsed, current.indexInNote)
            }
        }

        val cardsInUpdatedNote = FlashcardParser.parse(newContent, noteTitle(updatedNote))
            .mapIndexed { index, card -> ReviewCard(updatedNote, card, index) }
        return FlashcardQueueUpdate(updatedNote, remaining, cardsInUpdatedNote)
    }
}
