package io.github.wiiznokes.gitnote.ui.component.markdown

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer
import com.mikepenz.markdown.model.ImageWidth
import com.mikepenz.markdown.model.PlaceholderConfig
import io.github.wiiznokes.gitnote.ui.screen.app.grid.MarkdownCustomInner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Fase I.2 -- prova que o embed atravessa o caminho de leitura inteiro e chega ao
 * transformador de imagem, **com o anotador de producao no meio**
 * (`missingWikilinkAnnotator`, o mesmo que `MarkDownContent` usa no modo leitura).
 * Sem esta prova, os testes puros garantiriam so o texto reescrito, e um anotador
 * que engolisse o no de imagem passaria despercebido.
 *
 * `LocalInspectionMode provides true` pelo mesmo motivo medido em
 * `MarkdownCustomInnerRecolherTest`: sem ele o parse da v0.43.0 sai da composicao e
 * o teste mede o estado `Loading`, que nao desenha nada.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImagemNaLeituraUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * Guarda todo endereco que a biblioteca pedir, tanto para desenhar quanto para
     * reservar espaco -- os dois caminhos provam que o embed chegou.
     */
    private class TransformadorFalso : ImageTransformer {
        val pedidos = mutableListOf<String>()

        @Composable
        override fun transform(link: String): ImageData? {
            pedidos += link
            return ImageData(painter = ColorPainter(Color.Red))
        }

        override fun placeholderConfig(
            link: String,
            density: Density,
            containerSize: Size,
            imageWidth: ImageWidth,
            imageSize: Size,
            imageSizeChanged: ((link: String, Size) -> Unit)?,
        ): PlaceholderConfig {
            pedidos += link
            return PlaceholderConfig(Size(120f, 90f))
        }
    }

    private fun compor(conteudo: String, transformador: TransformadorFalso) {
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                Box(Modifier.width(320.dp).height(400.dp)) {
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

    @Test
    fun oEmbedResolvidoChegaAoTransformadorComALarguraDaNota() {
        val transformador = TransformadorFalso()
        val conteudo = preprocessarImagens("texto\n\n![[foto.png|300]]\n") {
            "$PASTA_ANEXOS/foto.png"
        }
        assertTrue(conteudo.contains("gitnote://image"), "pre-passe falhou: $conteudo")

        compor(conteudo, transformador)
        composeRule.waitUntil(timeoutMillis = 5_000) { transformador.pedidos.isNotEmpty() }

        val pedido = assertNotNull(parseUriDeImagem(transformador.pedidos.first()))
        assertEquals("$PASTA_ANEXOS/foto.png", pedido.caminho)
        assertEquals(300, pedido.largura)
    }

    @Test
    fun imagemRemotaNaoChegaAoTransformadorEContinuaVisivelComoTexto() {
        val transformador = TransformadorFalso()
        val conteudo = preprocessarImagens("![diagrama](https://exemplo.com/foto.png)\n") { null }

        compor(conteudo, transformador)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("exemplo.com", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.runOnIdle {
            assertTrue(
                transformador.pedidos.isEmpty(),
                "imagem remota nao pode ser buscada (decisao I.7): ${transformador.pedidos}",
            )
        }
    }

    @Test
    fun notaSemImagemNaoChamaOTransformador() {
        val transformador = TransformadorFalso()
        compor("# Titulo\n\ncorpo com [[wikilink]]\n", transformador)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("corpo", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.runOnIdle {
            assertTrue(transformador.pedidos.isEmpty(), "${transformador.pedidos}")
        }
    }
}
