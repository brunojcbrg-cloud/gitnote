package io.github.wiiznokes.gitnote.ui.component.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.wiiznokes.gitnote.ui.theme.MarkdownColorScheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarkdownLivePreviewTransformationTest {
    private val colors = MarkdownColorScheme(
        h1 = Color(0xFF000001),
        h2 = Color(0xFF000002),
        h3 = Color(0xFF000003),
        h4 = Color(0xFF000004),
        emphasis = Color(0xFF000005),
        emphasis2 = Color(0xFF000006),
        code = Color(0xFF000007),
        codeBackground = Color(0xFF000008),
        quote = Color(0xFF000009),
        listMarker = Color(0xFF00000A),
        link = Color(0xFF00000B),
        highlight = Color(0xFF00000C),
        highlightBackground = Color(0xFF00000D),
    )

    @Test
    fun emptyTextHasSafeClampedMappings() {
        val result = transform("")

        assertEquals("", result.text.text)
        assertEquals(0, result.offsetMapping.originalToTransformed(-100))
        assertEquals(0, result.offsetMapping.originalToTransformed(100))
        assertEquals(0, result.offsetMapping.transformedToOriginal(-100))
        assertEquals(0, result.offsetMapping.transformedToOriginal(100))
    }

    @Test
    fun plainTextUsesIdentityPositions() {
        val source = "plain á😀 text"
        val result = transform(source)

        assertEquals(source, result.text.text)
        for (offset in 0..source.length) {
            assertEquals(offset, result.offsetMapping.originalToTransformed(offset))
            assertEquals(offset, result.offsetMapping.transformedToOriginal(offset))
        }
        assertMappingsAreSafeAndMonotonic(source, result.text.text, result.offsetMapping)
    }

    @Test
    fun hidesMarkersAtTheFirstAndLastCharacter() {
        val source = "**text**"
        val result = transform(source)

        assertEquals("text", result.text.text)
        assertEquals(0, result.offsetMapping.originalToTransformed(0))
        assertEquals(0, result.offsetMapping.originalToTransformed(2))
        assertEquals(4, result.offsetMapping.originalToTransformed(source.length))
        assertEquals(source.length, result.offsetMapping.transformedToOriginal(4))
        assertMappingsAreSafeAndMonotonic(source, result.text.text, result.offsetMapping)
    }

    @Test
    fun leavesAnUnclosedMarkerUntouched() {
        val source = "**text"
        val result = transform(source)

        assertEquals(source, result.text.text)
        assertMappingsAreSafeAndMonotonic(source, result.text.text, result.offsetMapping)
    }

    @Test
    fun textMadeOnlyOfMarkersCanCollapseSafely() {
        val source = "# "
        val result = transform(source)

        assertEquals("", result.text.text)
        assertMappingsAreSafeAndMonotonic(source, result.text.text, result.offsetMapping)
    }

    @Test
    fun normalizesAdjacentMarkerRanges() {
        val source = "**a**_b_"
        val result = transform(source)

        assertEquals("ab", result.text.text)
        assertMappingsAreSafeAndMonotonic(source, result.text.text, result.offsetMapping)
        for (offset in 0..result.text.length) {
            val original = result.offsetMapping.transformedToOriginal(offset)
            assertEquals(offset, result.offsetMapping.originalToTransformed(original))
        }
    }

    @Test
    fun revealsMarkersOnBothSelectionEndpointLines() {
        val source = "**first**\n_middle_\n~~last~~"
        val activeLines = activeMarkdownLines(
            text = source,
            selectionStart = source.indexOf("first"),
            selectionEnd = source.indexOf("last"),
        )
        val result = transform(source, activeLines)

        assertEquals(setOf(0, 2), activeLines)
        assertEquals("**first**\nmiddle\n~~last~~", result.text.text)
        assertMappingsAreSafeAndMonotonic(source, result.text.text, result.offsetMapping)
    }

    @Test
    fun activeLineChangesWhichMarkersAreVisible() {
        val source = "**one**\n_two_"

        assertEquals("**one**\ntwo", transform(source, setOf(0)).text.text)
        assertEquals("one\n_two_", transform(source, setOf(1)).text.text)
    }

    @Test
    fun hidesLinkDestinationButKeepsListMarkers() {
        val source = "- Read [Gitnote](https://example.com)"
        val result = transform(source)

        assertEquals("- Read Gitnote", result.text.text)
        assertMappingsAreSafeAndMonotonic(source, result.text.text, result.offsetMapping)
    }

    @Test
    fun hidesWikilinkMarkersWithoutChangingTheRawCoordinates() {
        val source = "Open [[Nome da Nota]] now"
        val result = transform(source)

        assertEquals("Open Nome da Nota now", result.text.text)
        assertMappingsAreSafeAndMonotonic(source, result.text.text, result.offsetMapping)
        assertTrue(result.text.spanStyles.any { it.item.color == colors.link })
    }

    @Test
    fun revealsWikilinkMarkersOnTheActiveLine() {
        val source = "Open [[Nome da Nota]]"

        assertEquals(source, transform(source, setOf(0)).text.text)
    }

    @Test
    fun allWikilinkFormsPreserveOffsetMappingsInsideAndOutsideTheActiveLine() {
        val cases = listOf(
            "[[Nome]]" to "Nome",
            "[[Nome|apelido]]" to "apelido",
            "[[Nome#Secao]]" to "Nome",
            "[[Nome#Secao|apelido]]" to "apelido",
            "[[#Secao]]" to "Secao",
            "[[#Secao|apelido]]" to "apelido",
        )

        cases.forEach { (wikilink, display) ->
            val source = "$wikilink\nplain"
            val insideLines = activeMarkdownLines(source, 2, 2)
            val outsideLines = activeMarkdownLines(source, source.lastIndex, source.lastIndex)
            val inside = transform(source, insideLines)
            val outside = transform(source, outsideLines)

            assertEquals(source, inside.text.text, "inside $wikilink")
            assertEquals("$display\nplain", outside.text.text, "outside $wikilink")
            assertMappingsAreSafeAndMonotonic(source, inside.text.text, inside.offsetMapping)
            assertMappingsAreSafeAndMonotonic(source, outside.text.text, outside.offsetMapping)
        }
    }

    @Test
    fun utf16EmojiAndAccentsKeepValidOffsets() {
        val source = "**á😀**"
        val result = transform(source)

        assertEquals("á😀", result.text.text)
        assertEquals(3, result.text.length, "The emoji occupies two UTF-16 positions")
        assertMappingsAreSafeAndMonotonic(source, result.text.text, result.offsetMapping)
        for (offset in 0..result.text.length) {
            val original = result.offsetMapping.transformedToOriginal(offset)
            assertEquals(offset, result.offsetMapping.originalToTransformed(original))
        }
    }

    @Test
    fun emphasisUsesColorAndNeverBoldWeight() {
        val result = transform("**color**")
        val emphasisStyle = result.text.spanStyles.single { it.item.color == colors.emphasis }.item

        assertEquals(colors.emphasis, emphasisStyle.color)
        assertNull(emphasisStyle.fontWeight)
        assertTrue(emphasisStyle.fontWeight != FontWeight.Bold)
    }

    @Test
    fun mapsEveryOffsetInALongNoteWithinBounds() {
        val line = "# Title\n**á😀** [link](https://example.com)\n"
        val source = buildString {
            while (length < 24_000) append(line)
        }.take(24_000)
        val result = transform(source)

        assertMappingsAreSafeAndMonotonic(source, result.text.text, result.offsetMapping)
    }

    private fun transform(source: String, activeLines: Set<Int> = emptySet()) =
        MarkdownLivePreviewTransformation(
            colors = colors,
            activeLines = activeLines,
            baseFontSize = 16.sp,
        ).filter(AnnotatedString(source))

    private fun assertMappingsAreSafeAndMonotonic(
        original: String,
        transformed: String,
        mapping: androidx.compose.ui.text.input.OffsetMapping,
    ) {
        var previousTransformed = 0
        for (offset in 0..original.length) {
            val mapped = mapping.originalToTransformed(offset)
            assertTrue(mapped in 0..transformed.length, "o2t[$offset]=$mapped")
            assertTrue(mapped >= previousTransformed, "o2t is not monotonic at $offset")
            previousTransformed = mapped
        }

        var previousOriginal = 0
        for (offset in 0..transformed.length) {
            val mapped = mapping.transformedToOriginal(offset)
            assertTrue(mapped in 0..original.length, "t2o[$offset]=$mapped")
            assertTrue(mapped >= previousOriginal, "t2o is not monotonic at $offset")
            assertEquals(
                offset,
                mapping.originalToTransformed(mapped),
                "transformed round trip failed at $offset",
            )
            previousOriginal = mapped
        }

        assertTrue(mapping.originalToTransformed(-1) in 0..transformed.length)
        assertTrue(mapping.originalToTransformed(Int.MAX_VALUE) in 0..transformed.length)
        assertTrue(mapping.transformedToOriginal(-1) in 0..original.length)
        assertTrue(mapping.transformedToOriginal(Int.MAX_VALUE) in 0..original.length)
    }
}
