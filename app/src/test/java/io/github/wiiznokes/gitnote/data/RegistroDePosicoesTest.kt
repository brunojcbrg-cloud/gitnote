package io.github.wiiznokes.gitnote.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * O registro de onde o usuario parou em cada nota.
 *
 * O que estes testes protegem: a posicao guardada tem de sobreviver a um registro
 * corrompido, a caminhos com espaco e acento (o vault dele e assim), e o limite
 * tem de descartar a nota mais antiga, nunca a que acabou de ser lida.
 */
class RegistroDePosicoesTest {

    private val nota = "04_IA_Workspace/Capturas/ideia solta.md"

    @Test
    fun `58 guarda e devolve a linha da nota`() {
        val registro = RegistroDePosicoes.registrar("", nota, 42)
        assertEquals(42, RegistroDePosicoes.posicao(registro, nota))
    }

    @Test
    fun `59 nota nunca vista devolve nulo em vez de zero`() {
        // Zero mandaria a tela para o topo como se ele tivesse parado la;
        // nulo deixa a tela decidir que nao ha retomada.
        val registro = RegistroDePosicoes.registrar("", nota, 10)
        assertNull(RegistroDePosicoes.posicao(registro, "outra.md"))
        assertNull(RegistroDePosicoes.posicao("", nota))
    }

    @Test
    fun `60 registro vazio nao quebra`() {
        assertEquals(0, RegistroDePosicoes.decodificar("").size)
        assertNull(RegistroDePosicoes.posicao("", nota))
    }

    @Test
    fun `61 caminho em branco nao entra no registro`() {
        assertEquals("", RegistroDePosicoes.registrar("", "   ", 5))
        assertNull(RegistroDePosicoes.posicao("", ""))
    }

    @Test
    fun `62 reabrir a mesma nota atualiza sem duplicar`() {
        var registro = RegistroDePosicoes.registrar("", nota, 10)
        registro = RegistroDePosicoes.registrar(registro, nota, 250)
        assertEquals(250, RegistroDePosicoes.posicao(registro, nota))
        assertEquals(1, RegistroDePosicoes.decodificar(registro).size)
    }

    @Test
    fun `63 a nota lida por ultimo fica na frente`() {
        var registro = RegistroDePosicoes.registrar("", "a.md", 1)
        registro = RegistroDePosicoes.registrar(registro, "b.md", 2)
        registro = RegistroDePosicoes.registrar(registro, "c.md", 3)
        assertEquals(listOf("c.md", "b.md", "a.md"), RegistroDePosicoes.decodificar(registro).keys.toList())
    }

    @Test
    fun `64 estourar o limite derruba a nota mais antiga`() {
        var registro = ""
        repeat(5) { i -> registro = RegistroDePosicoes.registrar(registro, "nota$i.md", i, limite = 3) }
        val mapa = RegistroDePosicoes.decodificar(registro)
        assertEquals(3, mapa.size)
        assertEquals(listOf("nota4.md", "nota3.md", "nota2.md"), mapa.keys.toList())
        assertNull(mapa["nota0.md"])
    }

    @Test
    fun `65 reler uma nota antiga a salva do descarte`() {
        var registro = ""
        repeat(3) { i -> registro = RegistroDePosicoes.registrar(registro, "nota$i.md", i, limite = 3) }
        // nota0 seria a proxima a cair; reabri-la joga ela para a frente da fila.
        registro = RegistroDePosicoes.registrar(registro, "nota0.md", 99, limite = 3)
        registro = RegistroDePosicoes.registrar(registro, "nova.md", 7, limite = 3)
        assertEquals(99, RegistroDePosicoes.posicao(registro, "nota0.md"))
        assertNull(RegistroDePosicoes.posicao(registro, "nota1.md"))
    }

    @Test
    fun `66 caminho com espaco acento e subpasta sobrevive`() {
        val caminhos = listOf(
            "02_Notas/Anatomia/Tronco Encefalico.md",
            "Aulas/Recombinacao Genetica e Heranca - 03.md",
            "notas/cabeca e pescoco (revisao).md",
        )
        var registro = ""
        caminhos.forEachIndexed { i, c -> registro = RegistroDePosicoes.registrar(registro, c, i * 10) }
        caminhos.forEachIndexed { i, c ->
            assertEquals(i * 10, RegistroDePosicoes.posicao(registro, c), "perdeu $c")
        }
    }

    @Test
    fun `67 caminho com tabulacao ou quebra e recusado sem corromper o registro`() {
        // O separador e a tabulacao: deixar passar embaralharia caminho com linha.
        val bom = RegistroDePosicoes.registrar("", nota, 8)
        assertEquals(bom, RegistroDePosicoes.registrar(bom, "ruim\tnome.md", 3))
        assertEquals(bom, RegistroDePosicoes.registrar(bom, "ruim\nnome.md", 3))
        assertEquals(8, RegistroDePosicoes.posicao(bom, nota))
    }

    @Test
    fun `68 linha negativa vira zero`() {
        val registro = RegistroDePosicoes.registrar("", nota, -5)
        assertEquals(0, RegistroDePosicoes.posicao(registro, nota))
    }

    @Test
    fun `69 registro corrompido nao lanca e salva o que da`() {
        // Perder a posicao de uma nota e aborrecimento; estourar ao abrir e defeito.
        val sujo = buildString {
            append("boa1.md\t10\n")
            append("sem separador\n")
            append("\t20\n")
            append("linha nao numerica.md\tabc\n")
            append("negativa.md\t-3\n")
            append("\n")
            append("boa2.md\t20")
        }
        val mapa = RegistroDePosicoes.decodificar(sujo)
        assertEquals(10, mapa["boa1.md"])
        assertEquals(20, mapa["boa2.md"])
        assertEquals(2, mapa.size)
    }

    @Test
    fun `70 primeira ocorrencia vence numa entrada repetida`() {
        val mapa = RegistroDePosicoes.decodificar("x.md\t1\nx.md\t2")
        assertEquals(1, mapa["x.md"])
        assertEquals(1, mapa.size)
    }

    @Test
    fun `71 esquecer apaga so a nota pedida`() {
        var registro = RegistroDePosicoes.registrar("", "a.md", 1)
        registro = RegistroDePosicoes.registrar(registro, "b.md", 2)
        val depois = RegistroDePosicoes.esquecer(registro, "a.md")
        assertNull(RegistroDePosicoes.posicao(depois, "a.md"))
        assertEquals(2, RegistroDePosicoes.posicao(depois, "b.md"))
    }

    @Test
    fun `72 esquecer nota ausente devolve o registro intacto`() {
        val registro = RegistroDePosicoes.registrar("", nota, 4)
        assertEquals(registro, RegistroDePosicoes.esquecer(registro, "nao existe.md"))
    }

    @Test
    fun `73 ida e volta pelo texto preserva tudo`() {
        var registro = ""
        repeat(50) { i -> registro = RegistroDePosicoes.registrar(registro, "pasta $i/nota.md", i * 3) }
        val mapa = RegistroDePosicoes.decodificar(registro)
        assertEquals(mapa, RegistroDePosicoes.decodificar(RegistroDePosicoes.codificar(mapa)))
        repeat(50) { i -> assertEquals(i * 3, mapa["pasta $i/nota.md"]) }
    }

    @Test
    fun `74 o limite padrao cobre o vault com folga`() {
        assertTrue(RegistroDePosicoes.LIMITE >= 300)
    }
}
