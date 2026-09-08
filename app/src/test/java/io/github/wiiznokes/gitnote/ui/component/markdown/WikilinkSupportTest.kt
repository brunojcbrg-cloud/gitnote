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
                "[Nome](gitnote://note?name=Nome) " +
                "[apelido](gitnote://note?name=Nome) " +
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
        val rendered = preprocessWikilinksForReading("[[#Inspeção|ver achados]]")
        val uri = rendered.substringAfter("](").removeSuffix(")")
        val parsed = parseWikilinkUri(uri)

        assertEquals("[ver achados](gitnote://section?name=Inspe%C3%A7%C3%A3o)", rendered)
        assertEquals("Inspeção", parsed?.name)
        assertTrue(parsed?.isSection == true)
        assertFalse(parsed?.isMissing ?: true)
        assertEquals(emptySet(), wikilinkNames("[[#Inspeção|ver achados]]"))
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
