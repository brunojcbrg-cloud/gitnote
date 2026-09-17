package io.github.wiiznokes.gitnote.ui.screen.app.edit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FastScrollMathTest {
    private val viewportHeight = 1_000f
    private val thumbHeight = 100f
    private val maxValue = 9_000

    @Test
    fun fingerPositionMapsToScrollExtremesAndClampsOutsideTrack() {
        assertEquals(0, target(-100f))
        assertEquals(0, target(thumbHeight / 2f))
        assertEquals(maxValue, target(viewportHeight - thumbHeight / 2f))
        assertEquals(maxValue, target(viewportHeight + 100f))
    }

    @Test
    fun fingerPositionMapsLinearlyBetweenExtremes() {
        assertEquals(maxValue / 2, target(viewportHeight / 2f))
    }

    @Test
    fun thumbPositionMapsScrollAndClampsOutsideRange() {
        assertEquals(0, thumb(-1))
        assertEquals(450, thumb(maxValue / 2))
        assertEquals(900, thumb(maxValue + 1))
    }

    @Test
    fun contentThatDoesNotScrollHasNoTargetOrThumb() {
        assertNull(fastScrollTargetOffset(500f, viewportHeight, thumbHeight, maxValue = 0))
        assertNull(fastScrollThumbOffset(0, viewportHeight, thumbHeight, maxValue = 0))
    }

    @Test
    fun unresolvedOrMissingTrackHasNoTargetOrThumb() {
        assertNull(
            fastScrollTargetOffset(500f, viewportHeight, thumbHeight, maxValue = Int.MAX_VALUE),
        )
        assertNull(fastScrollThumbOffset(0, 100f, 100f, maxValue))
    }

    private fun target(y: Float): Int? = fastScrollTargetOffset(
        fingerY = y,
        viewportHeight = viewportHeight,
        thumbHeight = thumbHeight,
        maxValue = maxValue,
    )

    private fun thumb(scrollValue: Int): Int? = fastScrollThumbOffset(
        scrollValue = scrollValue,
        viewportHeight = viewportHeight,
        thumbHeight = thumbHeight,
        maxValue = maxValue,
    )
}
