package io.github.wiiznokes.gitnote.ui.screen.app.grid

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * F.2: o chevron de recolher/expandir é desenhado por `positionedHeading` em cima do
 * título de verdade (`MarkdownCustomInner`), não por um componente à parte. Testado
 * compondo o composable direto, no mesmo caminho que `SumarioLateralTest` usa para
 * fugir do bloqueio de `GitManager`/`git_wrapper` (Fase A/C/E).
 *
 * POR QUE `LocalInspectionMode provides true` (medido na rodada 35523618557, que ficou
 * vermelha sem isto): `com.mikepenz.markdown` v0.43.0 parseia o markdown **fora da
 * composição** — `rememberMarkdownState` só chama `parseBlocking()` dentro do `remember`
 * quando `immediate = true`, e esse parâmetro tem default `LocalInspectionMode.current`.
 * Sem isso, o parse vai para `withContext(Dispatchers.Default)` disparado por um
 * `LaunchedEffect`, e nem `waitForIdle()` nem `runOnIdle` esperam trabalho numa thread
 * de fora do Compose: o teste media a árvore com o estado ainda em `State.Loading`, que
 * desenha o `loading = { Box(modifier) }` vazio — nenhum nó de texto, nem com chevron
 * nem sem. Ligar o modo de inspeção é o mesmo interruptor que a própria biblioteca usa
 * para os previews, e faz o parse acontecer de forma síncrona na primeira composição.
 * Não mexe em produção e não afrouxa nada do que está sendo provado aqui.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MarkdownCustomInnerRecolherTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun cliqueNoTituloChamaOToggleComOOffsetDoParser() {
        val conteudo = "# Título\ncorpo"
        var offsetRecebido: Int? = null
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                Box(Modifier.width(320.dp).height(400.dp)) {
                    MarkdownCustomInner(
                        content = conteudo,
                        onHeadingCollapseToggle = { offsetRecebido = it },
                        isHeadingCollapsed = { false },
                    )
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Título", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Título", substring = true).performClick()
        composeRule.runOnIdle {
            assertEquals(conteudo.indexOf("# Título"), offsetRecebido)
        }
    }

    @Test
    fun semCallbackDeToggleOTituloContinuaSemChevronClicavelExtra() {
        // Sem onHeadingCollapseToggle, positionedHeading devolve o delegate original
        // (nenhuma Row/IconButton extra) -- é o caminho que a Fase E/FlashcardScreens.kt
        // já usava e não pode regredir. Provado dos dois lados: comportamento (o título
        // renderizado não tem ação de clique) e estrutura (a guarda no código-fonte).
        val conteudo = "# Título\ncorpo"
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                Box(Modifier.width(320.dp).height(400.dp)) {
                    MarkdownCustomInner(content = conteudo)
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Título", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Título", substring = true).assertHasNoClickAction()

        val fonte = File(System.getProperty("user.dir") ?: ".", "src/main/java/io/github/wiiznokes/gitnote/ui/screen/app/grid/markdownHelper.kt")
        check(fonte.exists()) { "arquivo nao encontrado: ${fonte.absolutePath}" }
        assertTrue(
            fonte.readText().contains("if (onHeadingPositioned == null && onHeadingCollapseToggle == null) return delegate"),
            "sem nenhum callback de titulo, positionedHeading tem que devolver o delegate original",
        )
    }
}
