package io.github.wiiznokes.gitnote.ui.screen.app.edit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FastScrollMathTest {
    private val viewportHeight = 1_000f
    private val thumbHeight = 100f
    private val maxValue = 9_000
    private val lineCount = 901
    private val visibleLines = 20

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

    @Test
    fun fingerPositionMapsToLineExtremesAndClampsOutsideTrack() {
        assertEquals(0, line(-100f))
        assertEquals(0, line(thumbHeight / 2f))
        assertEquals(lineCount - 1, line(viewportHeight - thumbHeight / 2f))
        assertEquals(lineCount - 1, line(viewportHeight + 100f))
    }

    @Test
    fun fingerPositionMapsLinearlyBetweenLineExtremes() {
        assertEquals((lineCount - 1) / 2, line(viewportHeight / 2f))
    }

    @Test
    fun lineThumbMapsCursorLineAndClampsOutsideRange() {
        assertEquals(0, lineThumb(-1))
        assertEquals(450, lineThumb((lineCount - 1) / 2))
        assertEquals(900, lineThumb(lineCount + 10))
    }

    @Test
    fun aNoteThatFitsOnScreenHasNoLineTargetOrThumb() {
        assertNull(
            fastScrollLineTarget(500f, viewportHeight, thumbHeight, lineCount = 20, visibleLines = 20),
        )
        assertNull(
            fastScrollLineTarget(500f, viewportHeight, thumbHeight, lineCount = 1, visibleLines = 20),
        )
        assertNull(
            fastScrollLineThumbOffset(0, lineCount = 20, viewportHeight = viewportHeight, thumbHeight = thumbHeight, visibleLines = 20),
        )
    }

    @Test
    fun aViewportSmallerThanTheThumbHasNoLineTarget() {
        assertNull(
            fastScrollLineTarget(50f, 100f, 100f, lineCount = lineCount, visibleLines = 20),
        )
        assertNull(
            fastScrollLineTarget(50f, viewportHeight, thumbHeight, lineCount = lineCount, visibleLines = 0),
        )
    }

    @Test
    fun visibleLinesFollowTheViewportAndNeverDivideByZero() {
        assertEquals(20, estimatedVisibleLines(viewportHeight, lineHeight = 50f))
        assertEquals(0, estimatedVisibleLines(viewportHeight, lineHeight = 0f))
        assertEquals(0, estimatedVisibleLines(0f, lineHeight = 50f))
    }

    private fun line(y: Float): Int? = fastScrollLineTarget(
        fingerY = y,
        viewportHeight = viewportHeight,
        thumbHeight = thumbHeight,
        lineCount = lineCount,
        visibleLines = visibleLines,
    )

    private fun lineThumb(line: Int): Int? = fastScrollLineThumbOffset(
        line = line,
        lineCount = lineCount,
        viewportHeight = viewportHeight,
        thumbHeight = thumbHeight,
        visibleLines = visibleLines,
    )

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
