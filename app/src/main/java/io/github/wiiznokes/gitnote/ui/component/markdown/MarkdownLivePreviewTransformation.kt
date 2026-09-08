package io.github.wiiznokes.gitnote.ui.component.markdown

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import io.github.wiiznokes.gitnote.ui.theme.MarkdownColorScheme

class MarkdownLivePreviewTransformation(
    private val colors: MarkdownColorScheme,
    private val activeLines: Set<Int>,
    private val baseFontSize: TextUnit,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val source = text.text
        val spans = MarkdownScanner.scan(source)
        val hiddenRanges = normalizeRanges(
            ranges = spans.asSequence()
                .filterNot { it.line in activeLines }
                .flatMap { it.markers.asSequence() }
                .toList(),
            textLength = source.length,
        )

        val originalToTransformed = IntArray(source.length + 1)
        val transformedToOriginalBuffer = IntArray(source.length + 1)
        val transformed = AnnotatedString.Builder()

        var hiddenRangeIndex = 0
        var transformedOffset = 0
        for (originalOffset in source.indices) {
            while (hiddenRangeIndex < hiddenRanges.size &&
                originalOffset > hiddenRanges[hiddenRangeIndex].last
            ) {
                hiddenRangeIndex++
            }

            val isHidden = hiddenRangeIndex < hiddenRanges.size &&
                originalOffset in hiddenRanges[hiddenRangeIndex]
            originalToTransformed[originalOffset] = transformedOffset

            if (!isHidden) {
                transformedToOriginalBuffer[transformedOffset] = originalOffset
                transformed.append(source[originalOffset])
                transformedOffset++
            }
        }

        originalToTransformed[source.length] = transformedOffset
        transformedToOriginalBuffer[transformedOffset] = source.length
        val transformedToOriginal = transformedToOriginalBuffer.copyOf(transformedOffset + 1)

        spans.forEach { span ->
            addStyle(transformed, span.range, styleFor(span.kind), originalToTransformed)
            span.markers.forEach { marker ->
                addStyle(transformed, marker, styleFor(span.kind), originalToTransformed)
            }
        }

        return TransformedText(
            text = transformed.toAnnotatedString(),
            offsetMapping = ArrayOffsetMapping(
                originalToTransformed = originalToTransformed,
                transformedToOriginal = transformedToOriginal,
            ),
        )
    }

    private fun styleFor(kind: MdKind): SpanStyle = when (kind) {
        MdKind.H1 -> SpanStyle(color = colors.h1, fontSize = baseFontSize * 1.6f)
        MdKind.H2 -> SpanStyle(color = colors.h2, fontSize = baseFontSize * 1.4f)
        MdKind.H3 -> SpanStyle(color = colors.h3, fontSize = baseFontSize * 1.2f)
        MdKind.H4, MdKind.H5, MdKind.H6 -> SpanStyle(color = colors.h4)
        MdKind.BOLD -> SpanStyle(color = colors.emphasis)
        MdKind.ITALIC -> SpanStyle(color = colors.emphasis2, fontStyle = FontStyle.Italic)
        MdKind.BOLD_ITALIC -> SpanStyle(
            color = colors.emphasis,
            fontStyle = FontStyle.Italic,
        )
        MdKind.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
        MdKind.HIGHLIGHT -> SpanStyle(
            color = colors.highlight,
            background = colors.highlightBackground,
        )
        MdKind.INLINE_CODE, MdKind.CODE_FENCE -> SpanStyle(
            color = colors.code,
            background = colors.codeBackground,
            fontFamily = FontFamily.Monospace,
        )
        MdKind.QUOTE -> SpanStyle(color = colors.quote, fontStyle = FontStyle.Italic)
        MdKind.BULLET, MdKind.ORDERED, MdKind.TASK_DONE, MdKind.TASK_TODO ->
            SpanStyle(color = colors.listMarker)
        MdKind.WIKILINK, MdKind.LINK_TEXT -> SpanStyle(
            color = colors.link,
            textDecoration = TextDecoration.Underline,
        )
        MdKind.LINK_URL -> SpanStyle(color = colors.link)
    }

    private fun addStyle(
        builder: AnnotatedString.Builder,
        originalRange: IntRange,
        style: SpanStyle,
        originalToTransformed: IntArray,
    ) {
        if (originalRange.isEmpty()) return

        val originalStart = originalRange.first.coerceIn(0, originalToTransformed.lastIndex)
        val originalEnd = (originalRange.last + 1).coerceIn(0, originalToTransformed.lastIndex)
        val transformedStart = originalToTransformed[originalStart]
        val transformedEnd = originalToTransformed[originalEnd]
        if (transformedStart < transformedEnd) {
            builder.addStyle(style, transformedStart, transformedEnd)
        }
    }
}

fun activeMarkdownLines(text: String, selectionStart: Int, selectionEnd: Int): Set<Int> {
    val start = selectionStart.coerceIn(0, text.length)
    val end = selectionEnd.coerceIn(0, text.length)
    val maxOffset = maxOf(start, end)
    var currentLine = 0
    var startLine = 0
    var endLine = 0

    for (index in 0 until maxOffset) {
        if (text[index] == '\n') currentLine++
        if (index + 1 == start) startLine = currentLine
        if (index + 1 == end) endLine = currentLine
    }

    if (start == 0) startLine = 0
    if (end == 0) endLine = 0
    return if (startLine == endLine) setOf(startLine) else setOf(startLine, endLine)
}

private fun normalizeRanges(ranges: List<IntRange>, textLength: Int): List<IntRange> {
    if (ranges.isEmpty() || textLength == 0) return emptyList()

    val clipped = ranges.asSequence()
        .filterNot { it.isEmpty() }
        .mapNotNull { range ->
            val start = range.first.coerceIn(0, textLength)
            val end = range.last.coerceIn(-1, textLength - 1)
            if (start <= end) start..end else null
        }
        .sortedWith(compareBy<IntRange> { it.first }.thenBy { it.last })
        .toList()
    if (clipped.isEmpty()) return emptyList()

    val merged = mutableListOf<IntRange>()
    var current = clipped.first()
    for (index in 1 until clipped.size) {
        val next = clipped[index]
        if (next.first <= current.last + 1) {
            current = current.first..maxOf(current.last, next.last)
        } else {
            merged += current
            current = next
        }
    }
    merged += current
    return merged
}

private class ArrayOffsetMapping(
    private val originalToTransformed: IntArray,
    private val transformedToOriginal: IntArray,
) : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int =
        originalToTransformed[offset.coerceIn(0, originalToTransformed.lastIndex)]

    override fun transformedToOriginal(offset: Int): Int =
        transformedToOriginal[offset.coerceIn(0, transformedToOriginal.lastIndex)]
}
