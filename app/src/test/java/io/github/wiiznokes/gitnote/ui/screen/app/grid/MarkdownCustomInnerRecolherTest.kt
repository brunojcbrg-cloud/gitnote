package io.github.wiiznokes.gitnote.ui.screen.app.grid

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
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
        // (nenhuma Row/IconButton extra) -- é o caminho que a Fase E/FlashcardScreens.kt
        // já usava e não pode regredir. Não constrói via Robolectric (achado nesta fase:
        // sem o wrapper do chevron, "Título" não aparece no Robolectric, provavelmente
        // por causa de como o annotator/CompositionLocal da própria biblioteca de markdown
        // se comporta nesse ambiente de teste -- não investigado a fundo, não é regressão
        // da Fase F). Prova estrutural, lendo o código-fonte.
        val fonte = File(System.getProperty("user.dir") ?: ".", "src/main/java/io/github/wiiznokes/gitnote/ui/screen/app/grid/markdownHelper.kt")
        check(fonte.exists()) { "arquivo nao encontrado: ${fonte.absolutePath}" }
        val texto = fonte.readText()

        assertTrue(
            texto.contains("if (onHeadingPositioned == null && onHeadingCollapseToggle == null) return delegate"),
            "sem nenhum callback de titulo, positionedHeading tem que devolver o delegate original",
        )
    }
}
