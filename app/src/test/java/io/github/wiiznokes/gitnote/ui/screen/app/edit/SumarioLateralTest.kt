package io.github.wiiznokes.gitnote.ui.screen.app.edit

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
        composeRule.onNodeWithTag("sumario-acoes-dobra").assertExists()
        composeRule.onNodeWithText("Collapse all").performClick()
        composeRule.runOnIdle { assertTrue(recolherTudoChamado) }
        composeRule.onNodeWithText("Expand all").performClick()
        composeRule.runOnIdle { assertTrue(expandirTudoChamado) }
        composeRule.onNodeWithTag("sumario-recolher-nivel-2").assertExists()
        composeRule.onNodeWithText("H2").performClick()
        composeRule.runOnIdle { assertEquals(2, nivelPedido) }
    }
}
