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
    fun highlightConsumesMatchingOrLongerEqualsRunsWithoutLeavingMarkers() {
        val cases = listOf(
            "==x==" to (2 to 2),
            "===x===" to (3 to 3),
            "====x====" to (4 to 4),
            "==x===" to (2 to 3),
        )

        cases.forEach { (source, markerLengths) ->
            val span = MarkdownScanner.scan(source).single()

            assertEquals(MdKind.HIGHLIGHT, span.kind, source)
            assertEquals("x", source.substring(span.range), source)
            assertEquals(markerLengths.first, span.markers.first().count(), source)
            assertEquals(markerLengths.second, span.markers.last().count(), source)
            assertEquals(
                source.indices.toSet(),
                (span.range.asSequence() + span.markers.flatMap { it.asSequence() }).toSet(),
                source,
            )
        }
    }

    @Test
    fun highlightInsideInlineOrFencedCodeIsIgnored() {
        val tick = 96.toChar()
        val source = "$tick==inline==$tick\n$tick$tick$tick\n==fenced==\n$tick$tick$tick"

        assertFalse(MarkdownScanner.scan(source).any { it.kind == MdKind.HIGHLIGHT })
    }

    @Test
    fun setextHeadingUnderlineIsNotAHighlight() {
        val source = "Título\n======"

        assertFalse(MarkdownScanner.scan(source).any { it.kind == MdKind.HIGHLIGHT })
    }

    @Test
    fun wideningEqualsDoesNotChangeBoldOrStrikeMarkers() {
        val source = "**negrito** ~~riscado~~"
        val spans = MarkdownScanner.scan(source)

        assertEquals(listOf(MdKind.BOLD, MdKind.STRIKE), spans.map { it.kind })
        assertTrue(spans.all { span -> span.markers.all { it.count() == 2 } })
    }

    @Test
    fun scansAllSupportedWikilinkFormsWithOriginalCoordinates() {
        val cases = listOf(
            WikilinkCase("[[Nome]]", "Nome", "Nome", null, null),
            WikilinkCase("[[Nome|apelido]]", "apelido", "Nome", null, "apelido"),
            WikilinkCase("[[Nome#Secao]]", "Nome", "Nome", "Secao", null),
            WikilinkCase(
                "[[Nome#Secao|apelido]]",
                "apelido",
                "Nome",
                "Secao",
                "apelido",
            ),
            WikilinkCase("[[#Secao]]", "Secao", "", "Secao", null),
            WikilinkCase("[[#Secao|apelido]]", "apelido", "", "Secao", "apelido"),
        )

        cases.forEach { case ->
            val wikilink = MarkdownScanner.scan(case.source)
                .single { it.kind == MdKind.WIKILINK }

            assertEquals(case.display, case.source.substring(wikilink.range), case.source)
            assertEquals(
                WikilinkParts(case.target, case.section, case.alias),
                wikilink.wikilink,
                case.source,
            )
            assertEquals(
                case.source.removeRange(wikilink.markers.last())
                    .removeRange(wikilink.markers.first()),
                case.display,
                case.source,
            )
        }
    }

    @Test
    fun parsesAccentsEmojiHashInAliasAndSpacesInTarget() {
        val cases = listOf(
            WikilinkCase(
                "[[Exame físico|Inspeção e Percussão 🩺]]",
                "Inspeção e Percussão 🩺",
                "Exame físico",
                null,
                "Inspeção e Percussão 🩺",
            ),
            WikilinkCase(
                "[[Nome#Secao|passo #2]]",
                "passo #2",
                "Nome",
                "Secao",
                "passo #2",
            ),
        )

        cases.forEach { case ->
            val wikilink = MarkdownScanner.scan(case.source)
                .single { it.kind == MdKind.WIKILINK }
            assertEquals(case.display, case.source.substring(wikilink.range))
            assertEquals(WikilinkParts(case.target, case.section, case.alias), wikilink.wikilink)
        }
    }

    @Test
    fun ignoresDegenerateEmbeddedEscapedAndUnclosedWikilinks() {
        val source = "[[]] [[|]] [[#]] [[|alias]] [[#|alias]] [[ ]] ![[embed]] " +
            "\\[[escaped]] stray ]] [[unclosed"

        assertFalse(MarkdownScanner.scan(source).any { it.kind == MdKind.WIKILINK })
    }

    @Test
    fun emptyAliasWinsAndProducesAnEmptyContiguousDisplayRange() {
        val noteLink = MarkdownScanner.scan("[[Nome|]]").single()
        val sectionLink = MarkdownScanner.scan("[[#Secao|]]").single()

        assertTrue(noteLink.range.isEmpty())
        assertEquals(WikilinkParts("Nome", null, ""), noteLink.wikilink)
        assertTrue(sectionLink.range.isEmpty())
        assertEquals(WikilinkParts("", "Secao", ""), sectionLink.wikilink)
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

    private data class WikilinkCase(
        val source: String,
        val display: String,
        val target: String,
        val section: String?,
        val alias: String?,
    )
}
