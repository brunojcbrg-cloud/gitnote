package io.github.wiiznokes.gitnote.ui.component.markdown

import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarkdownScannerTest {
    @Test
    fun headingsAndBlockMarkersRequireASpace() {
        val spans = MarkdownScanner.scan(
            "# title\n#not-a-title\n- item\n-item\n> quote\n>not-a-quote\n12. item\n12.item"
        )

        assertEquals(listOf(MdKind.H1, MdKind.BULLET, MdKind.QUOTE, MdKind.ORDERED), spans.map { it.kind })
        assertEquals(0..1, spans[0].markers.single())
        assertEquals(2..6, spans[0].range)
        assertTrue(spans[1].markers.isEmpty(), "List markers stay visible and are only colored")
        assertTrue(spans[3].markers.isEmpty(), "Ordered-list markers stay visible")
    }

    @Test
    fun inlineMarkupDoesNotCrossLinesOrAcceptAnUnclosedMarker() {
        val spans = MarkdownScanner.scan("**open\nstill open**\n**closed**")

        assertEquals(1, spans.size)
        assertEquals(MdKind.BOLD, spans.single().kind)
        assertEquals(2, spans.single().line)
        assertEquals("closed", "**open\nstill open**\n**closed**".substring(spans.single().range))
    }

    @Test
    fun scansSupportedInlineKindsWithoutArbitraryNesting() {
        val source = "***both*** **color** _italic_ ~~strike~~ ==mark== `code`"
        val spans = MarkdownScanner.scan(source)

        assertEquals(
            listOf(
                MdKind.BOLD_ITALIC,
                MdKind.BOLD,
                MdKind.ITALIC,
                MdKind.STRIKE,
                MdKind.HIGHLIGHT,
                MdKind.INLINE_CODE,
            ),
            spans.map { it.kind },
        )
    }

    @Test
    fun inlineCodeAndCodeFencesWinOverOtherMarkup() {
        val source = "`**inline**` and **styled**\n```kotlin\n**inside fence**\n```\n_after_"
        val spans = MarkdownScanner.scan(source)

        assertEquals(1, spans.count { it.kind == MdKind.INLINE_CODE })
        assertEquals(1, spans.count { it.kind == MdKind.BOLD })
        assertEquals(3, spans.count { it.kind == MdKind.CODE_FENCE })
        assertEquals(1, spans.count { it.kind == MdKind.ITALIC })
        assertFalse(spans.any { it.kind == MdKind.BOLD && it.line == 2 })
        assertEquals(listOf(1, 2, 3), spans.filter { it.kind == MdKind.CODE_FENCE }.map { it.line })
    }

    @Test
    fun scansTasksAndLinksWithOriginalCoordinates() {
        val source = "- [ ] todo\n* [X] done\nRead [Gitnote](https://example.com)."
        val spans = MarkdownScanner.scan(source)

        assertEquals(MdKind.TASK_TODO, spans[0].kind)
        assertEquals(MdKind.TASK_DONE, spans[1].kind)

        val linkText = spans.single { it.kind == MdKind.LINK_TEXT }
        val linkUrl = spans.single { it.kind == MdKind.LINK_URL }
        assertEquals("Gitnote", source.substring(linkText.range))
        assertEquals("https://example.com", source.substring(linkUrl.range))
        assertEquals("[", source.substring(linkText.markers.first()))
        assertEquals("](https://example.com)", source.substring(linkText.markers.last()))
    }

    @Test
    fun scansSimpleWikilinksWithOriginalCoordinates() {
        val source = "See [[Nome da Nota]] and [[Outra]]."
        val wikilinks = MarkdownScanner.scan(source).filter { it.kind == MdKind.WIKILINK }

        assertEquals(listOf("Nome da Nota", "Outra"), wikilinks.map { source.substring(it.range) })
        assertEquals("[[", source.substring(wikilinks.first().markers.first()))
        assertEquals("]]", source.substring(wikilinks.first().markers.last()))
    }

    @Test
    fun ignoresOutOfScopeAndUnsafeWikilinkForms() {
        val source = "[[Nome|alias]] [[Nome#secao]] ![[embed]] \\[[escaped]] [[ ]] [[unclosed"

        assertFalse(MarkdownScanner.scan(source).any { it.kind == MdKind.WIKILINK })
    }

    @Test
    fun codeKeepsPrecedenceOverWikilinks() {
        val source = "`[[inline]]`\n```\n[[fenced]]\n```\n[[visible]]"
        val spans = MarkdownScanner.scan(source)

        assertEquals(listOf("visible"), spans.filter { it.kind == MdKind.WIKILINK }.map {
            source.substring(it.range)
        })
    }

    @Test
    fun scansTwentyFourThousandCharactersInLinearTime() {
        val line = "## Heading **emphasis** and `code`\n"
        val source = buildString {
            while (length < 24_000) append(line)
        }.take(24_000)

        lateinit var spans: List<MdSpan>
        val elapsedMillis = measureTimeMillis {
            spans = MarkdownScanner.scan(source)
        }

        assertTrue(spans.isNotEmpty())
        assertTrue(elapsedMillis < 2_000, "24k scan took ${elapsedMillis}ms")
    }
}
