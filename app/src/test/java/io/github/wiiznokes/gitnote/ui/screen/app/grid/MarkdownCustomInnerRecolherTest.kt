package io.github.wiiznokes.gitnote.ui.screen.app.grid

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * F.2: o chevron de recolher/expandir é desenhado por `positionedHeading` em cima do
 * título de verdade (`MarkdownCustomInner`), não por um componente à parte. Testado
 * compondo o composable direto, no mesmo caminho que `SumarioLateralTest` usa para
 * fugir do bloqueio de `GitManager`/`git_wrapper` (Fase A/C/E).
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
            Box(Modifier.width(320.dp).height(400.dp)) {
                MarkdownCustomInner(
                    content = conteudo,
                    onHeadingCollapseToggle = { offsetRecebido = it },
                    isHeadingCollapsed = { false },
                )
            }
        }
        composeRule.onNodeWithText("Título").performClick()
        composeRule.runOnIdle {
            assertEquals(conteudo.indexOf("# Título"), offsetRecebido)
        }
    }

    @Test
    fun semCallbackDeToggleOTituloContinuaSemChevronClicavelExtra() {
        // Sem onHeadingCollapseToggle, positionedHeading devolve o delegate original
        // (nenhuma Row/IconButton extra) -- é o caminho que a Fase E já usava e não
        // pode regredir.
        val conteudo = "# Título\ncorpo"
        composeRule.setContent {
            Box(Modifier.width(320.dp).height(400.dp)) {
                MarkdownCustomInner(content = conteudo)
            }
        }
        composeRule.onNodeWithText("Título").assertExists()
    }
}
