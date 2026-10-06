package io.github.wiiznokes.gitnote.ui.component.markdown

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import io.github.wiiznokes.gitnote.ui.screen.app.grid.MarkdownCustomInner
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Os dois defeitos relatados em 06/10 na nota `P1 Pratica` de Parasitologia,
 * medidos no caminho de leitura de producao (anotador e componentes reais):
 *
 * 1. As fotos tem ~280 px. So com `Modifier.width`, o `ContentScale.Fit` usava a
 *    altura natural como teto e pintava a foto no tamanho natural -- o `|568`
 *    gravado pelo Obsidian nao mudava nada na tela.
 * 2. `> [!success] titulo` saia como citacao com o `[!success]` cru.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CalloutETamanhoUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Largura do contêiner da nota no teste, em dp. */
    private val larguraDaTela = 320

    private fun compor(conteudo: String) {
        // Mesmo tamanho das fotos reais da nota (279x209).
        val transformador = TransformadorDeImagemDaNota(
            raizDoRepo = "/repo",
            decodificar = { _, _ -> ImageBitmap(279, 209) },
        )
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                Box(Modifier.width(larguraDaTela.dp).height(900.dp)) {
                    MarkdownCustomInner(
                        content = conteudo,
                        imageTransformer = transformador,
                        annotator = missingWikilinkAnnotator(
                            warningColor = Color.Red,
                            highlightColor = Color.Black,
                            highlightBackground = Color.Yellow,
                        ),
                    )
                }
            }
        }
    }

    private fun esperarImagem() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("foto")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun larguraDaNotaAmpliaAFotoPequenaMantendoAProporcao() {
        compor("![foto](${uriDeImagem("a.png", 300)})\n")
        esperarImagem()

        composeRule.onNodeWithContentDescription("foto")
            .assertWidthIsEqualTo(300.dp)
            .assertHeightIsEqualTo((300f * 209f / 279f).dp)
    }

    @Test
    fun larguraMaiorQueATelaFicaNaLarguraDaTela() {
        compor("![foto](${uriDeImagem("a.png", 568)})\n")
        esperarImagem()

        composeRule.onNodeWithContentDescription("foto")
            .assertWidthIsEqualTo(larguraDaTela.dp)
            .assertHeightIsEqualTo((larguraDaTela * 209f / 279f).dp)
    }

    @Test
    fun semLarguraNaNotaValeAPxNaturalComoDp() {
        compor("![foto](${uriDeImagem("a.png", null)})\n")
        esperarImagem()

        composeRule.onNodeWithContentDescription("foto")
            .assertWidthIsEqualTo(279.dp)
            .assertHeightIsEqualTo(209.dp)
    }

    @Test
    fun calloutEscondeOMarcadorEMostraOTitulo() {
        compor(
            "> [!success] ✅ Caiu em prova: 115 T1 Q1\n" +
                "- **Doença**: Teníase\n",
        )
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Caiu em prova", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        assertEquals(
            0,
            composeRule.onAllNodesWithText("[!success]", substring = true)
                .fetchSemanticsNodes().size,
            "o marcador do callout continua visivel",
        )
        assertTrue(
            composeRule.onAllNodesWithContentDescription("success")
                .fetchSemanticsNodes().isNotEmpty(),
            "o icone do callout nao foi desenhado",
        )
        assertTrue(
            composeRule.onAllNodesWithText("Teníase", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun citacaoComumContinuaComoEstava() {
        compor("> uma citacao [!success] no meio\n")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("[!success]", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
