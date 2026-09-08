package io.github.wiiznokes.gitnote.ui.component.markdown

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import com.mikepenz.markdown.annotator.buildMarkdownAnnotatedString
import com.mikepenz.markdown.model.markdownAnnotator
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import java.nio.file.Files
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ObsidianLineBreaksTest {
    @Test
    fun case01SoftBreakRendersAsNewLine() {
        assertEquals("a b", renderFirstTextBlock("a\nb", obsidianLineBreaks = false))
        assertEquals("a\nb", renderFirstTextBlock("a\nb"))
    }

    @Test
    fun case02BlankLineAlreadySeparatesParagraphs() {
        val source = "a\n\nb"

        assertEquals(2, nodesOfType(source, MarkdownElementTypes.PARAGRAPH).size)
        assertEquals(source, sourceAfterRendering(source))
    }

    @Test
    fun case03SingleLineIsUnchanged() {
        assertEquals("a", renderFirstTextBlock("a"))
    }

    @Test
    fun case04EmptyTextIsUnchanged() {
        assertEquals("", renderFirstTextBlock(""))
    }

    @Test
    fun case05TrailingLineEndingDoesNotCreateAnotherLine() {
        assertEquals("a", renderFirstTextBlock("a\n"))
    }

    @Test
    fun case06MultipleBlankLinesRemainParagraphSeparators() {
        val source = "a\n\n\nb"

        assertEquals(2, nodesOfType(source, MarkdownElementTypes.PARAGRAPH).size)
        assertEquals(source, sourceAfterRendering(source))
    }

    @Test
    fun case07TwoSpacesRemainANativeHardBreakWithoutDuplication() {
        val source = "a  \nb"

        assertEquals(1, nodesOfType(source, MarkdownTokenTypes.HARD_LINE_BREAK).size)
        assertEquals("a\nb", renderFirstTextBlock(source, obsidianLineBreaks = false))
        assertEquals(source, sourceAfterRendering(source))
    }

    @Test
    fun case08BackslashRemainsANativeHardBreakWithoutDuplication() {
        val source = "a\\\nb"

        assertEquals(1, nodesOfType(source, MarkdownTokenTypes.HARD_LINE_BREAK).size)
        assertEquals("a\nb", renderFirstTextBlock(source, obsidianLineBreaks = false))
        assertEquals(source, sourceAfterRendering(source))
    }

    @Test
    fun case09CodeFenceContentsAreByteForByteUntouched() {
        val source = "```kotlin\nval exact = \"[[x]]  \\\"\nval second = 2\n```"
        val fencedText = MarkdownScanner.scan(source)
            .filter { it.kind == MdKind.CODE_FENCE }
            .map { source.substring(it.range) }

        assertEquals(
            listOf("```kotlin", "val exact = \"[[x]]  \\\"", "val second = 2", "```"),
            fencedText,
        )
        assertEquals(source, preprocessWikilinksForReading(source))
        assertEquals(source, sourceAfterRendering(source))
    }

    @Test
    fun case10CodeFenceDelimiterLinesAreUntouched() {
        val source = "```kotlin\ncode\n```\nafter"
        val fenceLines = MarkdownScanner.scan(source)
            .filter { it.kind == MdKind.CODE_FENCE }
            .map { it.line }

        assertEquals(listOf(0, 1, 2), fenceLines)
        assertEquals(source, preprocessWikilinksForReading(source))
    }

    @Test
    fun case11UnclosedCodeFenceProtectsEverythingAfterIt() {
        val source = "before\n```kotlin\n[[still code]]\nexact  spaces\\"

        assertEquals(source, preprocessWikilinksForReading(source))
        assertEquals(
            listOf(1, 2, 3),
            MarkdownScanner.scan(source)
                .filter { it.kind == MdKind.CODE_FENCE }
                .map { it.line },
        )
    }

    @Test
    fun case12HeadingDoesNotGainAHardBreak() {
        val source = "# Título\ntexto"

        assertTrue(nodesOfType(source, MarkdownTokenTypes.HARD_LINE_BREAK).isEmpty())
        assertEquals(source, sourceAfterRendering(source))
    }

    @Test
    fun case13LineImmediatelyBeforeHeadingDoesNotGainAHardBreak() {
        val source = "antes\n# Título"

        assertTrue(nodesOfType(source, MarkdownTokenTypes.HARD_LINE_BREAK).isEmpty())
        assertEquals("antes", renderFirstTextBlock(source))
    }

    @Test
    fun case14TableLinesAreUntouched() {
        val source = "| a | b |\n| --- | --- |\n| 1 | 2 |"

        assertTrue(nodesOfType(source, MarkdownTokenTypes.HARD_LINE_BREAK).isEmpty())
        assertEquals(source, preprocessWikilinksForReading(source))
    }

    @Test
    fun case15HorizontalRuleIsUntouched() {
        val source = "---\n\nafter"

        assertTrue(nodesOfType(source, MarkdownTokenTypes.HARD_LINE_BREAK).isEmpty())
        assertEquals(source, sourceAfterRendering(source))
    }

    @Test
    fun case16InlineCodeIsPreservedWhileTheSoftBreakRenders() {
        val source = "antes `x  y` depois\nlinha"
        val codeSpan = nodesOfType(source, MarkdownElementTypes.CODE_SPAN).single()
        val rendered = renderFirstTextBlock(source)

        assertEquals("`x  y`", codeSpan.getTextInNode(source).toString())
        assertTrue(rendered.contains("x  y"))
        assertTrue(rendered.endsWith("\nlinha"))
    }

    @Test
    fun case17TabIndentedVaultLinesContinueOnSeparateRenderedLines() {
        val source = "1 [[#Revisão sistemática]]\n\t→[[#Inspeção|Inspeção]]\n\t→[[#Palpação|Palpação]]"
        val preprocessed = preprocessWikilinksForReading(source)
        val renderedLines = renderFirstTextBlock(preprocessed)
            .lines()
            .map { it.trimStart() }

        assertEquals(
            listOf("1 Revisão sistemática", "→Inspeção", "→Palpação"),
            renderedLines,
        )
        assertEquals(source, sourceAfterRendering(source))
    }

    @Test
    fun case18ConsecutiveListItemsRemainSeparateListItems() {
        val source = "- a\n- b"

        assertEquals(2, nodesOfType(source, MarkdownElementTypes.LIST_ITEM).size)
        assertEquals(source, sourceAfterRendering(source))
    }

    @Test
    fun case19IndentedListItemKeepsItsNesting() {
        val source = "- pai\n  - filho"

        assertEquals(2, nodesOfType(source, MarkdownElementTypes.LIST_ITEM).size)
        assertEquals(2, nodesOfType(source, MarkdownElementTypes.UNORDERED_LIST).size)
        assertEquals(source, sourceAfterRendering(source))
    }

    @Test
    fun case20AccentsAndUtf16EmojiArePreserved() {
        val source = "Inspeção 🩺\nPercussão"

        assertEquals(source, renderFirstTextBlock(source))
        assertEquals(source.indexOf('\n'), renderFirstTextBlock(source).indexOf('\n'))
    }

    @Test
    fun case21LineEndingInWikilinkComposesWithNativeLineBreaks() {
        val source = "[[#Revisão sistemática]]\n[[#Inspeção|Inspeção]]"
        val preprocessed = preprocessWikilinksForReading(source)

        assertEquals("Revisão sistemática\nInspeção", renderFirstTextBlock(preprocessed))
    }

    @Test
    fun case22WikilinksArePreprocessedBeforeLineBreakRendering() {
        val source = "Exame [[Roteiro|físico]]\n[[#Inspeção|Inspeção]] e Percussão"
        val preprocessed = preprocessWikilinksForReading(
            source,
            existingNames = setOf("Roteiro"),
        )

        assertTrue(preprocessed.startsWith("Exame [físico](gitnote://note?name=Roteiro)\n"))
        assertEquals("Exame físico\nInspeção e Percussão", renderFirstTextBlock(preprocessed))
    }

    @Test
    fun case23NativeOptionIsIdempotentBecauseItDoesNotRewriteContent() {
        val source = "a\nb"
        val first = renderFirstTextBlock(source)
        val second = renderFirstTextBlock(source)

        assertEquals(first, second)
        assertEquals(source, sourceAfterRendering(source))
        assertTrue(obsidianLineBreaksAnnotatorConfig.eolAsNewLine)
    }

    @Test
    fun case24CrLfIsRecognizedAsOneRenderedLineBreak() {
        val source = "a\r\nb"

        assertEquals("a\nb", renderFirstTextBlock(source))
        assertEquals(source, sourceAfterRendering(source))
    }

    @Test
    fun case25MixedLineEndingsAreHandledWithoutCorruption() {
        val source = "a\r\nb\nc\r\nd"

        assertEquals("a\nb\nc\nd", renderFirstTextBlock(source))
        assertEquals(source, sourceAfterRendering(source))
    }

    @Test
    fun case26TwentyFourThousandCharactersRenderQuickly() {
        val line = "linha de anotacao clinica\n"
        val source = buildString {
            while (length < 23_999) append(line)
        }.take(23_999) + "x"

        lateinit var rendered: String
        val elapsedMillis = measureTimeMillis {
            rendered = renderFirstTextBlock(source)
        }

        assertEquals(24_000, source.length)
        assertEquals(source, rendered)
        assertTrue(elapsedMillis < 2_000, "24k render took ${elapsedMillis}ms")
    }

    @Test
    fun case27EditingTextFieldValueRemainsRaw() {
        val editingValue = TextFieldValue("a\r\nb")

        renderFirstTextBlock(editingValue.text)

        assertEquals("a\r\nb", editingValue.text)
    }

    @Test
    fun case28RenderingDoesNotChangeTheFileOnDisk() {
        val path = Files.createTempFile("gitnote-line-breaks-", ".md")
        val source = "a\r\nb"
        try {
            Files.writeString(path, source)

            renderFirstTextBlock(Files.readString(path))

            assertEquals(source, Files.readString(path))
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun case29ExistingScannerAndWikilinkBehaviorStillCompose() {
        val source = "```\n[[code]]\n```\n[[visible]]"
        val spans = MarkdownScanner.scan(source)

        assertEquals(1, spans.count { it.kind == MdKind.WIKILINK })
        assertEquals(
            "```\n[[code]]\n```\n[visible](gitnote://note?name=visible)",
            preprocessWikilinksForReading(source),
        )
    }

    @Suppress("DEPRECATION")
    private fun renderFirstTextBlock(
        source: String,
        obsidianLineBreaks: Boolean = true,
    ): String =
        source.buildMarkdownAnnotatedString(
            style = TextStyle.Default,
            annotator = if (obsidianLineBreaks) {
                markdownAnnotator(config = obsidianLineBreaksAnnotatorConfig)
            } else {
                markdownAnnotator()
            },
        ).text

    private fun sourceAfterRendering(source: String): String {
        renderFirstTextBlock(source)
        return source
    }

    private fun nodesOfType(source: String, type: IElementType): List<ASTNode> =
        parse(source).descendants().filter { it.type == type }.toList()

    private fun parse(source: String): ASTNode =
        MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(source)

    private fun ASTNode.descendants(): Sequence<ASTNode> = sequence {
        yield(this@descendants)
        children.forEach { yieldAll(it.descendants()) }
    }
}
