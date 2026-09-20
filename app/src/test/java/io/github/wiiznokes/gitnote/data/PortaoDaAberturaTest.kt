package io.github.wiiznokes.gitnote.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * O portao que separa "a tela esta montando" de "ele rolou".
 *
 * Estes testes reproduzem a ordem real da abertura: a varredura dos blocos anuncia
 * a linha 0, a rolagem emite 0 como primeiro valor, e so depois a retomada leva a
 * tela para onde ele parou. Se algum desses zeros for gravado, a posicao se perde.
 */
class PortaoDaAberturaTest {

    @Test
    fun `75 sem posicao guardada tudo e gravado`() {
        val portao = PortaoDaAbertura()
        portao.retomouEm(null)
        assertTrue(portao.deveGravar(0))
        assertTrue(portao.deveGravar(0))
        assertTrue(portao.deveGravar(30))
    }

    @Test
    fun `76 posicao guardada no topo nao precisa de protecao`() {
        // Retomar na linha 0 e o mesmo que nao retomar: nao ha nada a perder.
        val portao = PortaoDaAbertura()
        portao.retomouEm(0)
        assertTrue(portao.deveGravar(0))
    }

    @Test
    fun `77 o segundo zero da abertura tambem e segurado`() {
        // Este e o defeito que o portao existe para fechar: segurar so o primeiro
        // zero deixava o seguinte apagar a posicao guardada.
        val portao = PortaoDaAbertura()
        portao.retomouEm(120)
        assertFalse(portao.deveGravar(0), "primeiro zero (varredura dos blocos)")
        assertFalse(portao.deveGravar(0), "segundo zero (valor inicial da rolagem)")
        assertFalse(portao.deveGravar(0), "terceiro zero")
    }

    @Test
    fun `78 a primeira linha de verdade abre o portao`() {
        val portao = PortaoDaAbertura()
        portao.retomouEm(120)
        assertFalse(portao.deveGravar(0))
        assertTrue(portao.deveGravar(120), "a retomada chegou")
        // Dai em diante manda a tela, inclusive quando ele rola de volta ao topo.
        assertTrue(portao.deveGravar(0), "ele voltou ao topo de proposito")
        assertTrue(portao.deveGravar(0))
    }

    @Test
    fun `79 abertura completa em ordem`() {
        val portao = PortaoDaAbertura()
        portao.retomouEm(87)
        val gravados = listOf(0, 0, 87, 90, 95, 0, 12)
            .filter { portao.deveGravar(it) }
        assertTrue(gravados == listOf(87, 90, 95, 0, 12), "gravou $gravados")
    }

    @Test
    fun `80 linha negativa e tratada como topo`() {
        val portao = PortaoDaAbertura()
        portao.retomouEm(50)
        assertFalse(portao.deveGravar(-1))
        assertTrue(portao.deveGravar(50))
    }

    @Test
    fun `81 abrir outra nota rearma o portao`() {
        // O ViewModel e por nota, mas rearmar tem de funcionar caso ele seja reusado.
        val portao = PortaoDaAbertura()
        portao.retomouEm(10)
        assertTrue(portao.deveGravar(10))
        portao.retomouEm(200)
        assertFalse(portao.deveGravar(0))
        assertTrue(portao.deveGravar(200))
    }
}
