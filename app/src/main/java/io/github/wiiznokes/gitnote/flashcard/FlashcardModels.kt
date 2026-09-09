package io.github.wiiznokes.gitnote.flashcard

import java.time.LocalDate

data class FlashcardSchedule(
    val dueDate: LocalDate,
    val interval: Int,
    val ease: Int,
) {
    fun asComment(): String = "<!--SR:!$dueDate,$interval,$ease-->"
}

data class ParsedFlashcard(
    val question: String,
    val answer: String,
    val sourceRange: IntRange,
    val scheduleLineRange: IntRange?,
    val insertionOffset: Int,
    val insertionLineEnding: String,
    val schedule: FlashcardSchedule?,
    val decks: List<String>,
    val context: List<String>,
) {
    val deck: String?
        get() = decks.firstOrNull()

    fun isAvailable(on: LocalDate): Boolean = schedule == null || !schedule.dueDate.isAfter(on)
}

enum class FlashcardRating {
    AGAIN,
    HARD,
    GOOD,
    EASY,
}

data class ScheduledReview(
    val rating: FlashcardRating,
    val schedule: FlashcardSchedule,
)
