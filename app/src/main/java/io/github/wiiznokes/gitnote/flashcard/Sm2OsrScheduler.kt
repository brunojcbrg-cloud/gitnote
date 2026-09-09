package io.github.wiiznokes.gitnote.flashcard

import java.time.LocalDate
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Port of obsidian-spaced-repetition's SM-2-OSR `osrSchedule`.
 *
 * The plugin rounds before load balancing when that option is enabled. GitNote mirrors that
 * deterministic rounding, but deliberately omits the histogram-based choice inside the fuzz
 * window, which is outside this iteration's scope.
 */
object Sm2OsrScheduler {
    const val BASE_EASE = 250
    const val MINIMUM_EASE = 130
    const val MAXIMUM_INTERVAL = 36_525
    const val EASY_BONUS = 1.3
    const val LAPSES_INTERVAL_CHANGE = 0.5

    fun next(
        previous: FlashcardSchedule?,
        rating: FlashcardRating,
        today: LocalDate,
        delayedDays: Int = 0,
        initialEase: Int = BASE_EASE,
    ): FlashcardSchedule {
        val originalInterval = max(1, previous?.interval ?: 1)
        var ease = previous?.ease ?: initialEase
        val delay = max(0, delayedDays)
        val calculatedInterval = when (rating) {
            FlashcardRating.EASY -> {
                ease += 20
                (originalInterval + delay) * ease / 100.0 * EASY_BONUS
            }

            FlashcardRating.GOOD -> (originalInterval + delay / 2.0) * ease / 100.0

            FlashcardRating.HARD -> {
                ease = max(MINIMUM_EASE, ease - 20)
                max(1.0, (originalInterval + delay / 4.0) * LAPSES_INTERVAL_CHANGE)
            }

            FlashcardRating.AGAIN -> {
                ease = max(MINIMUM_EASE, ease - 20)
                0.0
            }
        }

        val interval = calculatedInterval.roundToInt().coerceAtMost(MAXIMUM_INTERVAL)
        return FlashcardSchedule(
            dueDate = today.plusDays(interval.toLong()),
            interval = interval,
            ease = ease,
        )
    }

    fun previews(
        previous: FlashcardSchedule?,
        today: LocalDate,
        initialEase: Int = BASE_EASE,
    ): List<ScheduledReview> = FlashcardRating.entries.map { rating ->
        ScheduledReview(rating, next(previous, rating, today, initialEase = initialEase))
    }

    /** Mirrors the plugin's note-ease contribution for a new card in an established note. */
    fun initialEase(schedulesInNote: List<FlashcardSchedule>): Int {
        if (schedulesInNote.isEmpty()) return BASE_EASE
        val average = schedulesInNote.map { it.ease }.average()
        val contribution = min(1.0, ln(schedulesInNote.size + 0.5) / ln(64.0))
        return (average * contribution + BASE_EASE * (1.0 - contribution)).roundToInt()
    }

    fun formatInterval(schedule: FlashcardSchedule): String =
        if (schedule.interval == 0) "1 min" else "${schedule.interval} day(s)"
}
