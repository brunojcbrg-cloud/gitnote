package io.github.wiiznokes.gitnote.ui.component.markdown

const val ZOOM_MINIMO = 1f
const val ZOOM_MAXIMO = 8f
const val ZOOM_DUPLO_TOQUE = 2.5f

/** Escala e deslocamento (em px de tela) aplicados sobre a imagem no visor. */
data class TransformacaoDeImagem(
    val escala: Float,
    val deslocamentoX: Float,
    val deslocamentoY: Float,
) {
    companion object {
        val INICIAL = TransformacaoDeImagem(ZOOM_MINIMO, 0f, 0f)
    }
}

internal fun limitarEscala(escala: Float): Float = escala.coerceIn(ZOOM_MINIMO, ZOOM_MAXIMO)

/**
 * Quanto a imagem pode se afastar do centro num eixo sem deixar uma borda
 * inteira de fundo vazio atrás dela. Em repouso (escala mínima) é zero: a
 * imagem já preenche o contêiner (ContentScale.Fit) e não há o que arrastar.
 */
internal fun limiteDeArrasto(escala: Float, tamanhoDoEixo: Float): Float =
    if (escala <= ZOOM_MINIMO) 0f else (escala - ZOOM_MINIMO) * tamanhoDoEixo / 2f

internal fun limitarEixo(valor: Float, escala: Float, tamanhoDoEixo: Float): Float {
    val limite = limiteDeArrasto(escala, tamanhoDoEixo)
    return valor.coerceIn(-limite, limite)
}

/**
 * Aplica um passo de pinça+arrasto (de [detectTransformGestures]) sobre a
 * transformação atual.
 *
 * O ponto sob o centroide da pinça não pode "escorregar" na tela: por isso o
 * deslocamento é recalculado a partir de onde esse ponto do conteúdo estava
 * antes do gesto, na escala nova — não é só somar o arrasto bruto.
 */
internal fun aplicarGestoDePincaEArrasto(
    atual: TransformacaoDeImagem,
    centroideX: Float,
    centroideY: Float,
    arrastoX: Float,
    arrastoY: Float,
    fatorDeZoom: Float,
    larguraContainer: Float,
    alturaContainer: Float,
): TransformacaoDeImagem {
    val novaEscala = limitarEscala(atual.escala * fatorDeZoom)
    val centroX = larguraContainer / 2f
    val centroY = alturaContainer / 2f
    val pontoConteudoX = (centroideX - centroX - atual.deslocamentoX) / atual.escala
    val pontoConteudoY = (centroideY - centroY - atual.deslocamentoY) / atual.escala
    val novoXBruto = centroideX - centroX - pontoConteudoX * novaEscala + arrastoX
    val novoYBruto = centroideY - centroY - pontoConteudoY * novaEscala + arrastoY
    return TransformacaoDeImagem(
        escala = novaEscala,
        deslocamentoX = limitarEixo(novoXBruto, novaEscala, larguraContainer),
        deslocamentoY = limitarEixo(novoYBruto, novaEscala, alturaContainer),
    )
}

/**
 * Duplo toque alterna entre 1× (recentralizado) e [ZOOM_DUPLO_TOQUE], focado
 * no ponto tocado.
 */
internal fun alternarZoomNoDuploToque(
    atual: TransformacaoDeImagem,
    toqueX: Float,
    toqueY: Float,
    larguraContainer: Float,
    alturaContainer: Float,
): TransformacaoDeImagem {
    if (atual.escala > ZOOM_MINIMO) return TransformacaoDeImagem.INICIAL
    val centroX = larguraContainer / 2f
    val centroY = alturaContainer / 2f
    val novoXBruto = -(toqueX - centroX) * (ZOOM_DUPLO_TOQUE - ZOOM_MINIMO)
    val novoYBruto = -(toqueY - centroY) * (ZOOM_DUPLO_TOQUE - ZOOM_MINIMO)
    return TransformacaoDeImagem(
        escala = ZOOM_DUPLO_TOQUE,
        deslocamentoX = limitarEixo(novoXBruto, ZOOM_DUPLO_TOQUE, larguraContainer),
        deslocamentoY = limitarEixo(novoYBruto, ZOOM_DUPLO_TOQUE, alturaContainer),
    )
}
