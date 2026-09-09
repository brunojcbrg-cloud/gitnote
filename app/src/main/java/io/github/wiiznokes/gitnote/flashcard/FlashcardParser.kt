package io.github.wiiznokes.gitnote.flashcard

import io.github.wiiznokes.gitnote.ui.component.markdown.MarkdownScanner
import io.github.wiiznokes.gitnote.ui.component.markdown.MdKind
import io.github.wiiznokes.gitnote.ui.component.markdown.MdSpan
import java.time.LocalDate

object FlashcardParser {
    private val scheduleRegex = Regex(
        """^<!--SR:!(\d{4}-\d{2}-\d{2}),(\d+),(\d+)-->$""",
    )

    fun parse(content: String, noteTitle: String = "Note"): List<ParsedFlashcard> {
        if (content.isEmpty()) return emptyList()

        val lines = splitLines(content)
        val spans = MarkdownScanner.scan(content)
        val protectedSpans = spans.filter {
            it.kind == MdKind.CODE_FENCE || it.kind == MdKind.INLINE_CODE
        }
        val decks = findDecks(content, protectedSpans)
        if (decks.isEmpty()) return emptyList()

        val codeLines = BooleanArray(lines.size) { lineIndex ->
            protectedSpans.any { span ->
                span.kind == MdKind.CODE_FENCE && span.range.overlaps(lines[lineIndex].contentRange)
            }
        }
        val headingByLine = headingSpansByLine(lines, spans)
        val contexts = contextsByLine(lines, headingByLine, noteTitle)
        val consumedByMultiline = BooleanArray(lines.size)
        val cards = mutableListOf<ParsedFlashcard>()

        lines.indices.forEach { separatorLine ->
            val line = lines[separatorLine]
            if (codeLines[separatorLine] || line.text(content).trim() != "?") return@forEach

            val questionStart = multilineQuestionStart(
                separatorLine = separatorLine,
                lines = lines,
                content = content,
                codeLines = codeLines,
                headingByLine = headingByLine,
                protectedSpans = protectedSpans,
            ) ?: return@forEach
            val answerEnd = multilineAnswerEnd(separatorLine, lines, content, codeLines)
                ?: return@forEach

            val question = content.substring(
                lines[questionStart].start,
                lines[separatorLine - 1].contentEnd,
            ).trimLineEnds()
            val answer = content.substring(
                lines[separatorLine + 1].start,
                lines[answerEnd].contentEnd,
            ).trimLineEnds()
            if (question.isBlank() || answer.isBlank()) return@forEach

            val scheduleLine = (answerEnd + 1).takeIf { it < lines.size }
                ?.takeIf { parseSchedule(lines[it].text(content)) != null }
            val schedule = scheduleLine?.let { parseSchedule(lines[it].text(content)) }
            val cardLine = lines[answerEnd]
            cards += ParsedFlashcard(
                question = question,
                answer = answer,
                sourceRange = lines[questionStart].start until cardLine.contentEnd,
                scheduleLineRange = scheduleLine?.let { lines[it].contentRange },
                insertionOffset = cardLine.end,
                insertionLineEnding = insertionLineEnding(lines, answerEnd),
                schedule = schedule,
                decks = decks,
                context = contexts[questionStart],
            )
            for (index in questionStart..answerEnd) consumedByMultiline[index] = true
        }

        lines.indices.forEach { lineIndex ->
            if (codeLines[lineIndex] || consumedByMultiline[lineIndex]) return@forEach
            val line = lines[lineIndex]
            val lineText = line.text(content)
            if (lineText.contains(":::")) return@forEach

            val separator = firstSingleLineSeparator(
                content = content,
                line = line,
                protectedSpans = protectedSpans,
            ) ?: return@forEach
            val question = content.substring(line.start, separator).trim()
            val answer = content.substring(separator + 2, line.contentEnd).trim()
            if (question.isEmpty() || answer.isEmpty()) return@forEach

            val scheduleLine = (lineIndex + 1).takeIf { it < lines.size }
                ?.takeIf { parseSchedule(lines[it].text(content)) != null }
            val schedule = scheduleLine?.let { parseSchedule(lines[it].text(content)) }
            cards += ParsedFlashcard(
                question = question,
                answer = answer,
                sourceRange = line.contentRange,
                scheduleLineRange = scheduleLine?.let { lines[it].contentRange },
                insertionOffset = line.end,
                insertionLineEnding = insertionLineEnding(lines, lineIndex),
                schedule = schedule,
                decks = decks,
                context = contexts[lineIndex],
            )
        }

        return cards.sortedBy { it.sourceRange.first }
    }

    private fun findDecks(content: String, protectedSpans: List<MdSpan>): List<String> {
        val decks = linkedSetOf<String>()
        var offset = 0
        while (offset < content.length) {
            val found = content.indexOf("#flashcards", offset)
            if (found < 0) break
            val beforeIsTag = found > 0 && content[found - 1].isTagCharacter()
            val afterRoot = found + FLASHCARD_TAG.length
            val validEnd = afterRoot == content.length ||
                content[afterRoot] == '/' || !content[afterRoot].isTagCharacter()
            val protected = protectedSpans.any { found in it.fullRange() }
            if (!beforeIsTag && validEnd && !protected) {
                var end = afterRoot
                while (end < content.length &&
                    (content[end].isTagCharacter() || content[end] == '/')
                ) {
                    end++
                }
                val raw = content.substring(found, end)
                val deck = raw.removePrefix(FLASHCARD_TAG).removePrefix("/")
                decks += deck
            }
            offset = afterRoot
        }
        return decks.toList()
    }

    private fun multilineQuestionStart(
        separatorLine: Int,
        lines: List<SourceLine>,
        content: String,
        codeLines: BooleanArray,
        headingByLine: Map<Int, MdSpan>,
        protectedSpans: List<MdSpan>,
    ): Int? {
        if (separatorLine == 0) return null
        var start = separatorLine - 1
        while (start >= 0) {
            val text = lines[start].text(content)
            if (text.isBlank() || codeLines[start] || headingByLine.containsKey(start) ||
                parseSchedule(text) != null || text.trim() == "??" || text.contains(":::") ||
                text.contains(FLASHCARD_TAG) ||
                firstSingleLineSeparator(content, lines[start], protectedSpans) != null
            ) {
                break
            }
            start--
        }
        start++
        return start.takeIf { it < separatorLine }
    }

    private fun multilineAnswerEnd(
        separatorLine: Int,
        lines: List<SourceLine>,
        content: String,
        codeLines: BooleanArray,
    ): Int? {
        var end = separatorLine + 1
        if (end >= lines.size) return null
        while (end < lines.size) {
            val text = lines[end].text(content)
            if (text.isBlank() || codeLines[end] || parseSchedule(text) != null) break
            end++
        }
        return (end - 1).takeIf { it >= separatorLine + 1 }
    }

    private fun firstSingleLineSeparator(
        content: String,
        line: SourceLine,
        protectedSpans: List<MdSpan>,
    ): Int? {
        var offset = line.start
        while (offset + 1 < line.contentEnd) {
            if (content[offset] == ':' && content[offset + 1] == ':' &&
                protectedSpans.none { span ->
                    span.kind == MdKind.INLINE_CODE && offset in span.fullRange()
                }
            ) {
                return offset
            }
            offset++
        }
        return null
    }

    private fun headingSpansByLine(
        lines: List<SourceLine>,
        spans: List<MdSpan>,
    ): Map<Int, MdSpan> = spans.asSequence()
        .filter { it.kind.headingLevel() != null }
        .mapNotNull { span ->
            lines.indexOfFirst { span.range.overlaps(it.contentRange) }
                .takeIf { it >= 0 }
                ?.let { it to span }
        }
        .toMap()

    private fun contextsByLine(
        lines: List<SourceLine>,
        headingByLine: Map<Int, MdSpan>,
        noteTitle: String,
    ): List<List<String>> {
        val stack = mutableListOf<Pair<Int, String>>()
        return lines.indices.map { lineIndex ->
            headingByLine[lineIndex]?.let { heading ->
                val level = checkNotNull(heading.kind.headingLevel())
                while (stack.isNotEmpty() && stack.last().first >= level) stack.removeLast()
                stack += level to linesTextSlice(heading, lines, lineIndex)
            }
            buildList {
                add(noteTitle)
                addAll(stack.map { it.second })
            }
        }
    }

    private fun linesTextSlice(span: MdSpan, lines: List<SourceLine>, lineIndex: Int): String {
        val line = lines[lineIndex]
        val start = span.range.first.coerceIn(line.start, line.contentEnd)
        val end = (span.range.last + 1).coerceIn(start, line.contentEnd)
        return line.source.substring(start, end).trim()
    }

    private fun parseSchedule(rawLine: String): FlashcardSchedule? {
        val match = scheduleRegex.matchEntire(rawLine.trim()) ?: return null
        return runCatching {
            FlashcardSchedule(
                dueDate = LocalDate.parse(match.groupValues[1]),
                interval = match.groupValues[2].toInt(),
                ease = match.groupValues[3].toInt(),
            )
        }.getOrNull()
    }

    private fun insertionLineEnding(lines: List<SourceLine>, lineIndex: Int): String {
        val current = lines[lineIndex].lineEnding
        if (current.isNotEmpty()) return current
        return lines.asSequence().map { it.lineEnding }.firstOrNull { it.isNotEmpty() } ?: "\n"
    }

    private fun splitLines(content: String): List<SourceLine> {
        val lines = mutableListOf<SourceLine>()
        var start = 0
        while (start < content.length) {
            var contentEnd = start
            while (contentEnd < content.length && content[contentEnd] != '\r' &&
                content[contentEnd] != '\n'
            ) {
                contentEnd++
            }
            val end = when {
                contentEnd >= content.length -> contentEnd
                content[contentEnd] == '\r' && contentEnd + 1 < content.length &&
                    content[contentEnd + 1] == '\n' -> contentEnd + 2
                else -> contentEnd + 1
            }
            lines += SourceLine(content, start, contentEnd, end)
            start = end
        }
        return lines
    }

    private data class SourceLine(
        val source: String,
        val start: Int,
        val contentEnd: Int,
        val end: Int,
    ) {
        val contentRange: IntRange
            get() = start until contentEnd
        val lineEnding: String
            get() = source.substring(contentEnd, end)

        fun text(content: String): String = content.substring(start, contentEnd)
    }

    private fun MdSpan.fullRange(): IntRange {
        val starts = markers.map { it.first } + range.first
        val ends = markers.map { it.last } + range.last
        return starts.min()..ends.max()
    }

    private fun MdKind.headingLevel(): Int? = when (this) {
        MdKind.H1 -> 1
        MdKind.H2 -> 2
        MdKind.H3 -> 3
        MdKind.H4 -> 4
        MdKind.H5 -> 5
        MdKind.H6 -> 6
        else -> null
    }

    private fun IntRange.overlaps(other: IntRange): Boolean =
        !isEmpty() && !other.isEmpty() && first <= other.last && other.first <= last

    private fun Char.isTagCharacter(): Boolean = isLetterOrDigit() || this == '_' || this == '-'

    private fun String.trimLineEnds(): String = trim { it == '\r' || it == '\n' }

    private const val FLASHCARD_TAG = "#flashcards"
}
