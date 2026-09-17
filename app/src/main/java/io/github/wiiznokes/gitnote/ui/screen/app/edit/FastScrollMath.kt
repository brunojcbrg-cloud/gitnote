package io.github.wiiznokes.gitnote.ui.screen.app.edit

import kotlin.math.roundToInt

internal fun fastScrollTargetOffset(
    fingerY: Float,
    viewportHeight: Float,
    thumbHeight: Float,
    maxValue: Int,
): Int? {
    if (maxValue <= 0 || maxValue == Int.MAX_VALUE || viewportHeight <= thumbHeight) return null

    val trackRange = viewportHeight - thumbHeight
    val fraction = ((fingerY - thumbHeight / 2f) / trackRange).coerceIn(0f, 1f)
    return (fraction * maxValue).roundToInt()
}

internal fun fastScrollThumbOffset(
    scrollValue: Int,
    viewportHeight: Float,
    thumbHeight: Float,
    maxValue: Int,
): Int? {
    if (maxValue <= 0 || maxValue == Int.MAX_VALUE || viewportHeight <= thumbHeight) return null

    val trackRange = viewportHeight - thumbHeight
    val fraction = scrollValue.toFloat().div(maxValue).coerceIn(0f, 1f)
    return (fraction * trackRange).roundToInt()
}
