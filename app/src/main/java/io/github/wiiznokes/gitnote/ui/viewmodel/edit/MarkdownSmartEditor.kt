@file:Suppress("IfThenToElvis")

package io.github.wiiznokes.gitnote.ui.viewmodel.edit

import android.util.Log
import androidx.compose.ui.text.TextRange
import kotlin.math.max
import kotlin.math.min


data class EdicaoDeTexto(val texto: String, val selecao: TextRange)

data class GatilhoSugestaoWikilink(
    val abertura: Int,
    val inicioSubstituicao: Int,
    val cursor: Int,
    val alvo: String,
    val consulta: String,
    val secao: Boolean,
)

private const val TAG = "markdownSmartEditor"

/**
 * Detecta apenas o `[[` aberto na linha do cursor. A tecla comum sai depois de
 * olhar essa linha; a varredura de cerca de código só roda quando há gatilho.
 */
fun gatilhoSugestaoWikilink(texto: String, selecao: TextRange): GatilhoSugestaoWikilink? {
    if (!selecao.collapsed) return null
    val cursor = selecao.start.coerceIn(0, texto.length)
    val inicioDaLinha = if (cursor == 0) {
        0
    } else {
        texto.lastIndexOf('\n', startIndex = cursor - 1).let { if (it < 0) 0 else it + 1 }
    }
    val antesDoCursor = texto.substring(inicioDaLinha, cursor)
    val aberturaNaLinha = antesDoCursor.lastIndexOf("[[")
    if (aberturaNaLinha < 0) return null
    val abertura = inicioDaLinha + aberturaNaLinha
    if (abertura > 0 && texto[abertura - 1] == '!') return null

    val digitado = texto.substring(abertura + 2, cursor)
    if ("]]" in digitado || '[' in digitado || ']' in digitado || '|' in digitado) return null
    if (isInsideFencedCodeBlock(texto, abertura)) return null
    if (isInsideInlineCode(texto, inicioDaLinha, abertura)) return null

    val separador = digitado.indexOf('#')
    return if (separador < 0) {
        GatilhoSugestaoWikilink(
            abertura = abertura,
            inicioSubstituicao = abertura + 2,
            cursor = cursor,
            alvo = digitado,
            consulta = digitado,
            secao = false,
        )
    } else {
        GatilhoSugestaoWikilink(
            abertura = abertura,
            inicioSubstituicao = abertura + 2 + separador + 1,
            cursor = cursor,
            alvo = digitado.substring(0, separador).trim(),
            consulta = digitado.substring(separador + 1),
            secao = true,
        )
    }
}

fun aplicarSugestaoWikilink(
    edicao: EdicaoDeTexto,
    gatilho: GatilhoSugestaoWikilink,
    textoAceito: String,
): EdicaoDeTexto {
    if (!edicao.selecao.collapsed || edicao.selecao.start != gatilho.cursor) return edicao
    if (gatilho.inicioSubstituicao !in 0..gatilho.cursor || gatilho.cursor > edicao.texto.length) {
        return edicao
    }
    val insercao = "$textoAceito]]"
    return edicao.copy(
        texto = edicao.texto.replaceRange(gatilho.inicioSubstituicao, gatilho.cursor, insercao),
        selecao = TextRange(gatilho.inicioSubstituicao + insercao.length),
    )
}

fun deveAtualizarSugestaoWikilink(
    textoAnterior: String,
    selecaoAnterior: TextRange,
    textoAtual: String,
    selecaoAtual: TextRange,
    gatilhoAtivo: Boolean,
): Boolean {
    if (gatilhoAtivo) return true
    if (!selecaoAtual.collapsed || textoAtual.length != textoAnterior.length + 1) return false
    val inserido = selecaoAtual.start - 1
    if (inserido !in textoAtual.indices || textoAtual[inserido] != '[') return false
    if (!textoAtual.regionMatches(0, textoAnterior, 0, inserido)) return false
    return textoAtual.regionMatches(
        inserido + 1,
        textoAnterior,
        selecaoAnterior.max,
        textoAnterior.length - selecaoAnterior.max,
    )
}

private fun isInsideInlineCode(texto: String, inicioDaLinha: Int, offset: Int): Boolean {
    var delimitador = 0
    var indice = inicioDaLinha
    while (indice < offset) {
        if (texto[indice] == '\\') {
            indice += 2
            continue
        }
        if (texto[indice] != '`') {
            indice++
            continue
        }
        var fim = indice + 1
        while (fim < offset && texto[fim] == '`') fim++
        val tamanho = fim - indice
        delimitador = when {
            delimitador == 0 -> tamanho
            delimitador == tamanho -> 0
            else -> delimitador
        }
        indice = fim
    }
    return delimitador > 0
}

fun markdownSmartEditor(
    prev: EdicaoDeTexto,
    v: EdicaoDeTexto
): EdicaoDeTexto {

    if (v.selecao.start == v.selecao.end) {

        val cursorPos = v.selecao.start
        if (cursorPos > 0 && cursorPos <= v.texto.length) {


            if (v.texto[cursorPos - 1] == '\n') {

                // handle delete key when the line is:
                // - x
                //
                val lineBreakStart = insertedLineBreakStart(prev, v, cursorPos)
                if (lineBreakStart == null) {
                    return v
                }

                val lineStart = v.texto.lastIndexOf('\n', startIndex = lineBreakStart - 1).let {
                    if (it == -1) 0 else it + 1
                }
                val lineBefore = v.texto.substring(lineStart, lineBreakStart)

                val currentLine = v.texto.indexOf('\n', startIndex = cursorPos).let {
                    if (it == -1) v.texto.length else it
                }.let {
                    v.texto.substring(cursorPos, it)
                }

                val res = ListItemInfo.parseSafely(lineBefore)

                if (isInsideFencedCodeBlock(v.texto, lineStart)) {
                    return v
                }

                // remove empty list line
                if (currentLine.isBlank() && res?.shouldRemove() == true) {

                    return v.copy(
                        texto = v.texto.substring(0, lineStart) + v.texto.substring(
                            cursorPos,
                            v.texto.length
                        ),
                        selecao = TextRange(lineStart),
                    )
                }

                // we are in a list
                // add a new empty similar list line
                if (res != null) {
                    val newText = res.copy(isChecked = false).prefix(numberOp = { it + 1 })
                    return v.copy(
                        texto = v.texto.substring(
                            0,
                            cursorPos
                        ) + res.padding + newText + v.texto.substring(
                            cursorPos,
                            v.texto.length
                        ),
                        selecao = TextRange(cursorPos + res.padding.length + newText.length),
                    )
                }
                // no list found, but we can still add the padding
                else {
                    val padding = getPadding(lineBefore)
                    if (padding != null) {
                        return v.copy(
                            texto = v.texto.substring(0, cursorPos) + padding + v.texto.substring(
                                cursorPos,
                                v.texto.length
                            ),
                            selecao = TextRange(cursorPos + padding.length),
                        )
                    }
                }
            } else {
                // remove padding, under certain conditions
                if (prev.texto.length == v.texto.length + 1) {

                    val start = v.texto.lastIndexOf('\n', startIndex = cursorPos - 1).let {
                        if (it == -1) 0 else it + 1
                    }

                    val currentLine = v.texto.substring(start, cursorPos)

                    if (currentLine.isBlank() && (prev.texto[cursorPos] == ' ' || prev.texto[cursorPos] == '\t')) {
                        return v.copy(
                            texto = v.texto.substring(0, start) + v.texto.substring(
                                cursorPos,
                                v.texto.length
                            ),
                            selecao = TextRange(cursorPos - (cursorPos - start))
                        )
                    }

                }
            }
        }
    }

    return v
}

private fun insertedLineBreakStart(
    prev: EdicaoDeTexto,
    value: EdicaoDeTexto,
    cursorPos: Int,
): Int? {
    val lineBreakStart = if (cursorPos >= 2 && value.texto[cursorPos - 2] == '\r') {
        cursorPos - 2
    } else {
        cursorPos - 1
    }
    val selectionStart = prev.selecao.min
    val selectionEnd = prev.selecao.max

    if (lineBreakStart != selectionStart) return null
    if (!value.texto.regionMatches(0, prev.texto, 0, selectionStart)) return null

    val suffixLength = prev.texto.length - selectionEnd
    val valueSuffixStart = value.texto.length - suffixLength
    if (valueSuffixStart != cursorPos) return null
    if (!value.texto.regionMatches(valueSuffixStart, prev.texto, selectionEnd, suffixLength)) return null

    return lineBreakStart
}

private fun isInsideFencedCodeBlock(text: String, offset: Int): Boolean {
    var fenceMarker: Char? = null
    var fenceLength = 0
    var lineStart = 0

    while (lineStart < offset) {
        val nextLineBreak = text.indexOf('\n', startIndex = lineStart)
        val lineEnd = min(
            if (nextLineBreak == -1) text.length else nextLineBreak,
            offset,
        )
        val line = text.substring(lineStart, lineEnd).removeSuffix("\r")
        val indentation = line.takeWhile { it == ' ' }.length
        if (indentation <= 3) {
            val candidate = line.substring(indentation)
            val marker = candidate.firstOrNull()
            if (marker == '`' || marker == '~') {
                val runLength = candidate.takeWhile { it == marker }.length
                if (runLength >= 3) {
                    if (fenceMarker == null) {
                        fenceMarker = marker
                        fenceLength = runLength
                    } else if (
                        marker == fenceMarker &&
                        runLength >= fenceLength &&
                        candidate.substring(runLength).isBlank()
                    ) {
                        fenceMarker = null
                        fenceLength = 0
                    }
                }
            }
        }

        if (nextLineBreak == -1 || nextLineBreak >= offset) break
        lineStart = nextLineBreak + 1
    }

    return fenceMarker != null
}

sealed class ListType {
    object Dash : ListType()
    object Asterisk : ListType()
    object Quote : ListType()
    data class Number(val number: Int) : ListType()

    fun prefix(numberOp: (Int) -> Int = { it }): String {
        return when (this) {
            Asterisk -> "* "
            Dash -> "- "
            Quote -> "> "
            is Number -> "${numberOp(number)}. "
        }
    }
}

data class ListItemInfo(
    val listType: ListType = ListType.Dash,
    val isTaskList: Boolean = false,
    val isChecked: Boolean = false,
    val padding: String = "",
    val title: String? = null,
) {

    companion object {

        fun parseSafely(line: String): ListItemInfo? {
            return try {
                parse(line)
            } catch (e: Exception) {
                Log.d(TAG, "$e")
                null
            }
        }

        fun parse(line: String): ListItemInfo? {
            val quoteMatch = Regex("""^(\s*)>\s(.*)?""").matchEntire(line)
            if (quoteMatch != null) {
                return ListItemInfo(
                    listType = ListType.Quote,
                    padding = quoteMatch.groups[1]?.value.orEmpty(),
                    title = quoteMatch.groups[2]?.value,
                )
            }

            val regex = Regex("""^(\s*)(?:(-)|(\*)|(\d+)\.)\s(?:\[([ xX])]\s)?(.+)?""")
            val match = regex.matchEntire(line) ?: return null

            val padding = match.groups[1]?.value ?: throw Exception("padding null: $line")

            val listType = when {
                match.groups[2]?.value != null -> ListType.Dash
                match.groups[3]?.value != null -> ListType.Asterisk
                match.groups[4]?.value != null -> match.groups[4]?.value?.toInt()?.let {
                    ListType.Number(it)
                }

                else -> null
            }

            if (listType == null) {
                throw Exception("listType is null but we have a match: $line")
            }

            val isTaskList = match.groups[5] != null

            val isChecked = match.groups[5]?.value != " "

            val title = match.groups[6]?.value

            return ListItemInfo(
                listType = listType,
                isTaskList = isTaskList,
                isChecked = isChecked,
                padding = padding,
                title = title
            )
        }


        fun fromLineFallBack(line: String): ListItemInfo {
            return ListItemInfo(
                padding = getPadding(line) ?: ""
            )
        }

    }

    fun prefix(numberOp: (Int) -> Int = { it }): String {
        var text = listType.prefix(numberOp)
        if (this.isTaskList) {
            text += if (isChecked) "[x] " else "[ ] "
        }
        return text
    }

    fun line(numberOp: (Int) -> Int = { it }, minusPaddingInTitle: Boolean = false): String {
        var finalText = padding
        finalText += prefix(numberOp)

        if (title != null) {
            finalText += if (minusPaddingInTitle) {
                if (title.startsWith(padding))
                    title.substring(padding.length)
                else title
            } else title
        }

        return finalText
    }

    fun lineWithoutPrefix(): String {
        var finalText = padding
        finalText += title
        return finalText
    }

    fun shouldRemove(): Boolean {
        return title?.isNotBlank() != true
    }


}


fun getPadding(line: String): String? {
    val match = Regex("^\\s+").find(line)
    return match?.value
}


fun onTitle(v: EdicaoDeTexto): EdicaoDeTexto {
    val cursorPosMin = v.selecao.min

    val pattern = "### "
    return if (v.selecao.collapsed) {
        // check if line start with "### "
        val start = v.texto.lastIndexOf('\n', startIndex = cursorPosMin - 1).let {
            if (it == -1) 0 else it + 1
        }

        // remove it
        if (v.texto.startsWith(pattern, startIndex = start)) {
            v.copy(
                texto = v.texto.substring(0, start) + v.texto.substring(
                    start + pattern.length,
                    v.texto.length
                ),
                selecao = TextRange(cursorPosMin - pattern.length)
            )
        }
        // add it
        else {
            v.copy(
                texto = v.texto.substring(0, start) + pattern + v.texto.substring(
                    start,
                    v.texto.length
                ),
                selecao = TextRange(cursorPosMin + pattern.length)
            )
        }
    } else {
        // check if the text before cursorPosStart is "### "

        // remove it
        if (v.texto.substring(0, cursorPosMin).endsWith(pattern)) {
            v.copy(
                texto = v.texto.substring(0, cursorPosMin - pattern.length) + v.texto.substring(
                    cursorPosMin,
                    v.texto.length
                ),
                selecao = TextRange(
                    start = v.selecao.start - pattern.length,
                    end = v.selecao.end - pattern.length,
                )
            )
        }
        // add it
        else {
            v.copy(
                texto = v.texto.substring(0, cursorPosMin) + pattern + v.texto.substring(
                    cursorPosMin,
                    v.texto.length
                ),
                selecao = TextRange(
                    start = v.selecao.start + pattern.length,
                    end = v.selecao.end + pattern.length,
                )
            )
        }
    }
}


fun addOrRemovePatternAtTheExtremitiesOfSelection(
    v: EdicaoDeTexto,
    startPattern: String,
    endPattern: String
): EdicaoDeTexto {
    val cursorPosMin = v.selecao.min
    val cursorPosMax = v.selecao.max

    // if already present, remove it
    return if (v.texto.substring(0, cursorPosMin).endsWith(startPattern)
        && v.texto.startsWith(endPattern, startIndex = cursorPosMax)
    ) {
        v.copy(
            texto = v.texto.substring(0, cursorPosMin - startPattern.length)
                    + v.texto.substring(cursorPosMin, cursorPosMax)
                    + v.texto.substring(cursorPosMax + endPattern.length, v.texto.length),
            selecao = TextRange(
                start = v.selecao.start - startPattern.length,
                end = v.selecao.end - startPattern.length,
            )
        )
    }
    // else, add it
    else {
        v.copy(
            texto = v.texto.substring(0, cursorPosMin)
                    + startPattern + v.texto.substring(cursorPosMin, cursorPosMax) + endPattern
                    + v.texto.substring(cursorPosMax, v.texto.length),
            selecao = TextRange(
                start = v.selecao.start + startPattern.length,
                end = v.selecao.end + startPattern.length,
            )
        )

    }
}

fun addOrRemovePatternAtTheExtremitiesOfSelection(
    v: EdicaoDeTexto,
    pattern: String
): EdicaoDeTexto {
    return addOrRemovePatternAtTheExtremitiesOfSelection(v, pattern, pattern)
}

fun onCode(v: EdicaoDeTexto): EdicaoDeTexto {
    val cursorPosMin = v.selecao.min
    val cursorPosMax = v.selecao.max

    return if (v.texto.substring(cursorPosMin, cursorPosMax).contains('\n')) {
        addOrRemovePatternAtTheExtremitiesOfSelection(v, "```\n", "\n```")
    } else {
        addOrRemovePatternAtTheExtremitiesOfSelection(v, "`")
    }
}

fun onLink(v: EdicaoDeTexto): EdicaoDeTexto {
    val cursorPosMin = v.selecao.min
    val cursorPosMax = v.selecao.max

    val startPattern = "["
    val endPattern = "](url)"

    // remove url pattern
    return if (v.texto.startsWith(startPattern, startIndex = cursorPosMin - startPattern.length)
        && v.texto.startsWith(endPattern, startIndex = cursorPosMax)
    ) {
        v.copy(
            texto = v.texto.substring(0, cursorPosMin - startPattern.length)
                    + v.texto.substring(cursorPosMin, cursorPosMax)
                    + v.texto.substring(cursorPosMax + endPattern.length, v.texto.length),
            selecao = TextRange(
                start = v.selecao.start - startPattern.length,
                end = v.selecao.end - startPattern.length,
            )
        )
    }
    // add it
    else {
        v.copy(
            texto = v.texto.substring(0, cursorPosMin)
                    + startPattern
                    + v.texto.substring(cursorPosMin, cursorPosMax)
                    + endPattern
                    + v.texto.substring(cursorPosMax, v.texto.length),
            selecao = if (v.selecao.collapsed) TextRange(
                start = v.selecao.start + startPattern.length,
                end = v.selecao.end + startPattern.length,
            ) else TextRange(
                start = cursorPosMax + 3,
                end = cursorPosMax + 6,
            )
        )
    }

}

fun Int.max(b: Int): Int = max(this, b)

/**
 * @param f1 this callback will be called on each line selected. Return true if you want to short circuit.
 * @param f2 this callback is also called on each line selected, after f1. Return the modified line
 */
private fun multiLinePrefixModifier(
    v: EdicaoDeTexto,
    f1: (String) -> Boolean,
    f2: (String, Int) -> String
): EdicaoDeTexto {
    val cursorPosMin = if (v.texto.getOrNull(v.selecao.min) == '\n') {
        // if we are at the end of a line, decrement the cursor to include the line
        v.selecao.min - 1
    } else {
        v.selecao.min
    }

    val start = v.texto.lastIndexOf('\n', startIndex = cursorPosMin).let {
        if (it == -1) 0 else it + 1
    }

    val cursorPosMax = v.selecao.max
    val end = v.texto.indexOf('\n', startIndex = cursorPosMax).let {
        if (it == -1) v.texto.length else it
    }

    val subString = v.texto.substring(start, end)

    val lines = subString.lines()

    for (line in lines) {
        if (f1(line)) break
    }

    var res = v.texto.substring(0, start)

    var deltaAll = 0
    var deltaFirstLine = 0

    for (line in lines.withIndex()) {
        val newLine = f2(line.value, line.index + 1)
        val delta = newLine.length - line.value.length
        if (line.index == 0) {
            deltaFirstLine = delta
        }
        deltaAll += delta
        res += if (line.index == lines.size - 1) {
            newLine
        } else {
            "$newLine\n"
        }
    }

    res += v.texto.substring(end, v.texto.length)

    return v.copy(
        texto = res,
        selecao = if (v.selecao.reversed) TextRange(
            start = (v.selecao.start + deltaAll).max(start),
            end = (v.selecao.end + deltaFirstLine).max(start),
        ) else TextRange(
            start = (v.selecao.start + deltaFirstLine).max(start),
            end = (v.selecao.end + deltaAll).max(start),
        )
    )
}

fun onQuote(v: EdicaoDeTexto): EdicaoDeTexto {

    var atListOneListToConvert = false
    val pattern = "> "

    return multiLinePrefixModifier(
        v = v,
        f1 = { line ->

            if (!line.startsWith(pattern)) {
                atListOneListToConvert = true
            }
            atListOneListToConvert
        },
        f2 = { line, lineNumber ->
            return@multiLinePrefixModifier if (atListOneListToConvert) {
                if (line.startsWith(pattern)) line else pattern + line
            } else {
                line.substring(startIndex = pattern.length)
            }
        }
    )
}


fun onUnorderedList(v: EdicaoDeTexto): EdicaoDeTexto {

    var atListOneListToConvert = false
    var defaultListInfo: ListItemInfo? = null

    return multiLinePrefixModifier(
        v = v,
        f1 = {
            val res = ListItemInfo.parseSafely(line = it)
            if (res == null) {
                atListOneListToConvert = true
            } else {
                if (defaultListInfo == null) {
                    defaultListInfo = res
                }
                if (res.listType != ListType.Dash) {
                    atListOneListToConvert = true
                }
            }
            atListOneListToConvert && defaultListInfo != null
        },
        f2 = { line, lineNumber ->

            val res = ListItemInfo.parseSafely(line)
            return@multiLinePrefixModifier if (res != null) {
                if (atListOneListToConvert) {
                    res.copy(listType = ListType.Dash).line()
                } else {
                    res.lineWithoutPrefix()
                }

            } else {
                if (defaultListInfo == null) {
                    defaultListInfo = ListItemInfo()
                }

                defaultListInfo.copy(
                    listType = ListType.Dash,
                    isChecked = false,
                    title = line,
                    padding = getPadding(line) ?: ""
                ).line(minusPaddingInTitle = true)
            }
        }
    )
}

fun onNumberedList(v: EdicaoDeTexto): EdicaoDeTexto {

    var atListOneListToConvert = false
    var defaultListInfo: ListItemInfo? = null

    return multiLinePrefixModifier(
        v = v,
        f1 = {
            val res = ListItemInfo.parseSafely(line = it)
            if (res == null) {
                atListOneListToConvert = true
            } else {
                if (defaultListInfo == null) {
                    defaultListInfo = res
                }
                if (res.listType !is ListType.Number) {
                    atListOneListToConvert = true
                }
            }
            atListOneListToConvert && defaultListInfo != null
        },
        f2 = { line, lineNumber ->

            val res = ListItemInfo.parseSafely(line)
            return@multiLinePrefixModifier if (res != null) {
                if (atListOneListToConvert) {
                    res.copy(listType = ListType.Number(lineNumber)).line()
                } else {
                    res.lineWithoutPrefix()
                }
            } else {
                if (defaultListInfo == null) {
                    defaultListInfo = ListItemInfo()
                }

                defaultListInfo.copy(
                    listType = ListType.Number(lineNumber),
                    isChecked = false,
                    title = line,
                    padding = getPadding(line) ?: ""
                ).line(minusPaddingInTitle = true)
            }
        }
    )
}

fun onTaskList(v: EdicaoDeTexto): EdicaoDeTexto {

    var atListOneListToConvert = false
    var defaultListInfo: ListItemInfo? = null

    return multiLinePrefixModifier(
        v = v,
        f1 = {
            val res = ListItemInfo.parseSafely(line = it)
            if (res == null) {
                atListOneListToConvert = true
            } else {
                if (defaultListInfo == null) {
                    defaultListInfo = res
                }
                if (!res.isTaskList) {
                    atListOneListToConvert = true
                }
            }
            atListOneListToConvert && defaultListInfo != null
        },
        f2 = { line, lineNumber ->

            val res = ListItemInfo.parseSafely(line)
            return@multiLinePrefixModifier if (res != null) {
                if (atListOneListToConvert) {
                    res.copy(
                        isTaskList = true,
                    ).line(numberOp = { lineNumber })
                } else {
                    res.lineWithoutPrefix()
                }

            } else {
                if (defaultListInfo == null) {
                    defaultListInfo = ListItemInfo()
                }

                defaultListInfo.copy(
                    isTaskList = true,
                    isChecked = false,
                    title = line,
                    padding = getPadding(line) ?: ""
                ).line(numberOp = { lineNumber }, minusPaddingInTitle = true)
            }
        }
    )
}

/**
 * O recuo de um nivel e uma **tabulacao**, e nao espacos.
 *
 * Medido no vault em 21/09/2026: 1.489 itens de lista aninhados usam tabulacao
 * contra 515 com espacos. E tambem o que a tecla Tab do Obsidian insere. Recuar
 * com espacos aqui faria a mesma nota ser aninhada de um jeito no celular e de
 * outro no computador.
 */
const val RECUO_DE_UM_NIVEL = "\t"

/** Quantos espacos valem um nivel, para desrecuar o que veio escrito com espaco. */
private const val ESPACOS_POR_NIVEL = 4

/**
 * So item de lista recua.
 *
 * Linha comum recuada vira **bloco de codigo** no markdown -- quatro espacos ou
 * uma tabulacao no comeco e a sintaxe de codigo indentado. Entao o botao nao
 * mexe em paragrafo: silenciosamente transformar o texto do Bruno em codigo
 * seria pior que nao fazer nada. Citacao (`>`) tambem fica de fora pelo mesmo
 * motivo: `\t> x` nao e citacao aninhada, e codigo.
 */
private fun ehItemDeListaRecuavel(linha: String): Boolean {
    val info = ListItemInfo.parseSafely(linha) ?: return false
    return info.listType != ListType.Quote
}

private fun semUmNivelDeRecuo(linha: String): String {
    if (linha.startsWith(RECUO_DE_UM_NIVEL)) return linha.removePrefix(RECUO_DE_UM_NIVEL)
    val espacos = linha.takeWhile { it == ' ' }.length.coerceAtMost(ESPACOS_POR_NIVEL)
    return linha.substring(espacos)
}

/** Tab: aninha o item de lista um nivel. Vale para todas as linhas da selecao. */
fun onAumentarRecuo(v: EdicaoDeTexto): EdicaoDeTexto = multiLinePrefixModifier(
    v = v,
    f1 = { false },
    f2 = { linha, _ ->
        if (ehItemDeListaRecuavel(linha)) RECUO_DE_UM_NIVEL + linha else linha
    },
)

/** Shift+Tab: devolve o item um nivel. Sem recuo, a linha fica como esta. */
fun onDiminuirRecuo(v: EdicaoDeTexto): EdicaoDeTexto = multiLinePrefixModifier(
    v = v,
    f1 = { false },
    f2 = { linha, _ ->
        if (ehItemDeListaRecuavel(linha)) semUmNivelDeRecuo(linha) else linha
    },
)
