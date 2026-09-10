package io.github.wiiznokes.gitnote.ui.component.markdown

/**
 * A deliberately small, linear Markdown scanner for the editable preview.
 *
 * It is not a full CommonMark parser. It recognizes only the constructs that the live preview
 * styles, never crosses a line for inline markup, and gives fenced/inline code precedence over
 * every other construct.
 */
object MarkdownScanner {
    fun scan(text: String): List<MdSpan> {
        if (text.isEmpty()) return emptyList()

        val spans = mutableListOf<MdSpan>()
        var line = 0
        var lineStart = 0
        var inCodeFence = false

        while (lineStart <= text.length) {
            var lineEnd = lineStart
            while (lineEnd < text.length && text[lineEnd] != '\n') lineEnd++

            val firstContent = firstNonWhitespace(text, lineStart, lineEnd)
            val isFenceLine = startsWith(text, firstContent, lineEnd, "```")

            if (inCodeFence || isFenceLine) {
                if (isFenceLine) {
                    val fenceRange = firstContent until lineEnd
                    spans += MdSpan(
                        kind = MdKind.CODE_FENCE,
                        range = fenceRange,
                        markers = if (fenceRange.isEmpty()) emptyList() else listOf(fenceRange),
                        line = line,
                    )
                    inCodeFence = !inCodeFence
                } else if (lineStart < lineEnd) {
                    spans += MdSpan(
                        kind = MdKind.CODE_FENCE,
                        range = lineStart until lineEnd,
                        markers = emptyList(),
                        line = line,
                    )
                }
            } else {
                val inlineStart = scanBlockPrefix(
                    text = text,
                    lineStart = lineStart,
                    lineEnd = lineEnd,
                    firstContent = firstContent,
                    line = line,
                    spans = spans,
                )
                scanInline(text, inlineStart, lineEnd, line, spans)
            }

            if (lineEnd == text.length) break
            lineStart = lineEnd + 1
            line++
        }

        return spans
    }

    private fun scanBlockPrefix(
        text: String,
        lineStart: Int,
        lineEnd: Int,
        firstContent: Int,
        line: Int,
        spans: MutableList<MdSpan>,
    ): Int {
        if (firstContent >= lineEnd) return lineEnd

        if (text[firstContent] == '#') {
            var afterHashes = firstContent
            while (afterHashes < lineEnd && text[afterHashes] == '#' &&
                afterHashes - firstContent < 6
            ) {
                afterHashes++
            }
            val level = afterHashes - firstContent
            if (level in 1..6 && afterHashes < lineEnd && text[afterHashes] == ' ') {
                val contentStart = afterHashes + 1
                spans += MdSpan(
                    kind = headingKind(level),
                    range = contentStart until lineEnd,
                    markers = listOf(firstContent until contentStart),
                    line = line,
                )
                return contentStart
            }
        }

        if (text[firstContent] == '>' && firstContent + 1 < lineEnd &&
            text[firstContent + 1] == ' '
        ) {
            val contentStart = firstContent + 2
            spans += MdSpan(
                kind = MdKind.QUOTE,
                range = contentStart until lineEnd,
                markers = listOf(firstContent until contentStart),
                line = line,
            )
            return contentStart
        }

        if ((text[firstContent] == '-' || text[firstContent] == '*') &&
            firstContent + 1 < lineEnd && text[firstContent + 1] == ' '
        ) {
            val afterBullet = firstContent + 2
            val taskEnd = taskMarkerEnd(text, afterBullet, lineEnd)
            if (taskEnd != null) {
                spans += MdSpan(
                    kind = if (text[afterBullet + 1].equals('x', ignoreCase = true)) {
                        MdKind.TASK_DONE
                    } else {
                        MdKind.TASK_TODO
                    },
                    range = firstContent until taskEnd,
                    markers = emptyList(),
                    line = line,
                )
                return taskEnd
            }

            spans += MdSpan(
                kind = MdKind.BULLET,
                range = firstContent until afterBullet,
                markers = emptyList(),
                line = line,
            )
            return afterBullet
        }

        if (text[firstContent].isDigit()) {
            var afterDigits = firstContent + 1
            while (afterDigits < lineEnd && text[afterDigits].isDigit()) afterDigits++
            if (afterDigits + 1 < lineEnd && text[afterDigits] == '.' &&
                text[afterDigits + 1] == ' '
            ) {
                val afterOrdered = afterDigits + 2
                val taskEnd = taskMarkerEnd(text, afterOrdered, lineEnd)
                if (taskEnd != null) {
                    spans += MdSpan(
                        kind = if (text[afterOrdered + 1].equals('x', ignoreCase = true)) {
                            MdKind.TASK_DONE
                        } else {
                            MdKind.TASK_TODO
                        },
                        range = firstContent until taskEnd,
                        markers = emptyList(),
                        line = line,
                    )
                    return taskEnd
                }

                spans += MdSpan(
                    kind = MdKind.ORDERED,
                    range = firstContent until afterOrdered,
                    markers = emptyList(),
                    line = line,
                )
                return afterOrdered
            }
        }

        return lineStart
    }

    private fun scanInline(
        text: String,
        start: Int,
        end: Int,
        line: Int,
        spans: MutableList<MdSpan>,
    ) {
        var open: InlineOpen? = null
        var index = start

        while (index < end) {
            when (val current = open) {
                null -> {
                    val delimiter = openingDelimiter(text, index, end)
                    if (delimiter != null) {
                        open = DelimitedOpen(delimiter.first, index, delimiter.second)
                        index += delimiter.second
                    } else if (startsWith(text, index, end, "[[") &&
                        (index == start || (text[index - 1] != '!' && text[index - 1] != '\\'))
                    ) {
                        open = WikilinkOpen(markerStart = index)
                        index += 2
                    } else if (text[index] == '[') {
                        open = LinkOpen(markerStart = index)
                        index++
                    } else {
                        index++
                    }
                }

                is DelimitedOpen -> {
                    val closingLength = closingDelimiterLength(
                        text = text,
                        index = index,
                        end = end,
                        markerLength = current.markerLength,
                        kind = current.kind,
                    )
                    if (closingLength != null) {
                        val contentStart = current.markerStart + current.markerLength
                        if (contentStart < index) {
                            spans += MdSpan(
                                kind = current.kind,
                                range = contentStart until index,
                                markers = listOf(
                                    current.markerStart until contentStart,
                                    index until index + closingLength,
                                ),
                                line = line,
                            )
                        }
                        index += closingLength
                        open = null
                    } else {
                        index++
                    }
                }

                is LinkOpen -> {
                    if (current.textEnd == null) {
                        when {
                            text[index] == '[' -> open = LinkOpen(markerStart = index)
                            text[index] == ']' && index + 1 < end && text[index + 1] == '(' -> {
                                current.textEnd = index
                                current.urlStart = index + 2
                                index += 2
                                continue
                            }
                        }
                        index++
                    } else if (text[index] == ')') {
                        val textStart = current.markerStart + 1
                        val textEnd = current.textEnd ?: index
                        val urlStart = current.urlStart ?: index
                        if (textStart < textEnd) {
                            spans += MdSpan(
                                kind = MdKind.LINK_TEXT,
                                range = textStart until textEnd,
                                markers = listOf(
                                    current.markerStart..current.markerStart,
                                    textEnd..index,
                                ),
                                line = line,
                            )
                            if (urlStart < index) {
                                spans += MdSpan(
                                    kind = MdKind.LINK_URL,
                                    range = urlStart until index,
                                    markers = emptyList(),
                                    line = line,
                                )
                            }
                        }
                        open = null
                        index++
                    } else {
                        index++
                    }
                }

                is WikilinkOpen -> {
                    if (startsWith(text, index, end, "]]")) {
                        val contentStart = current.markerStart + 2
                        val parsed = parseWikilink(text, contentStart, index)
                        if (parsed != null) {
                            spans += MdSpan(
                                kind = MdKind.WIKILINK,
                                range = parsed.displayStart until parsed.displayEnd,
                                markers = listOf(
                                    current.markerStart until parsed.displayStart,
                                    parsed.displayEnd until index + 2,
                                ),
                                line = line,
                                wikilink = parsed.parts,
                            )
                        }
                        index += 2
                        open = null
                    } else {
                        index++
                    }
                }
            }
        }
    }

    private fun parseWikilink(text: String, contentStart: Int, contentEnd: Int): ParsedWikilink? {
        val aliasSeparator = indexOf(text, '|', contentStart, contentEnd)
        val targetAndSectionEnd = aliasSeparator ?: contentEnd
        val sectionSeparator = indexOf(text, '#', contentStart, targetAndSectionEnd)
        val targetEnd = sectionSeparator ?: targetAndSectionEnd

        val rawTarget = text.substring(contentStart, targetEnd)
        val target = if (rawTarget.isBlank()) "" else rawTarget
        val section = sectionSeparator?.let { text.substring(it + 1, targetAndSectionEnd) }
        val alias = aliasSeparator?.let { text.substring(it + 1, contentEnd) }

        if (target.isEmpty() && section.isNullOrBlank()) return null

        val displayStart: Int
        val displayEnd: Int
        when {
            aliasSeparator != null -> {
                displayStart = aliasSeparator + 1
                displayEnd = contentEnd
            }
            target.isNotEmpty() -> {
                displayStart = contentStart
                displayEnd = targetEnd
            }
            else -> {
                displayStart = (sectionSeparator ?: return null) + 1
                displayEnd = targetAndSectionEnd
            }
        }
        return ParsedWikilink(
            displayStart = displayStart,
            displayEnd = displayEnd,
            parts = WikilinkParts(
                target = target,
                section = section,
                alias = alias,
            ),
        )
    }

    private fun indexOf(
        text: String,
        character: Char,
        start: Int,
        end: Int,
    ): Int? {
        for (index in start until end) {
            if (text[index] == character) return index
        }
        return null
    }

    private fun openingDelimiter(text: String, index: Int, end: Int): Pair<MdKind, Int>? =
        when {
            startsWith(text, index, end, "***") -> MdKind.BOLD_ITALIC to 3
            startsWith(text, index, end, "**") -> MdKind.BOLD to 2
            startsWith(text, index, end, "~~") -> MdKind.STRIKE to 2
            startsWith(text, index, end, "==") ->
                MdKind.HIGHLIGHT to consecutiveEquals(text, index, end)
            text[index] == '`' -> MdKind.INLINE_CODE to 1
            text[index] == '_' -> MdKind.ITALIC to 1
            else -> null
        }

    private fun closingDelimiterLength(
        text: String,
        index: Int,
        end: Int,
        markerLength: Int,
        kind: MdKind,
    ): Int? {
        if (kind == MdKind.HIGHLIGHT) {
            val length = consecutiveEquals(text, index, end)
            return length.takeIf { it >= markerLength }
        }
        val marker = when (kind) {
            MdKind.BOLD_ITALIC -> "***"
            MdKind.BOLD -> "**"
            MdKind.STRIKE -> "~~"
            MdKind.INLINE_CODE -> "`"
            MdKind.ITALIC -> "_"
            else -> return null
        }
        return markerLength.takeIf {
            marker.length == markerLength && startsWith(text, index, end, marker)
        }
    }

    private fun consecutiveEquals(text: String, index: Int, end: Int): Int {
        var afterEquals = index
        while (afterEquals < end && text[afterEquals] == '=') afterEquals++
        return afterEquals - index
    }

    private fun taskMarkerEnd(text: String, start: Int, end: Int): Int? {
        if (start + 3 >= end || text[start] != '[' || text[start + 2] != ']' ||
            text[start + 3] != ' '
        ) {
            return null
        }
        return if (text[start + 1] == ' ' || text[start + 1].equals('x', ignoreCase = true)) {
            start + 4
        } else {
            null
        }
    }

    private fun firstNonWhitespace(text: String, start: Int, end: Int): Int {
        var index = start
        while (index < end && (text[index] == ' ' || text[index] == '\t')) index++
        return index
    }

    private fun startsWith(text: String, start: Int, end: Int, marker: String): Boolean {
        if (start < 0 || start + marker.length > end) return false
        for (offset in marker.indices) {
            if (text[start + offset] != marker[offset]) return false
        }
        return true
    }

    private fun headingKind(level: Int) = when (level) {
        1 -> MdKind.H1
        2 -> MdKind.H2
        3 -> MdKind.H3
        4 -> MdKind.H4
        5 -> MdKind.H5
        else -> MdKind.H6
    }

    private sealed interface InlineOpen

    private data class DelimitedOpen(
        val kind: MdKind,
        val markerStart: Int,
        val markerLength: Int,
    ) : InlineOpen

    private data class LinkOpen(
        val markerStart: Int,
        var textEnd: Int? = null,
        var urlStart: Int? = null,
    ) : InlineOpen

    private data class WikilinkOpen(
        val markerStart: Int,
    ) : InlineOpen

    private data class ParsedWikilink(
        val displayStart: Int,
        val displayEnd: Int,
        val parts: WikilinkParts,
    )
}
