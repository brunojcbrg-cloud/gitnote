package io.github.wiiznokes.gitnote.ui.component.markdown

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import io.github.wiiznokes.gitnote.ui.theme.MarkdownColorScheme

class MarkdownLivePreviewTransformation(
    private val colors: MarkdownColorScheme,
    private val activeLines: Set<Int>,
    private val baseFontSize: TextUnit,
) : VisualTransformation {

    private var ultimoMapeamento: OffsetMapping = OffsetMapping.Identity

    /** Mapeamento que o proprio TextField acabou de usar para desenhar o cursor. */
    fun originalParaTransformado(offset: Int): Int = ultimoMapeamento.originalToTransformed(offset)

    companion object {
        // Cache de uma entrada no companion: mudancas de texto e trocas da linha ativa
        // ainda criam instancias novas. Nesses casos, o mesmo texto reusa a varredura.
        private var cachedSource: String? = null
        private var cachedSpans: List<MdSpan> = emptyList()

        private fun scanCached(source: String): List<MdSpan> {
            cachedSource?.let { if (it == source) return cachedSpans }
            val spans = MarkdownScanner.scan(source)
            cachedSource = source
            cachedSpans = spans
            return spans
        }

        /** Exposto so para o teste provar reaproveitamento por identidade de lista. */
        internal fun scanCachedForTest(source: String): List<MdSpan> = scanCached(source)
    }

    override fun filter(text: AnnotatedString): TransformedText {
        val source = text.text
        val spans = scanCached(source)

        // Formula fora da linha ativa e SUBSTITUIDA pelo texto convertido. Na linha
        // ativa ela aparece crua, pela mesma razao que os marcadores aparecem: sem
        // isso ele nao consegue editar o que esta embaixo do cursor.
        val formulas = HashMap<Int, MdSpan>()
        spans.forEach { span ->
            if (span.kind == MdKind.MATH && span.line !in activeLines && span.math != null) {
                val de = span.markers.firstOrNull()?.first ?: span.range.first
                formulas[de] = span
            }
        }

        val hiddenRanges = normalizeRanges(
            ranges = spans.asSequence()
                .filterNot { it.line in activeLines }
                .filterNot { it.kind == MdKind.MATH }
                .flatMap { it.markers.asSequence() }
                .toList(),
            textLength = source.length,
        )

        var folga = 0
        formulas.values.forEach { span ->
            val convertido = span.math?.text?.length ?: 0
            val original = faixaTotal(span).count()
            if (convertido > original) folga += convertido - original
        }

        val originalToTransformed = IntArray(source.length + 1)
        val transformedToOriginalBuffer = IntArray(source.length + 1 + folga)
        val transformed = AnnotatedString.Builder()
        val estilosDeIndice = mutableListOf<Triple<Int, Int, SpanStyle>>()

        var hiddenRangeIndex = 0
        var transformedOffset = 0
        var originalOffset = 0
        while (originalOffset < source.length) {
            val formula = formulas[originalOffset]
            if (formula != null) {
                val faixa = faixaTotal(formula)
                val math = formula.math ?: MathText("")
                val inicioTransformado = transformedOffset
                // Todo o trecho original colapsa no inicio do texto convertido.
                for (offset in faixa) originalToTransformed[offset] = inicioTransformado
                math.text.forEach { caractere ->
                    transformedToOriginalBuffer[transformedOffset] = faixa.first
                    transformed.append(caractere)
                    transformedOffset++
                }
                math.subscripts.forEach { faixaIndice ->
                    estilosDeIndice += Triple(
                        inicioTransformado + faixaIndice.first,
                        inicioTransformado + faixaIndice.last + 1,
                        SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = baseFontSize * 0.75f),
                    )
                }
                math.superscripts.forEach { faixaIndice ->
                    estilosDeIndice += Triple(
                        inicioTransformado + faixaIndice.first,
                        inicioTransformado + faixaIndice.last + 1,
                        SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = baseFontSize * 0.75f),
                    )
                }
                if (transformedOffset > inicioTransformado) {
                    estilosDeIndice += Triple(inicioTransformado, transformedOffset, styleFor(MdKind.MATH))
                }
                originalOffset = faixa.last + 1
                while (hiddenRangeIndex < hiddenRanges.size &&
                    originalOffset > hiddenRanges[hiddenRangeIndex].last
                ) {
                    hiddenRangeIndex++
                }
                continue
            }

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
            originalOffset++
        }

        originalToTransformed[source.length] = transformedOffset
        transformedToOriginalBuffer[transformedOffset] = source.length
        val transformedToOriginal = transformedToOriginalBuffer.copyOf(transformedOffset + 1)

        spans.forEach { span ->
            if (span.kind == MdKind.MATH && formulas.containsKey(span.markers.firstOrNull()?.first ?: span.range.first)) {
                return@forEach
            }
            addStyle(transformed, span.range, styleFor(span.kind), originalToTransformed)
            span.markers.forEach { marker ->
                addStyle(transformed, marker, styleFor(span.kind), originalToTransformed)
            }
        }
        // Os indices entram depois para vencerem a cor da formula.
        estilosDeIndice.sortedBy { it.third.baselineShift != null }.forEach { (de, ate, estilo) ->
            if (de < ate) transformed.addStyle(estilo, de, ate)
        }

        val mapeamento = ArrayOffsetMapping(
            originalToTransformed = originalToTransformed,
            transformedToOriginal = transformedToOriginal,
        )
        ultimoMapeamento = mapeamento
        return TransformedText(
            text = transformed.toAnnotatedString(),
            offsetMapping = mapeamento,
        )
    }

    /** Da abertura do delimitador ao fim do fechamento, marcadores inclusos. */
    private fun faixaTotal(span: MdSpan): IntRange {
        val de = span.markers.firstOrNull()?.first ?: span.range.first
        val ate = span.markers.lastOrNull()?.last ?: span.range.last
        return de..ate
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
        MdKind.MATH -> SpanStyle(color = colors.code)
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
