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

/**
 * Quantas linhas de texto cabem na viewport, estimadas pela altura de linha.
 * E uma estimativa: linhas longas quebram em varias linhas visuais, entao o valor
 * real e menor. Serve so para decidir se a barra aparece.
 */
internal fun estimatedVisibleLines(viewportHeight: Float, lineHeight: Float): Int {
    if (viewportHeight <= 0f || lineHeight <= 0f) return 0
    return (viewportHeight / lineHeight).toInt()
}

/**
 * Linha de destino para o dedo na faixa de rolagem do editor.
 * Devolve null quando a nota cabe na tela — ai nao ha o que rolar.
 */
internal fun fastScrollLineTarget(
    fingerY: Float,
    viewportHeight: Float,
    thumbHeight: Float,
    lineCount: Int,
    visibleLines: Int,
): Int? {
    if (lineCount <= 1 || visibleLines <= 0 || lineCount <= visibleLines) return null
    if (viewportHeight <= thumbHeight) return null

    val trackRange = viewportHeight - thumbHeight
    val fraction = ((fingerY - thumbHeight / 2f) / trackRange).coerceIn(0f, 1f)
    return (fraction * (lineCount - 1)).roundToInt().coerceIn(0, lineCount - 1)
}

/**
 * Posicao do polegar para a linha atual do cursor, na mesma escala do [fastScrollLineTarget].
 */
internal fun fastScrollLineThumbOffset(
    line: Int,
    lineCount: Int,
    viewportHeight: Float,
    thumbHeight: Float,
    visibleLines: Int,
): Int? {
    if (lineCount <= 1 || visibleLines <= 0 || lineCount <= visibleLines) return null
    if (viewportHeight <= thumbHeight) return null

    val trackRange = viewportHeight - thumbHeight
    val fraction = (line.toFloat() / (lineCount - 1)).coerceIn(0f, 1f)
    return (fraction * trackRange).roundToInt()
}
