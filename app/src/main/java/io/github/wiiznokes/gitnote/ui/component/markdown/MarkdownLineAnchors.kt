package io.github.wiiznokes.gitnote.ui.component.markdown

fun lineStartOffsets(text: String): IntArray {
    val offsets = IntArray(text.count { it == '\n' } + 1)
    var line = 1
    text.forEachIndexed { index, character ->
        if (character == '\n') offsets[line++] = index + 1
    }
    return offsets
}

fun lineOfOffset(text: String, offset: Int): Int =
    lineOfOffset(lineStartOffsets(text), offset.coerceIn(0, text.length))

fun lineOfOffset(lineStarts: IntArray, offset: Int): Int {
    if (lineStarts.isEmpty()) return 0
    val result = lineStarts.binarySearch(offset.coerceAtLeast(0))
    return if (result >= 0) result else (-result - 2).coerceAtLeast(0)
}

fun offsetOfLineStart(text: String, line: Int): Int {
    val offsets = lineStartOffsets(text)
    return offsets[line.coerceIn(0, offsets.lastIndex)]
}

/** Returns null when [line] is before the first measured anchor. */
fun nearestAnchorAtOrBefore(line: Int, anchors: Map<Int, Int>): Int? = anchors
    .asSequence()
    .filter { (anchorLine, _) -> anchorLine <= line }
    .maxByOrNull { (anchorLine, _) -> anchorLine }
    ?.value

/** Returns the closest line above [position], or the first measured line when none is above it. */
fun nearestLineAtOrBefore(position: Int, anchors: Map<Int, Int>): Int? {
    if (anchors.isEmpty()) return null
    return anchors
        .asSequence()
        .filter { (_, anchorPosition) -> anchorPosition <= position }
        .maxByOrNull { (_, anchorPosition) -> anchorPosition }
        ?.key
        ?: anchors.minByOrNull { (_, anchorPosition) -> anchorPosition }?.key
}

/** Returns the first line at or below the viewport top, or the last measured line past the end. */
fun firstLineAtOrAfter(position: Int, anchors: Map<Int, Int>): Int? {
    if (anchors.isEmpty()) return null
    return anchors
        .asSequence()
        .filter { (_, anchorPosition) -> anchorPosition >= position }
        .minByOrNull { (_, anchorPosition) -> anchorPosition }
        ?.key
        ?: anchors.maxByOrNull { (_, anchorPosition) -> anchorPosition }?.key
}
