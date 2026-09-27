package io.github.wiiznokes.gitnote.ui.component.markdown

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val LARGURA_MAXIMA_NO_VISOR_PX = 4096

private sealed interface EstadoDoVisor {
    data object Carregando : EstadoDoVisor
    data class Pronto(val bitmap: ImageBitmap) : EstadoDoVisor
    data object Falhou : EstadoDoVisor
}

/**
 * Abre a figura em tela cheia com zoom (pedido do Bruno em 27/09: legendas de
 * anatomia ficam ilegíveis na largura da nota).
 *
 * Decodifica de novo, em resolução maior: a miniatura da nota (via
 * [decodificarAnexo]) é sub-amostrada para a largura da tela e perderia
 * detalhe mesmo com zoom. O botão fechar e o botão voltar do Android (que
 * `Dialog` já intercepta sozinho) chamam [aoFechar].
 */
@Composable
fun VisorDeImagem(
    caminho: String,
    aoFechar: () -> Unit,
    decodificar: (String, Int) -> ImageBitmap? = ::decodificarAnexoParaVisor,
) {
    Dialog(
        onDismissRequest = aoFechar,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            val estado by produceState<EstadoDoVisor>(EstadoDoVisor.Carregando, caminho) {
                value = withContext(Dispatchers.IO) {
                    decodificar(caminho, LARGURA_MAXIMA_NO_VISOR_PX)
                        ?.let { EstadoDoVisor.Pronto(it) }
                        ?: EstadoDoVisor.Falhou
                }
            }

            when (val estadoAtual = estado) {
                EstadoDoVisor.Carregando -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                )
                EstadoDoVisor.Falhou -> Text(
                    text = "Não foi possível abrir a imagem",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
                is EstadoDoVisor.Pronto -> ImagemComZoom(estadoAtual.bitmap)
            }

            IconButton(
                onClick = aoFechar,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.White)
            }
        }
    }
}

/**
 * Pinça para dar zoom (1× a 8×), arrastar dentro dos limites e duplo toque
 * para alternar 1×/2,5× no ponto tocado. A matemática mora em
 * [aplicarGestoDePincaEArrasto] e [alternarZoomNoDuploToque], testadas à
 * parte -- aqui só liga os detectores de gesto ao estado.
 */
@Composable
private fun ImagemComZoom(bitmap: ImageBitmap) {
    var transformacao by remember { mutableStateOf(TransformacaoDeImagem.INICIAL) }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val larguraPx = constraints.maxWidth.toFloat()
        val alturaPx = constraints.maxHeight.toFloat()
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = transformacao.escala
                    scaleY = transformacao.escala
                    translationX = transformacao.deslocamentoX
                    translationY = transformacao.deslocamentoY
                }
                .pointerInput(larguraPx, alturaPx) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        transformacao = aplicarGestoDePincaEArrasto(
                            atual = transformacao,
                            centroideX = centroid.x,
                            centroideY = centroid.y,
                            arrastoX = pan.x,
                            arrastoY = pan.y,
                            fatorDeZoom = zoom,
                            larguraContainer = larguraPx,
                            alturaContainer = alturaPx,
                        )
                    }
                }
                .pointerInput(larguraPx, alturaPx) {
                    detectTapGestures(
                        onDoubleTap = { ponto ->
                            transformacao = alternarZoomNoDuploToque(
                                atual = transformacao,
                                toqueX = ponto.x,
                                toqueY = ponto.y,
                                larguraContainer = larguraPx,
                                alturaContainer = alturaPx,
                            )
                        },
                    )
                },
        )
    }
}
