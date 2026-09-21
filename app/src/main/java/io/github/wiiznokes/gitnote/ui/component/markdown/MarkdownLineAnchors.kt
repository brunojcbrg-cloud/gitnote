package io.github.wiiznokes.gitnote.ui.component.markdown

fun lineStartOffsets(text: String): IntArray {
    val offsets = IntArray(text.count { it == '\n' } + 1)
    var line = 1
    text.forEachIndexed { index, character ->
        if (character == '\n') offsets[line++] = index + 1
    }
    return offsets
}

/**
 * Conta as quebras antes de [offset] sem alocar o vetor de linhas.
 * Esta no caminho de cada tecla do editor, entao nao pode alocar.
 */
fun lineOfOffset(text: String, offset: Int): Int {
    val end = offset.coerceIn(0, text.length)
    var line = 0
    var index = 0
    while (index < end) {
        if (text[index] == '\n') line++
        index++
    }
    return line
}

fun lineOfOffset(lineStarts: IntArray, offset: Int): Int {
    if (lineStarts.isEmpty()) return 0
    val result = lineStarts.binarySearch(offset.coerceAtLeast(0))
    return if (result >= 0) result else (-result - 2).coerceAtLeast(0)
}

fun offsetOfLineStart(text: String, line: Int): Int {
    val offsets = lineStartOffsets(text)
    return offsets[line.coerceIn(0, offsets.lastIndex)]
}

/** Returns null when [line] is before the first measured anchor. */
fun nearestAnchorAtOrBefore(line: Int, anchors: Map<Int, Int>): Int? = anchors
    .asSequence()
    .filter { (anchorLine, _) -> anchorLine <= line }
    .maxByOrNull { (anchorLine, _) -> anchorLine }
    ?.value

/** Returns the closest line above [position], or the first measured line when none is above it. */
fun nearestLineAtOrBefore(position: Int, anchors: Map<Int, Int>): Int? {
    if (anchors.isEmpty()) return null
    return anchors
        .asSequence()
        .filter { (_, anchorPosition) -> anchorPosition <= position }
        .maxByOrNull { (_, anchorPosition) -> anchorPosition }
        ?.key
        ?: anchors.minByOrNull { (_, anchorPosition) -> anchorPosition }?.key
}

/** Returns the first line at or below the viewport top, or the last measured line past the end. */
fun firstLineAtOrAfter(position: Int, anchors: Map<Int, Int>): Int? {
    if (anchors.isEmpty()) return null
    return anchors
        .asSequence()
        .filter { (_, anchorPosition) -> anchorPosition >= position }
        .minByOrNull { (_, anchorPosition) -> anchorPosition }
        ?.key
        ?: anchors.maxByOrNull { (_, anchorPosition) -> anchorPosition }?.key
}

/**
 * Onde o título clicado estava na tela no instante em que ele mandou dobrar.
 *
 * Guardar a posição na TELA, e não a rolagem, é o que faz o título ficar parado:
 * depois da dobra o conteúdo acima dele pode ter outro tamanho, então a rolagem
 * antiga apontaria para outro lugar.
 */
data class AncoraDaDobra(val linha: Int, val deslocamentoNaTela: Int)

/** Nulo quando o título ainda não foi medido -- aí não há para onde voltar. */
fun ancoraDoTitulo(linha: Int, y: Int?, rolagem: Int): AncoraDaDobra? =
    if (y == null) null else AncoraDaDobra(linha, y - rolagem)

/** A rolagem que devolve o título ao mesmo ponto da tela depois da dobra. */
fun rolagemQueMantemOTitulo(ancora: AncoraDaDobra, yDepois: Int, rolagemMaxima: Int): Int =
    (yDepois - ancora.deslocamentoNaTela).coerceIn(0, rolagemMaxima.coerceAtLeast(0))

/**
 * Espera o título voltar a ser medido depois da dobra e devolve a rolagem.
 *
 * Fica fora do composable para poder ser testado: nenhum teste consegue
 * construir o `MarkDownVM` de verdade (`GitManager` carrega `git_wrapper`).
 *
 * Devolve `false` quando o título não apareceu dentro de [quadros] quadros --
 * e, nesse caso, **não rola para lugar nenhum**. Rolar para zero por não ter
 * achado o título é exatamente o defeito que isto conserta.
 */
suspend fun devolverPosicaoDepoisDaDobra(
    ancora: AncoraDaDobra,
    quadros: Int,
    esperarQuadro: suspend () -> Unit,
    posicaoDoTitulo: () -> Int?,
    rolagemMaxima: () -> Int,
    rolarPara: suspend (Int) -> Unit,
): Boolean {
    repeat(quadros) {
        esperarQuadro()
        val y = posicaoDoTitulo()
        if (y != null) {
            rolarPara(rolagemQueMantemOTitulo(ancora, y, rolagemMaxima()))
            return true
        }
    }
    return false
}
