package io.github.wiiznokes.gitnote.flashcard

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Sm2OsrSchedulerTest {
    private val today = LocalDate.of(2026, 9, 8)

    @Test
    fun `17 new card Good matches the plugin`() {
        val result = Sm2OsrScheduler.next(null, FlashcardRating.GOOD, today)
        assertEquals(FlashcardSchedule(today.plusDays(3), 3, 250), result)
    }

    @Test
    fun `18 all four new-card responses match the plugin`() {
        val results = FlashcardRating.entries.associateWith {
            Sm2OsrScheduler.next(null, it, today)
        }
        assertEquals(FlashcardSchedule(today, 0, 230), results[FlashcardRating.AGAIN])
        assertEquals(FlashcardSchedule(today.plusDays(1), 1, 230), results[FlashcardRating.HARD])
        assertEquals(FlashcardSchedule(today.plusDays(3), 3, 250), results[FlashcardRating.GOOD])
        assertEquals(FlashcardSchedule(today.plusDays(4), 4, 270), results[FlashcardRating.EASY])
    }

    @Test
    fun `19 real state 2026-09-10 interval 2 ease 210 matches the plugin`() {
        val previous = FlashcardSchedule(LocalDate.of(2026, 9, 10), 2, 210)
        val results = FlashcardRating.entries.associateWith {
            Sm2OsrScheduler.next(previous, it, today)
        }
        assertEquals(0 to 190, results.getValue(FlashcardRating.AGAIN).pair())
        assertEquals(1 to 190, results.getValue(FlashcardRating.HARD).pair())
        assertEquals(4 to 210, results.getValue(FlashcardRating.GOOD).pair())
        assertEquals(6 to 230, results.getValue(FlashcardRating.EASY).pair())
    }

    @Test
    fun `20 real state 2026-09-08 interval 0 ease 230 matches the plugin`() {
        val previous = FlashcardSchedule(LocalDate.of(2026, 9, 8), 0, 230)
        val results = FlashcardRating.entries.associateWith {
            Sm2OsrScheduler.next(previous, it, today)
        }
        assertEquals(0 to 210, results.getValue(FlashcardRating.AGAIN).pair())
        assertEquals(1 to 210, results.getValue(FlashcardRating.HARD).pair())
        assertEquals(2 to 230, results.getValue(FlashcardRating.GOOD).pair())
        assertEquals(3 to 250, results.getValue(FlashcardRating.EASY).pair())
    }

    @Test
    fun `21 source wins - Again is zero and Hard applies lapse factor`() {
        val mature = FlashcardSchedule(today, 100, 250)
        val again = Sm2OsrScheduler.next(mature, FlashcardRating.AGAIN, today)
        val hard = Sm2OsrScheduler.next(mature, FlashcardRating.HARD, today)
        assertEquals(0, again.interval)
        assertEquals(230, again.ease)
        assertEquals(50, hard.interval)
        assertEquals(230, hard.ease)
    }

    @Test
    fun `22 maximum interval is capped at 36525`() {
        val mature = FlashcardSchedule(today, 20_000, 250)
        assertEquals(
            Sm2OsrScheduler.MAXIMUM_INTERVAL,
            Sm2OsrScheduler.next(mature, FlashcardRating.EASY, today).interval,
        )
    }

    @Test
    fun `23 ease never falls below the plugin minimum`() {
        val minimum = FlashcardSchedule(today, 10, 130)
        assertEquals(130, Sm2OsrScheduler.next(minimum, FlashcardRating.AGAIN, today).ease)
        assertEquals(130, Sm2OsrScheduler.next(minimum, FlashcardRating.HARD, today).ease)
    }

    @Test
    fun `24 Easy applies the 1_3 bonus`() {
        val mature = FlashcardSchedule(today, 10, 250)
        val easy = Sm2OsrScheduler.next(mature, FlashcardRating.EASY, today)
        val withoutBonus = (10 * 270 / 100.0).toInt()
        assertEquals(35, easy.interval)
        assertTrue(easy.interval > withoutBonus)
    }

    @Test
    fun `45 delay is one day when due yesterday`() {
        val previous = FlashcardSchedule(today.minusDays(1), 2, 210)
        assertEquals(1, Sm2OsrScheduler.delayDays(previous, today))
    }

    @Test
    fun `46 delay is ten days when due ten days ago`() {
        val previous = FlashcardSchedule(today.minusDays(10), 2, 210)
        assertEquals(10, Sm2OsrScheduler.delayDays(previous, today))
    }

    @Test
    fun `47 future due date has no delay`() {
        val previous = FlashcardSchedule(today.plusDays(1), 2, 210)
        assertEquals(0, Sm2OsrScheduler.delayDays(previous, today))
    }

    @Test
    fun `48 a card without schedule has no delay`() {
        assertEquals(0, Sm2OsrScheduler.delayDays(null, today))
    }

    @Test
    fun `49 overdue zero interval state matches all four plugin formulas`() {
        val reviewDate = LocalDate.of(2026, 9, 15)
        val previous = FlashcardSchedule(LocalDate.of(2026, 9, 8), 0, 230)
        val delay = Sm2OsrScheduler.delayDays(previous, reviewDate)
        val results = FlashcardRating.entries.associateWith {
            Sm2OsrScheduler.next(previous, it, reviewDate, delayedDays = delay)
        }

        assertEquals(0 to 210, results.getValue(FlashcardRating.AGAIN).pair())
        assertEquals(1 to 210, results.getValue(FlashcardRating.HARD).pair())
        assertEquals(10 to 230, results.getValue(FlashcardRating.GOOD).pair())
        assertEquals(26 to 250, results.getValue(FlashcardRating.EASY).pair())
        assertTrue(
            results.getValue(FlashcardRating.EASY).interval > 7,
            "At 26 days the plugin could load-balance by plus or minus one day; GitNote does not.",
        )
    }

    @Test
    fun `50 preview and persisted calculation use the same nonzero delay`() {
        val reviewDate = LocalDate.of(2026, 9, 15)
        val previous = FlashcardSchedule(LocalDate.of(2026, 9, 8), 0, 230)
        val displayed = Sm2OsrScheduler.previews(previous, reviewDate)
            .single { it.rating == FlashcardRating.GOOD }
        val source = "#flashcards\nPergunta::Resposta\n${previous.asComment()}"
        val card = FlashcardParser.parse(source).single()
        val written = FlashcardNoteUpdater.update(source, card, displayed.schedule)
        val persisted = FlashcardParser.parse(written).single().schedule

        assertTrue(Sm2OsrScheduler.delayDays(previous, reviewDate) > 0)
        assertEquals(displayed.schedule, persisted)
    }

    private fun FlashcardSchedule.pair() = interval to ease
}
