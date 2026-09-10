package io.github.wiiznokes.gitnote.ui.component.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WikilinkSupportTest {
    @Test
    fun preprocessesAllSixWikilinkFormsOutsideCode() {
        val source = "[[Nome]] [[Nome|apelido]] [[Nome#Secao]] " +
            "[[Nome#Secao|apelido]] [[#Secao]] [[#Secao|apelido]] " +
            "`[[codigo]]` ![[embed]]"
        val rendered = preprocessWikilinksForReading(source)

        assertEquals(
                "[Nome](gitnote://note?name=Nome) " +
                "[apelido](gitnote://note?name=Nome) " +
                "[Nome](gitnote://note?name=Nome&section=Secao) " +
                "[apelido](gitnote://note?name=Nome&section=Secao) " +
                "[Secao](gitnote://section?name=Secao) " +
                "[apelido](gitnote://section?name=Secao) " +
                "`[[codigo]]` ![[embed]]",
            rendered,
        )
        assertEquals(setOf("Nome"), wikilinkNames(source))
    }

    @Test
    fun marksKnownMissingTargetsWithoutChangingTheDisplayedName() {
        val source = "[[Existe]] e [[Ausente]]"
        val rendered = preprocessWikilinksForReading(source, existingNames = setOf("Existe"))

        assertEquals(
            "[Existe](gitnote://note?name=Existe) e " +
                "[Ausente](gitnote://missing-note?name=Ausente)",
            rendered,
        )
        assertEquals("Ausente", parseWikilinkUri("gitnote://missing-note?name=Ausente")?.name)
        assertTrue(parseWikilinkUri("gitnote://missing-note?name=Ausente")?.isMissing == true)
    }

    @Test
    fun aliasUsesTheTargetForResolutionAndMissingState() {
        val source = "[[Nome da Nota|Inspeção 🩺 #2]]"
        val rendered = preprocessWikilinksForReading(
            source,
            existingNames = setOf("Nome da Nota"),
        )

        assertEquals(
            "[Inspeção 🩺 #2](gitnote://note?name=Nome%20da%20Nota)",
            rendered,
        )
        assertEquals(setOf("Nome da Nota"), wikilinkNames(source))
        assertFalse(rendered.contains("missing-note"))
    }

    @Test
    fun internalSectionHasItsOwnNonNavigatingUriType() {
        val plain = preprocessWikilinksForReading("[[#Inspeção]]")
        val aliased = preprocessWikilinksForReading("[[#Inspeção|Inspeção]]")
        val plainParsed = parseWikilinkUri(plain.substringAfter("](").removeSuffix(")"))
        val parsed = parseWikilinkUri(aliased.substringAfter("](").removeSuffix(")"))

        assertEquals("inspeção", normalizeSectionHeading(checkNotNull(plainParsed).name))
        assertEquals("inspeção", normalizeSectionHeading(checkNotNull(parsed).name))
        assertEquals("Inspeção", parsed?.name)
        assertEquals("Inspeção", parsed?.section)
        assertTrue(parsed?.isSection == true)
        assertFalse(parsed?.isMissing ?: true)
        assertEquals(emptySet(), wikilinkNames("[[#Inspeção|ver achados]]"))
    }

    @Test
    fun externalSectionSurvivesTheUriRoundTrip() {
        val rendered = preprocessWikilinksForReading("[[Nota#Revisão sistemática]]")
        val parsed = parseWikilinkUri(rendered.substringAfter("](").removeSuffix(")"))

        assertEquals("Nota", parsed?.name)
        assertEquals("Revisão sistemática", parsed?.section)
    }

    @Test
    fun sectionResolutionNormalizesWhitespaceUsesFirstDuplicateAndHasSafeFallback() {
        val headings = listOf(
            HeadingAnchor("Inspeção", y = 100, sourceOffset = 10),
            HeadingAnchor("Revisão   sistemática", y = 200, sourceOffset = 20),
            HeadingAnchor("Inspeção", y = 300, sourceOffset = 30),
            HeadingAnchor("Facies típicas: hipertireoidismo", y = 400, sourceOffset = 40),
        )

        assertEquals(200, resolveSectionHeading(" revisão\tsistemática ", headings)?.y)
        assertEquals(100, resolveSectionHeading("inspeção", headings)?.y)
        assertEquals(400, resolveSectionHeading("Facies típicas:hipertireoidismo", headings)?.y)
        assertNull(resolveSectionHeading("Inexistente", headings))
    }

    @Test
    fun sectionFallbackRejectsAmbiguousHeadings() {
        val headings = listOf(
            HeadingAnchor("AB C", y = 100, sourceOffset = 10),
            HeadingAnchor("A BC", y = 200, sourceOffset = 20),
        )

        assertNull(resolveSectionHeading("ABC", headings))
    }

    @Test
    fun preprocessesHighlightsWithEscapedLinkTextAndAlongsideWikilinks() {
        assertEquals("[x](gitnote://highlight)", preprocessWikilinksForReading("==x=="))
        assertEquals(
            "[\\[x\\]](gitnote://highlight)",
            preprocessWikilinksForReading("==[x]=="),
        )
        assertEquals(
            "[Nota](gitnote://note?name=Nota) e [trecho](gitnote://highlight)",
            preprocessWikilinksForReading("[[Nota]] e ===trecho==="),
        )
    }

    @Test
    fun uriRoundTripPreservesSpacesUnicodeAndReservedCharacters() {
        val name = "R&D + Kotlin á"
        val rendered = preprocessWikilinksForReading("[[$name]]")
        val uri = rendered.substringAfter("](").removeSuffix(")")

        assertEquals(name, parseWikilinkUri(uri)?.name)
        assertFalse(parseWikilinkUri(uri)?.isMissing ?: true)
        assertFalse(parseWikilinkUri(uri)?.isSection ?: true)
        assertNull(parseWikilinkUri("https://example.com"))
        assertNull(parseWikilinkUri("gitnote://other?name=Nota"))
    }

    @Test
    fun resolutionPrefersTheCurrentFolder() {
        val root = "Nota.md"
        val sibling = "pasta/Nota.md"
        val other = "outra/Nota.md"

        val resolved = resolveWikilinkTargets(
            names = setOf("nota"),
            currentParentPath = "pasta",
            candidatePaths = listOf(root, other, sibling),
        )

        assertEquals(sibling, resolved["nota"])
    }

    @Test
    fun resolutionFallbackIsCaseInsensitiveAndDeterministic() {
        val upper = "Z/NOTA.md"
        val alphabeticallyFirst = "a/Nota.md"
        val unicode = "acentos/ÁRVORE.md"

        val forward = resolveWikilinkTargets(
            names = setOf("nota", "árvore", "inexistente"),
            currentParentPath = "sem-correspondencia",
            candidatePaths = listOf(upper, alphabeticallyFirst, unicode),
        )
        val reversed = resolveWikilinkTargets(
            names = setOf("nota", "árvore", "inexistente"),
            currentParentPath = "sem-correspondencia",
            candidatePaths = listOf(unicode, alphabeticallyFirst, upper),
        )

        assertEquals(alphabeticallyFirst, forward["nota"])
        assertEquals(unicode, forward["árvore"])
        assertEquals(forward, reversed)
        assertNull(forward["inexistente"])
    }
}
