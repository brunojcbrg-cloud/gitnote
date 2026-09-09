package io.github.wiiznokes.gitnote.flashcard

import io.github.wiiznokes.gitnote.ui.component.markdown.MarkdownScanner
import io.github.wiiznokes.gitnote.ui.component.markdown.MdKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlashcardParserTest {
    @Test
    fun `01 parses a single-line card`() {
        val card = parse("#flashcards\nPergunta::Resposta").single()
        assertEquals("Pergunta", card.question)
        assertEquals("Resposta", card.answer)
    }

    @Test
    fun `02 ignores a note without the flashcards tag`() {
        assertTrue(parse("Pergunta::Resposta").isEmpty())
    }

    @Test
    fun `03 preserves a hierarchical deck tag exactly`() {
        val card = parse("#flashcards/a/pré-Clínico\nPergunta::Resposta").single()
        assertEquals("a/pré-Clínico", card.deck)
    }

    @Test
    fun `04 parses two cards with exact source ranges`() {
        val source = "#flashcards\nPrimeira::Um\ntexto\nSegunda::Dois"
        val cards = parse(source)
        assertEquals(2, cards.size)
        assertEquals("Primeira::Um", source.substring(cards[0].sourceRange))
        assertEquals("Segunda::Dois", source.substring(cards[1].sourceRange))
    }

    @Test
    fun `05 decodes the schedule on the next line`() {
        val card = parse(
            "#flashcards\nPergunta::Resposta\n<!--SR:!2026-09-10,2,210-->",
        ).single()
        assertEquals("2026-09-10", card.schedule?.dueDate.toString())
        assertEquals(2, card.schedule?.interval)
        assertEquals(210, card.schedule?.ease)
    }

    @Test
    fun `06 leaves a new card unscheduled`() {
        assertNull(parse("#flashcards\nPergunta::Resposta").single().schedule)
    }

    @Test
    fun `07 parses a multiline card`() {
        val card = parse("#flashcards\nPergunta em\nduas linhas\n?\nResposta em\nduas linhas\n").single()
        assertEquals("Pergunta em\nduas linhas", card.question)
        assertEquals("Resposta em\nduas linhas", card.answer)
    }

    @Test
    fun `08 stops a multiline answer at the blank line`() {
        val card = parse("#flashcards\nPergunta\n?\nResposta\n\nTexto seguinte").single()
        assertEquals("Resposta", card.answer)
        assertFalse(card.answer.contains("Texto seguinte"))
    }

    @Test
    fun `09 ignores reversible triple-colon cards`() {
        assertTrue(parse("#flashcards\nFrente:::Verso").isEmpty())
    }

    @Test
    fun `10 highlight markup does not become a card`() {
        assertTrue(parse("#flashcards\nTexto com ==realce==").isEmpty())
    }

    @Test
    fun `11 ignores separators inside fenced code`() {
        assertTrue(parse("#flashcards\n```\nPergunta::Resposta\n```").isEmpty())
    }

    @Test
    fun `12 ignores separators inside inline code`() {
        assertTrue(parse("#flashcards\n`Pergunta::Resposta`").isEmpty())
    }

    @Test
    fun `13 uses the first separator and preserves later separators`() {
        val card = parse("#flashcards\nPergunta::Resposta::com detalhe").single()
        assertEquals("Pergunta", card.question)
        assertEquals("Resposta::com detalhe", card.answer)
    }

    @Test
    fun `14 builds the nested heading trail`() {
        val card = parse(
            "#flashcards\n## Exame\n### Inspeção\nPergunta::Resposta",
            noteTitle = "Semiologia",
        ).single()
        assertEquals(listOf("Semiologia", "Exame", "Inspeção"), card.context)
    }

    @Test
    fun `15 preserves accents emoji and UTF-16 ranges`() {
        val source = "#flashcards\nQual é o sinal 🩺?::É íntegro ✅"
        val card = parse(source).single()
        assertEquals("Qual é o sinal 🩺?", card.question)
        assertEquals("É íntegro ✅", card.answer)
        assertEquals("Qual é o sinal 🩺?::É íntegro ✅", source.substring(card.sourceRange))
    }

    @Test
    fun `16 preserves wikilinks in the answer`() {
        val card = parse("#flashcards\nPergunta::Veja [[Semiologia#Inspeção]]").single()
        assertEquals("Veja [[Semiologia#Inspeção]]", card.answer)
    }

    @Test
    fun `36 parsing leaves editor markdown and wikilinks untouched`() {
        val source = "#flashcards\n## Título\n**Pergunta**::[[Resposta|apelido]]"
        parse(source)
        assertEquals("#flashcards\n## Título\n**Pergunta**::[[Resposta|apelido]]", source)
        assertTrue(MarkdownScanner.scan(source).any { it.kind == MdKind.WIKILINK })
    }

    @Test
    fun `37 existing scanner code precedence remains available`() {
        val spans = MarkdownScanner.scan("`[[inline]]`\n[[visible]]")
        assertEquals(1, spans.count { it.kind == MdKind.INLINE_CODE })
        assertEquals(1, spans.count { it.kind == MdKind.WIKILINK })
    }

    @Test
    fun `38 a tagged note without cards remains empty and unchanged`() {
        val source = "#flashcards\nApenas uma nota comum."
        assertTrue(parse(source).isEmpty())
        assertEquals("#flashcards\nApenas uma nota comum.", source)
    }

    private fun parse(source: String, noteTitle: String = "Nota") =
        FlashcardParser.parse(source, noteTitle)
}
