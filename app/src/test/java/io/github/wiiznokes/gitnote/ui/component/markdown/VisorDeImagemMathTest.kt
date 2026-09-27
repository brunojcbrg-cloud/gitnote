package io.github.wiiznokes.gitnote.ui.component.markdown

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val LARGURA = 1_000f
private const val ALTURA = 2_000f
private const val TOLERANCIA = 0.01f

private fun assertProximo(esperado: Float, atual: Float) {
    assertTrue(abs(esperado - atual) < TOLERANCIA, "esperado $esperado, veio $atual")
}

class VisorDeImagemMathTest {

    @Test
    fun `escala fica sempre entre 1x e 8x`() {
        assertEquals(ZOOM_MINIMO, limitarEscala(0.2f))
        assertEquals(ZOOM_MINIMO, limitarEscala(1f))
        assertEquals(4f, limitarEscala(4f))
        assertEquals(ZOOM_MAXIMO, limitarEscala(50f))
    }

    @Test
    fun `em repouso nao ha o que arrastar`() {
        assertEquals(0f, limiteDeArrasto(ZOOM_MINIMO, LARGURA))
        assertEquals(0f, limitarEixo(500f, ZOOM_MINIMO, LARGURA))
    }

    @Test
    fun `arrasto e limitado proporcional a escala, nunca deixando fundo vazio`() {
        // Em 2x, o excesso de conteudo por eixo e (escala-1)*tamanho; metade de
        // cada lado pode ir para o centro.
        assertEquals(500f, limiteDeArrasto(2f, LARGURA))
        assertEquals(500f, limitarEixo(10_000f, 2f, LARGURA))
        assertEquals(-500f, limitarEixo(-10_000f, 2f, LARGURA))
        assertEquals(200f, limitarEixo(200f, 2f, LARGURA))
    }

    @Test
    fun `pinca no centro so aumenta a escala, sem deslocar`() {
        val resultado = aplicarGestoDePincaEArrasto(
            atual = TransformacaoDeImagem.INICIAL,
            centroideX = LARGURA / 2f,
            centroideY = ALTURA / 2f,
            arrastoX = 0f,
            arrastoY = 0f,
            fatorDeZoom = 2f,
            larguraContainer = LARGURA,
            alturaContainer = ALTURA,
        )
        assertEquals(2f, resultado.escala)
        assertProximo(0f, resultado.deslocamentoX)
        assertProximo(0f, resultado.deslocamentoY)
    }

    @Test
    fun `pinca fora do centro mantem o ponto tocado sob o dedo`() {
        // Tocando 250px a esquerda do centro e dobrando o zoom: sem correcao,
        // o ponto fugiria mais pra esquerda ainda (o conteudo cresce a partir
        // do centro do container); a imagem desloca pra direita para
        // trazer esse ponto de volta a onde o dedo esta.
        val toqueX = LARGURA / 2f - 250f
        val resultado = aplicarGestoDePincaEArrasto(
            atual = TransformacaoDeImagem.INICIAL,
            centroideX = toqueX,
            centroideY = ALTURA / 2f,
            arrastoX = 0f,
            arrastoY = 0f,
            fatorDeZoom = 2f,
            larguraContainer = LARGURA,
            alturaContainer = ALTURA,
        )
        assertEquals(2f, resultado.escala)
        // +250 de deslocamento bruto, e o limite em 2x e 500 -> nao corta.
        assertProximo(250f, resultado.deslocamentoX)
    }

    @Test
    fun `zoom nunca passa de 8x mesmo com fator de pinca enorme`() {
        val resultado = aplicarGestoDePincaEArrasto(
            atual = TransformacaoDeImagem.INICIAL,
            centroideX = LARGURA / 2f,
            centroideY = ALTURA / 2f,
            arrastoX = 0f,
            arrastoY = 0f,
            fatorDeZoom = 100f,
            larguraContainer = LARGURA,
            alturaContainer = ALTURA,
        )
        assertEquals(ZOOM_MAXIMO, resultado.escala)
    }

    @Test
    fun `arrasto puro em repouso nao move a imagem`() {
        val resultado = aplicarGestoDePincaEArrasto(
            atual = TransformacaoDeImagem.INICIAL,
            centroideX = LARGURA / 2f,
            centroideY = ALTURA / 2f,
            arrastoX = 300f,
            arrastoY = 300f,
            fatorDeZoom = 1f,
            larguraContainer = LARGURA,
            alturaContainer = ALTURA,
        )
        assertEquals(0f, resultado.deslocamentoX)
        assertEquals(0f, resultado.deslocamentoY)
    }

    @Test
    fun `duplo toque de 1x vai para 2,5x focado no ponto tocado`() {
        val toqueX = LARGURA / 2f + 100f
        val toqueY = ALTURA / 2f + 40f
        val resultado = alternarZoomNoDuploToque(
            atual = TransformacaoDeImagem.INICIAL,
            toqueX = toqueX,
            toqueY = toqueY,
            larguraContainer = LARGURA,
            alturaContainer = ALTURA,
        )
        assertEquals(ZOOM_DUPLO_TOQUE, resultado.escala)
        // Toque a +100 do centro: desloca -150 (100 * (2.5-1)) para trazer o
        // ponto tocado de volta ao centro da tela.
        assertProximo(-150f, resultado.deslocamentoX)
        assertProximo(-60f, resultado.deslocamentoY)
    }

    @Test
    fun `duplo toque com zoom ativo sempre volta a 1x recentralizado`() {
        val comZoom = TransformacaoDeImagem(escala = 4f, deslocamentoX = 300f, deslocamentoY = -200f)
        val resultado = alternarZoomNoDuploToque(
            atual = comZoom,
            toqueX = 10f,
            toqueY = 10f,
            larguraContainer = LARGURA,
            alturaContainer = ALTURA,
        )
        assertEquals(TransformacaoDeImagem.INICIAL, resultado)
    }

    @Test
    fun `duplo toque bem longe do centro respeita o limite de arrasto em 2,5x`() {
        // Um toque tao longe do centro pediria um deslocamento maior que o
        // limite de arrasto permitido em 2,5x; tem que cortar, não estourar.
        val resultado = alternarZoomNoDuploToque(
            atual = TransformacaoDeImagem.INICIAL,
            toqueX = LARGURA * 3f,
            toqueY = ALTURA * 3f,
            larguraContainer = LARGURA,
            alturaContainer = ALTURA,
        )
        val limiteX = limiteDeArrasto(ZOOM_DUPLO_TOQUE, LARGURA)
        val limiteY = limiteDeArrasto(ZOOM_DUPLO_TOQUE, ALTURA)
        assertEquals(-limiteX, resultado.deslocamentoX)
        assertEquals(-limiteY, resultado.deslocamentoY)
    }
}
