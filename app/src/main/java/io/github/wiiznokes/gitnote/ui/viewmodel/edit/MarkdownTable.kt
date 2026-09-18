package io.github.wiiznokes.gitnote.ui.viewmodel.edit

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

data class MdTableCell(val text: String)

enum class MdAlign {
    NONE,
    LEFT,
    CENTER,
    RIGHT,
}

data class MdTable(
    val header: List<String>,
    val aligns: List<MdAlign>,
    val rows: List<List<String>>,
    val style: MdTableStyle,
)

data class MdTableStyle(
    val outerPipes: Boolean,
    val padded: Boolean,
    val lineEnding: String,
    /** Raw separator cells keep parse/render byte-identical for existing tables. */
    val separatorCells: List<String> = emptyList(),
)

data class MdTableResizeResult(
    val table: MdTable,
    val lostNonEmptyCells: Int,
)

data class MdTableRegion(
    val headerLine: Int,
    val separatorLine: Int,
    val lastLine: Int,
)

data class MdTableDocumentResizeResult(
    val value: TextFieldValue,
    val lostNonEmptyCells: Int,
)

fun cellsOf(line: String): List<String> {
    val pipes = structuralPipeOffsets(line)
    if (pipes.isEmpty()) return listOf(line)

    val firstIsOuter = line.substring(0, pipes.first()).isBlank()
    val lastIsOuter = line.substring(pipes.last() + 1).isBlank()
    val contentStart = if (firstIsOuter) pipes.first() + 1 else 0
    val contentEnd = if (lastIsOuter) pipes.last() else line.length
    if (contentStart > contentEnd) return emptyList()

    val innerPipes = pipes.filter { it in contentStart until contentEnd }
    val result = mutableListOf<String>()
    var cellStart = contentStart
    for (pipe in innerPipes) {
        result += line.substring(cellStart, pipe)
        cellStart = pipe + 1
    }
    result += line.substring(cellStart, contentEnd)
    return result
}

fun parseTable(text: String, headerLine: Int): MdTable? {
    val lines = tableLines(text)
    if (headerLine !in lines.indices || headerLine + 1 !in lines.indices) return null

    val headerText = lines[headerLine].content
    val separatorText = lines[headerLine + 1].content
    if (!hasStructuralPipe(headerText) || !hasStructuralPipe(separatorText)) return null

    val header = cellsOf(headerText)
    val separatorCells = cellsOf(separatorText)
    if (header.isEmpty() || separatorCells.size != header.size) return null

    val aligns = separatorCells.map { separatorCell ->
        alignmentOf(separatorCell) ?: return null
    }

    val rows = mutableListOf<List<String>>()
    var rowIndex = headerLine + 2
    while (rowIndex < lines.size) {
        val row = lines[rowIndex].content
        if (row.isBlank() || !hasStructuralPipe(row)) break
        rows += cellsOf(row)
        rowIndex++
    }

    val outerPipes = hasOuterPipes(headerText)
    val padded = header.all { it.startsWith(' ') && it.endsWith(' ') }
    return MdTable(
        header = header,
        aligns = aligns,
        rows = rows,
        style = MdTableStyle(
            outerPipes = outerPipes,
            padded = padded,
            lineEnding = dominantLineEnding(text),
            separatorCells = separatorCells,
        ),
    )
}

fun tableRegionAt(text: String, offset: Int): MdTableRegion? {
    val lines = tableLines(text)
    if (lines.size < 2) return null
    val cursorLine = text.substring(0, offset.coerceIn(0, text.length)).count { it == '\n' }
    val fenced = fencedLines(lines)

    for (headerLine in 0 until lines.lastIndex) {
        if (fenced[headerLine] || fenced[headerLine + 1]) continue
        val table = parseTable(text, headerLine) ?: continue
        var lastLine = headerLine + 1
        while (
            lastLine + 1 < lines.size &&
            !fenced[lastLine + 1] &&
            lines[lastLine + 1].content.isNotBlank() &&
            hasStructuralPipe(lines[lastLine + 1].content)
        ) {
            lastLine++
        }
        if (cursorLine in headerLine..lastLine) {
            return MdTableRegion(
                headerLine = headerLine,
                separatorLine = headerLine + 1,
                lastLine = lastLine,
            )
        }
        if (table.rows.isNotEmpty()) {
            // Skip body lines: none can start another table without a separator beneath it.
            continue
        }
    }
    return null
}

fun resizeTableAt(
    value: TextFieldValue,
    columns: Int,
    bodyRows: Int,
): MdTableDocumentResizeResult? {
    val region = tableRegionAt(value.text, value.selection.min) ?: return null
    val table = parseTable(value.text, region.headerLine) ?: return null
    val resized = resizeTable(table, columns, bodyRows)
    val rendered = renderTable(resized.table)
    val lines = tableLines(value.text)
    val start = lines[region.headerLine].start
    val end = lines[region.lastLine].end
    val newValue = value.copy(
        text = value.text.substring(0, start) + rendered + value.text.substring(end),
        selection = TextRange(start + firstCellCursor(rendered, resized.table.style.outerPipes)),
        composition = null,
    )
    return MdTableDocumentResizeResult(
        value = newValue,
        lostNonEmptyCells = resized.lostNonEmptyCells,
    )
}

fun renderTable(table: MdTable): String {
    require(table.header.isNotEmpty()) { "A table needs at least one column" }
    require(table.aligns.size == table.header.size) { "Every column needs an alignment" }

    val separatorCells = if (table.style.separatorCells.size == table.header.size) {
        table.style.separatorCells
    } else {
        table.aligns.map { alignment -> separatorCell(alignment, table.style.padded) }
    }
    val lines = buildList {
        add(renderCells(table.header, table.style.outerPipes))
        add(renderCells(separatorCells, table.style.outerPipes))
        table.rows.forEach { add(renderCells(it, table.style.outerPipes)) }
    }
    return lines.joinToString(table.style.lineEnding)
}

fun buildTable(columns: Int, bodyRows: Int, lineEnding: String): String {
    require(columns > 0) { "columns must be positive" }
    require(bodyRows > 0) { "bodyRows must be positive" }
    require(lineEnding == "\n" || lineEnding == "\r\n") { "Unsupported line ending" }

    val emptyCells = List(columns) { "  " }
    val separators = List(columns) { " --- " }
    return buildList {
        add(renderCells(emptyCells, outerPipes = true))
        add(renderCells(separators, outerPipes = true))
        repeat(bodyRows) { add(renderCells(emptyCells, outerPipes = true)) }
    }.joinToString(lineEnding)
}

fun insertTable(value: TextFieldValue, columns: Int, bodyRows: Int): TextFieldValue {
    val text = value.text
    val offset = value.selection.min.coerceIn(0, text.length)
    val lineEnding = dominantLineEnding(text)
    val lineStart = text.lastIndexOf('\n', startIndex = (offset - 1).coerceAtLeast(0)).let {
        if (offset == 0 || it == -1) 0 else it + 1
    }
    val newline = text.indexOf('\n', startIndex = offset)
    val rawLineEnd = if (newline == -1) text.length else newline
    val lineEnd = if (rawLineEnd > lineStart && text[rawLineEnd - 1] == '\r') {
        rawLineEnd - 1
    } else {
        rawLineEnd
    }
    val lineIsBlank = text.substring(lineStart, lineEnd).isBlank()
    val insertionOffset = if (lineIsBlank) lineStart else lineEnd
    val prefix = text.substring(0, insertionOffset)
    val suffix = text.substring(insertionOffset)
    val insideFence = isInsideFencedBlock(text, insertionOffset)
    val requiredBreaks = if (insideFence) 1 else 2
    val before = lineEnding.repeat(
        (requiredBreaks - trailingLineEndings(prefix, lineEnding)).coerceAtLeast(0),
    )
    val after = lineEnding.repeat(
        (requiredBreaks - leadingLineEndings(suffix, lineEnding)).coerceAtLeast(0),
    )
    val table = buildTable(columns, bodyRows, lineEnding)
    val insertion = before + table + after
    val firstCellOffset = insertionOffset + before.length + 2

    return value.copy(
        text = prefix + insertion + suffix,
        selection = TextRange(firstCellOffset),
        composition = null,
    )
}

fun resizeTable(table: MdTable, columns: Int, bodyRows: Int): MdTableResizeResult {
    require(columns > 0) { "columns must be positive" }
    require(bodyRows > 0) { "bodyRows must be positive" }

    val oldColumns = table.header.size
    val emptyCell = if (table.style.padded) "  " else ""
    val normalizedRows = table.rows.map { normalizeRow(it, oldColumns, emptyCell) }
    var lostNonEmptyCells = 0

    if (columns < oldColumns) {
        lostNonEmptyCells += table.header.drop(columns).count { it.isNotBlank() }
        normalizedRows.forEach { row ->
            lostNonEmptyCells += row.drop(columns).count { it.isNotBlank() }
        }
    }
    if (bodyRows < normalizedRows.size) {
        normalizedRows.drop(bodyRows).forEach { row ->
            lostNonEmptyCells += row.take(columns).count { it.isNotBlank() }
        }
    }

    fun resizeCells(cells: List<String>): List<String> = when {
        cells.size >= columns -> cells.take(columns)
        else -> cells + List(columns - cells.size) { emptyCell }
    }

    val resizedRows = normalizedRows.take(bodyRows).map(::resizeCells).toMutableList()
    repeat(bodyRows - resizedRows.size) {
        resizedRows += List(columns) { emptyCell }
    }

    val oldSeparators = table.style.separatorCells.ifEmpty {
        table.aligns.map { separatorCell(it, table.style.padded) }
    }
    val newSeparators = oldSeparators.take(columns).toMutableList()
    repeat(columns - newSeparators.size) {
        newSeparators += separatorCell(MdAlign.NONE, table.style.padded)
    }

    val newAligns = table.aligns.take(columns).toMutableList()
    repeat(columns - newAligns.size) { newAligns += MdAlign.NONE }

    return MdTableResizeResult(
        table = table.copy(
            header = resizeCells(table.header),
            aligns = newAligns,
            rows = resizedRows,
            style = table.style.copy(separatorCells = newSeparators),
        ),
        lostNonEmptyCells = lostNonEmptyCells,
    )
}

internal fun dominantLineEnding(text: String): String {
    var crlf = 0
    var lf = 0
    var index = 0
    while (index < text.length) {
        if (text[index] == '\n') {
            if (index > 0 && text[index - 1] == '\r') crlf++ else lf++
        }
        index++
    }
    return if (crlf > lf) "\r\n" else "\n"
}

private data class TableLine(
    val content: String,
    val start: Int,
    val end: Int,
)

private fun tableLines(text: String): List<TableLine> {
    val result = mutableListOf<TableLine>()
    var start = 0
    while (start <= text.length) {
        val newline = text.indexOf('\n', start)
        if (newline == -1) {
            val content = text.substring(start).removeSuffix("\r")
            result += TableLine(content = content, start = start, end = start + content.length)
            break
        }
        val end = if (newline > start && text[newline - 1] == '\r') newline - 1 else newline
        result += TableLine(content = text.substring(start, end), start = start, end = end)
        start = newline + 1
        if (start == text.length) {
            result += TableLine(content = "", start = start, end = start)
            break
        }
    }
    return result
}

private fun structuralPipeOffsets(line: String): List<Int> {
    val result = mutableListOf<Int>()
    var codeRun = 0
    var index = 0
    while (index < line.length) {
        if (line[index] == '`') {
            var end = index + 1
            while (end < line.length && line[end] == '`') end++
            val runLength = end - index
            if (codeRun == 0) codeRun = runLength else if (codeRun == runLength) codeRun = 0
            index = end
            continue
        }
        if (line[index] == '|' && codeRun == 0 && !isEscaped(line, index)) result += index
        index++
    }
    return result
}

private fun isEscaped(text: String, index: Int): Boolean {
    var backslashes = 0
    var cursor = index - 1
    while (cursor >= 0 && text[cursor] == '\\') {
        backslashes++
        cursor--
    }
    return backslashes % 2 == 1
}

private fun hasStructuralPipe(line: String): Boolean = structuralPipeOffsets(line).isNotEmpty()

private fun hasOuterPipes(line: String): Boolean {
    val pipes = structuralPipeOffsets(line)
    return pipes.size >= 2 &&
        line.substring(0, pipes.first()).isBlank() &&
        line.substring(pipes.last() + 1).isBlank()
}

private fun alignmentOf(cell: String): MdAlign? {
    val marker = cell.trim()
    if (marker.length < 3 || !Regex("^:?-+:?$").matches(marker)) return null
    return when {
        marker.startsWith(':') && marker.endsWith(':') -> MdAlign.CENTER
        marker.startsWith(':') -> MdAlign.LEFT
        marker.endsWith(':') -> MdAlign.RIGHT
        else -> MdAlign.NONE
    }
}

private fun separatorCell(alignment: MdAlign, padded: Boolean): String {
    val marker = when (alignment) {
        MdAlign.NONE -> "---"
        MdAlign.LEFT -> ":--"
        MdAlign.CENTER -> ":-:"
        MdAlign.RIGHT -> "--:"
    }
    return if (padded) " $marker " else marker
}

private fun renderCells(cells: List<String>, outerPipes: Boolean): String {
    val content = cells.joinToString("|")
    return if (outerPipes) "|$content|" else content
}

private fun normalizeRow(row: List<String>, columns: Int, emptyCell: String): List<String> {
    if (row.size <= columns) return row + List(columns - row.size) { emptyCell }
    if (columns == 1) return listOf(row.joinToString(" | "))
    return row.take(columns - 1) + row.drop(columns - 1).joinToString(" | ")
}

private fun fencedLines(lines: List<TableLine>): BooleanArray {
    val result = BooleanArray(lines.size)
    var marker: Char? = null
    var markerLength = 0
    lines.forEachIndexed { index, line ->
        val indentation = line.content.takeWhile { it == ' ' }.length
        val candidate = if (indentation <= 3) line.content.substring(indentation) else ""
        val candidateMarker = candidate.firstOrNull()
        val runLength = if (candidateMarker == '`' || candidateMarker == '~') {
            candidate.takeWhile { it == candidateMarker }.length
        } else {
            0
        }
        val fenceLine = runLength >= 3
        result[index] = marker != null || fenceLine
        if (fenceLine) {
            if (marker == null) {
                marker = candidateMarker
                markerLength = runLength
            } else if (
                marker == candidateMarker &&
                runLength >= markerLength &&
                candidate.substring(runLength).isBlank()
            ) {
                marker = null
                markerLength = 0
            }
        }
    }
    return result
}

private fun firstCellCursor(rendered: String, outerPipes: Boolean): Int {
    val header = rendered.substringBefore('\n').removeSuffix("\r")
    var cursor = if (outerPipes) structuralPipeOffsets(header).firstOrNull()?.plus(1) ?: 0 else 0
    while (cursor < header.length && header[cursor] == ' ') cursor++
    return cursor
}

private fun trailingLineEndings(text: String, lineEnding: String): Int {
    var count = 0
    var end = text.length
    while (end >= lineEnding.length && text.regionMatches(end - lineEnding.length, lineEnding, 0, lineEnding.length)) {
        count++
        end -= lineEnding.length
    }
    return count
}

private fun leadingLineEndings(text: String, lineEnding: String): Int {
    var count = 0
    var start = 0
    while (start + lineEnding.length <= text.length && text.regionMatches(start, lineEnding, 0, lineEnding.length)) {
        count++
        start += lineEnding.length
    }
    return count
}

private fun isInsideFencedBlock(text: String, offset: Int): Boolean {
    var marker: Char? = null
    var markerLength = 0
    var lineStart = 0
    while (lineStart < offset) {
        val newline = text.indexOf('\n', lineStart)
        val lineEnd = minOf(if (newline == -1) text.length else newline, offset)
        val line = text.substring(lineStart, lineEnd).removeSuffix("\r")
        val indentation = line.takeWhile { it == ' ' }.length
        if (indentation <= 3) {
            val candidate = line.substring(indentation)
            val candidateMarker = candidate.firstOrNull()
            if (candidateMarker == '`' || candidateMarker == '~') {
                val runLength = candidate.takeWhile { it == candidateMarker }.length
                if (runLength >= 3) {
                    if (marker == null) {
                        marker = candidateMarker
                        markerLength = runLength
                    } else if (
                        marker == candidateMarker &&
                        runLength >= markerLength &&
                        candidate.substring(runLength).isBlank()
                    ) {
                        marker = null
                        markerLength = 0
                    }
                }
            }
        }
        if (newline == -1 || newline >= offset) break
        lineStart = newline + 1
    }
    return marker != null
}
