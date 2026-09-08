package io.github.wiiznokes.gitnote.ui.component.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WikilinkSupportTest {
    @Test
    fun preprocessesOnlySimpleWikilinksOutsideCode() {
        val source = "[[Nota á]] `[[codigo]]` ![[embed]] [[Nome|alias]] [[Nome#secao]]"
        val rendered = preprocessWikilinksForReading(source)

        assertEquals(
            "[Nota á](gitnote://note?name=Nota%20%C3%A1) `[[codigo]]` " +
                "![[embed]] [[Nome|alias]] [[Nome#secao]]",
            rendered,
        )
        assertEquals(setOf("Nota á"), wikilinkNames(source))
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
    fun uriRoundTripPreservesSpacesUnicodeAndReservedCharacters() {
        val name = "R&D + Kotlin á"
        val rendered = preprocessWikilinksForReading("[[$name]]")
        val uri = rendered.substringAfter("](").removeSuffix(")")

        assertEquals(name, parseWikilinkUri(uri)?.name)
        assertFalse(parseWikilinkUri(uri)?.isMissing ?: true)
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
