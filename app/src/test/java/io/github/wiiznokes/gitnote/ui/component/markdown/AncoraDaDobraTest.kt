package io.github.wiiznokes.gitnote.ui.component.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Recolher e expandir um título não pode mover a nota.
 *
 * O defeito relatado pelo Bruno em 21/09: expandir um tópico devolvia a nota ao
 * começo. A causa é que trocar o texto faz a biblioteca reparsear **fora da
 * composição**; por um quadro o corpo é um `Box` vazio, a altura rolável cai
 * para zero e o `ScrollState` corta a posição para 0.
 *
 * O `MarkDownVM` de verdade não é construível em teste (`GitManager` carrega
 * `git_wrapper`), então a lógica que devolve a posição vive fora do composable e
 * é medida aqui.
 */
class AncoraDaDobraTest {

    @Test
    fun aAncoraGuardaOndeOTituloEstavaNaTela() {
        // Título a 1.200 px do topo do documento, com a nota rolada em 1.000:
        // ele está a 200 px do alto da tela.
        val ancora = ancoraDoTitulo(linha = 42, y = 1_200, rolagem = 1_000)
        assertEquals(AncoraDaDobra(42, 200), ancora)
    }

    @Test
    fun semMedidaDoTituloNaoHaAncora() {
        assertNull(ancoraDoTitulo(linha = 42, y = null, rolagem = 1_000))
    }

    @Test
    fun oTituloVoltaParaOMesmoPontoDaTela() {
        val ancora = AncoraDaDobra(linha = 42, deslocamentoNaTela = 200)
        // Expandir empurrou o título para 1.500: a rolagem sobe junto.
        assertEquals(1_300, rolagemQueMantemOTitulo(ancora, yDepois = 1_500, rolagemMaxima = 9_999))
        // Recolher puxou o título para 900: a rolagem desce junto.
        assertEquals(700, rolagemQueMantemOTitulo(ancora, yDepois = 900, rolagemMaxima = 9_999))
    }

    @Test
    fun aRolagemRespeitaOsLimitesDoDocumento() {
        val ancora = AncoraDaDobra(linha = 0, deslocamentoNaTela = 500)
        // Titulo perto do topo: nao existe rolagem negativa.
        assertEquals(0, rolagemQueMantemOTitulo(ancora, yDepois = 100, rolagemMaxima = 9_999))
        // Documento que encolheu: nao passa do fim.
        assertEquals(300, rolagemQueMantemOTitulo(ancora, yDepois = 5_000, rolagemMaxima = 300))
        assertEquals(0, rolagemQueMantemOTitulo(ancora, yDepois = 5_000, rolagemMaxima = -1))
    }

    @Test
    fun esperaOTituloSerMedidoDeNovoAntesDeRolar() = runBlocking {
        // O titulo so volta a ser medido no quarto quadro, que e o caso real: o
        // parse acontece em outra thread e a primeira composicao vem vazia.
        var quadro = 0
        val rolagens = mutableListOf<Int>()
        val achou = devolverPosicaoDepoisDaDobra(
            ancora = AncoraDaDobra(linha = 7, deslocamentoNaTela = 150),
            quadros = 30,
            esperarQuadro = { quadro++ },
            posicaoDoTitulo = { if (quadro >= 4) 1_000 else null },
            rolagemMaxima = { 9_999 },
            rolarPara = { rolagens += it },
        )
        assertTrue(achou)
        assertEquals(listOf(850), rolagens)
        assertEquals(4, quadro, "nao pode continuar esperando depois de achar")
    }

    @Test
    fun tituloQueNuncaApareceNaoRolaParaLugarNenhum() = runBlocking {
        // A regressao que este teste tranca: rolar para 0 por nao ter achado o
        // titulo e exatamente o defeito relatado.
        val rolagens = mutableListOf<Int>()
        var quadros = 0
        val achou = devolverPosicaoDepoisDaDobra(
            ancora = AncoraDaDobra(linha = 7, deslocamentoNaTela = 150),
            quadros = 5,
            esperarQuadro = { quadros++ },
            posicaoDoTitulo = { null },
            rolagemMaxima = { 9_999 },
            rolarPara = { rolagens += it },
        )
        assertFalse(achou)
        assertTrue(rolagens.isEmpty(), "rolou sem ter achado o titulo: $rolagens")
        assertEquals(5, quadros)
    }

    @Test
    fun rolaNoPrimeiroQuadroQuandoOTituloJaEstaMedido() = runBlocking {
        val rolagens = mutableListOf<Int>()
        var quadros = 0
        devolverPosicaoDepoisDaDobra(
            ancora = AncoraDaDobra(linha = 1, deslocamentoNaTela = 0),
            quadros = 30,
            esperarQuadro = { quadros++ },
            posicaoDoTitulo = { 640 },
            rolagemMaxima = { 9_999 },
            rolarPara = { rolagens += it },
        )
        assertEquals(listOf(640), rolagens)
        assertEquals(1, quadros)
    }

    @Test
    fun oModoLeituraSeguraOConteudoEnquantoReparseia() {
        // A outra metade do conserto, e a que nenhum teste de composicao alcanca
        // sem depender de temporizacao: `retainState = true`. Com o padrao
        // `false`, `MarkdownStateImpl.updateInput` joga o estado para `Loading`
        // a cada troca de texto (`if (!newInput.retainState) stateFlow.value =
        // State.Loading(...)`), o corpo vira um Box vazio e o ScrollState corta
        // a rolagem para 0. Aqui so se tranca que o interruptor continua ligado.
        val raiz = System.getProperty("user.dir") ?: "."
        val tela = java.io.File(raiz, "src/main/java/io/github/wiiznokes/gitnote/ui/screen/app/edit/MarkDown.kt")
        val ajudante = java.io.File(raiz, "src/main/java/io/github/wiiznokes/gitnote/ui/screen/app/grid/markdownHelper.kt")
        check(tela.exists()) { "arquivo nao encontrado: ${tela.absolutePath}" }
        check(ajudante.exists()) { "arquivo nao encontrado: ${ajudante.absolutePath}" }
        assertTrue(
            tela.readText().contains("retainState = true"),
            "o modo leitura tem de segurar o conteudo durante o reparse",
        )
        assertTrue(
            ajudante.readText().contains("retainState = retainState"),
            "MarkdownCustomInner tem de repassar retainState para a biblioteca",
        )
    }
}
