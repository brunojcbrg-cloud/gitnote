package io.github.wiiznokes.gitnote.ui.screen.app.edit

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import io.github.wiiznokes.gitnote.ui.component.markdown.offsetOfLineStart
import io.github.wiiznokes.gitnote.ui.component.markdown.sumarioDe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SumarioLateralTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun painelCom269TitulosNavegaParaLinhaSemOcuparMaisDe62PorCentoEm320dp() {
        val nota = (0 until 269).joinToString("\n") { "## Título $it" }
        val itens = sumarioDe(nota)
        var cursor = -1
        composeRule.setContent {
            Box(Modifier.width(320.dp).height(600.dp).testTag("largura-tela")) {
                SumarioLateral(
                    itens = itens,
                    linhaAtual = 0,
                    onItemClick = { cursor = offsetOfLineStart(nota, it.linha) },
                    onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithText("Título 0").assertExists()
        composeRule.onNodeWithText("Título 3").performClick()
        composeRule.runOnIdle { assertEquals(nota.indexOf("## Título 3"), cursor) }
        val largura = composeRule.onNodeWithTag("sumario-painel")
            .fetchSemanticsNode().boundsInRoot.width
        val larguraTela = composeRule.onNodeWithTag("largura-tela")
            .fetchSemanticsNode().boundsInRoot.width
        assertTrue(largura <= larguraTela * 0.62f + 1f)
    }

    @Test
    fun painelEm375dpMantemDocumentoVisivel() {
        composeRule.setContent {
            Box(Modifier.width(375.dp).height(600.dp).testTag("largura-tela")) {
                SumarioLateral(
                    itens = sumarioDe("# Primeiro\n## Segundo"),
                    linhaAtual = 1,
                    onItemClick = {},
                    onDismiss = {},
                )
            }
        }
        val largura = composeRule.onNodeWithTag("sumario-painel")
            .fetchSemanticsNode().boundsInRoot.width
        val larguraTela = composeRule.onNodeWithTag("largura-tela")
            .fetchSemanticsNode().boundsInRoot.width
        assertTrue(largura <= larguraTela * 0.62f + 1f)
    }

    @Test
    fun acoesDeDobraNaoAparecemSemCallback() {
        composeRule.setContent {
            SumarioLateral(
                itens = sumarioDe("# Único"),
                linhaAtual = 0,
                onItemClick = {},
                onDismiss = {},
            )
        }
        composeRule.onNodeWithTag("sumario-acoes-dobra").assertDoesNotExist()
    }

    @Test
    fun recolherTudoExpandirTudoERecolherAteNivelChamamOsCallbacksCertos() {
        val nota = listOf("# A", "## B", "### C").joinToString("\n")
        val itens = sumarioDe(nota)
        var recolherTudoChamado = false
        var expandirTudoChamado = false
        var nivelPedido: Int? = null
        composeRule.setContent {
            Box(Modifier.width(320.dp).height(600.dp)) {
                SumarioLateral(
                    itens = itens,
                    linhaAtual = 0,
                    onItemClick = {},
                    onDismiss = {},
                    onRecolherTudo = { recolherTudoChamado = true },
                    onExpandirTudo = { expandirTudoChamado = true },
                    onRecolherAteNivel = { nivelPedido = it },
                )
            }
        }
        composeRule.onNodeWithTag("sumario-acoes-dobra").assertExists()
        composeRule.onNodeWithText("Collapse all").performClick()
        composeRule.runOnIdle { assertTrue(recolherTudoChamado) }
        composeRule.onNodeWithText("Expand all").performClick()
        composeRule.runOnIdle { assertTrue(expandirTudoChamado) }
        // POR QUE `performScrollTo` (medido na rodada 35523618557: expected:<2> but
        // was:<null>, com o `assertExists` da mesma tag passando): a linha de ações é um
        // `Row` com `horizontalScroll` dentro de um painel de `min(280dp, 62% da tela)`.
        // "Collapse all" + "Expand all" já consomem essa largura, então o botão de nível
        // fica fora do recorte: o nó existe e tem coordenadas, mas o toque injetado no
        // centro dele cai fora da área clipada e não atinge botão nenhum. Num aparelho o
        // usuário rola a linha -- o teste é que clicava sem rolar. A asserção continua a
        // mesma: o callback de verdade tem de ser chamado com o nível 2.
        composeRule.onNodeWithTag("sumario-recolher-nivel-2").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(2, nivelPedido) }
    }

    @Test
    fun aSetaDoPainelEscondeEDevolveOsSubtitulos() {
        // Pedido do Bruno em 21/09: recolher o titulo NO SUMARIO, para os
        // subtopicos aparecerem ou nao. E so da lista -- nao mexe na nota.
        val nota = """
            # Um
            ## Um.A
            ## Um.B
            # Dois
        """.trimIndent()
        val itens = sumarioDe(nota)
        val offsetDoUm = itens.first { it.texto == "Um" }.offset
        composeRule.setContent {
            var recolhidos by remember { mutableStateOf(setOf<Int>()) }
            Box(Modifier.width(320.dp).height(600.dp)) {
                SumarioLateral(
                    itens = itens,
                    linhaAtual = 0,
                    onItemClick = {},
                    onDismiss = {},
                    recolhidosNoSumario = recolhidos,
                    onRecolherNoSumario = { offset ->
                        recolhidos = if (offset in recolhidos) recolhidos - offset else recolhidos + offset
                    },
                )
            }
        }
        composeRule.onNodeWithText("Um.A").assertExists()
        composeRule.onNodeWithTag("sumario-seta-" + offsetDoUm).performClick()
        composeRule.onNodeWithText("Um.A").assertDoesNotExist()
        composeRule.onNodeWithText("Um.B").assertDoesNotExist()
        // O titulo recolhido e o irmao dele continuam na lista.
        composeRule.onNodeWithText("Um").assertExists()
        composeRule.onNodeWithText("Dois").assertExists()
        composeRule.onNodeWithTag("sumario-seta-" + offsetDoUm).performClick()
        composeRule.onNodeWithText("Um.A").assertExists()
    }

    @Test
    fun tituloSemSubtituloNaoGanhaSeta() {
        val nota = """
            # Um
            ## Um.A
            # Dois
        """.trimIndent()
        val itens = sumarioDe(nota)
        val offsetDoDois = itens.first { it.texto == "Dois" }.offset
        composeRule.setContent {
            Box(Modifier.width(320.dp).height(600.dp)) {
                SumarioLateral(
                    itens = itens,
                    linhaAtual = 0,
                    onItemClick = {},
                    onDismiss = {},
                    recolhidosNoSumario = emptySet(),
                    onRecolherNoSumario = {},
                )
            }
        }
        composeRule.onNodeWithTag("sumario-seta-" + offsetDoDois).assertDoesNotExist()
    }

    @Test
    fun semOCallbackOPainelContinuaComoEraAntes() {
        // Nao-regressao: quem chama sem recolhimento de painel nao ganha seta
        // nenhuma e continua vendo a lista inteira.
        val nota = """
            # Um
            ## Um.A
            # Dois
        """.trimIndent()
        val itens = sumarioDe(nota)
        composeRule.setContent {
            Box(Modifier.width(320.dp).height(600.dp)) {
                SumarioLateral(
                    itens = itens,
                    linhaAtual = 0,
                    onItemClick = {},
                    onDismiss = {},
                    recolhidosNoSumario = setOf(itens.first().offset),
                )
            }
        }
        composeRule.onNodeWithText("Um.A").assertExists()
        composeRule.onNodeWithTag("sumario-seta-" + itens.first().offset).assertDoesNotExist()
    }

    @Test
    fun cliqueNoTextoContinuaNavegandoComASetaPresente() {
        val nota = """
            # Um
            ## Um.A
            # Dois
        """.trimIndent()
        val itens = sumarioDe(nota)
        var escolhido: String? = null
        composeRule.setContent {
            Box(Modifier.width(320.dp).height(600.dp)) {
                SumarioLateral(
                    itens = itens,
                    linhaAtual = 0,
                    onItemClick = { escolhido = it.texto },
                    onDismiss = {},
                    recolhidosNoSumario = emptySet(),
                    onRecolherNoSumario = {},
                )
            }
        }
        composeRule.onNodeWithText("Um.A").performClick()
        composeRule.runOnIdle { assertEquals("Um.A", escolhido) }
    }

    @Test
    fun recolherNoPainelNaoJogaAListaDeVoltaParaOComeco() {
        // Relatado por ele em 21/09, logo depois da b77: recolher um titulo la
        // embaixo devolvia o painel ao primeiro titulo, e ele tinha de rolar
        // tudo de novo. A causa era o efeito de rolagem olhar a LISTA; agora
        // olha so a linha que esta sendo lida, que recolher nao muda.
        val nota = (0 until 40).joinToString("\n") { "# Pai $it\n## Filho $it" }
        val itens = sumarioDe(nota)
        val paiLaEmbaixo = itens.first { it.texto == "Pai 30" }
        composeRule.setContent {
            var recolhidos by remember { mutableStateOf(setOf<Int>()) }
            Box(Modifier.width(320.dp).height(600.dp)) {
                SumarioLateral(
                    itens = itens,
                    linhaAtual = 0,
                    onItemClick = {},
                    onDismiss = {},
                    recolhidosNoSumario = recolhidos,
                    onRecolherNoSumario = { offset ->
                        recolhidos = if (offset in recolhidos) recolhidos - offset else recolhidos + offset
                    },
                )
            }
        }
        composeRule.onNodeWithTag("sumario-lista").performScrollToNode(hasText("Pai 30"))
        composeRule.onNodeWithTag("sumario-seta-" + paiLaEmbaixo.offset).performClick()
        // Recolheu de verdade...
        composeRule.onNodeWithText("Filho 30").assertDoesNotExist()
        // ...e o painel continua mostrando onde ele estava.
        composeRule.onNodeWithText("Pai 30").assertIsDisplayed()
    }
}
