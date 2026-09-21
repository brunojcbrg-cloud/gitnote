package io.github.wiiznokes.gitnote.ui.component.markdown

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer
import com.mikepenz.markdown.model.ImageWidth
import com.mikepenz.markdown.model.PlaceholderConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Fração da memória do processo que o cache de imagens pode ocupar. */
private const val FATIA_DA_MEMORIA = 8

/** Largura suposta enquanto o tamanho real da imagem não é conhecido. */
private const val LARGURA_PROVISORIA_DP = 200f

/** Proporção suposta (4:3) enquanto o tamanho real não é conhecido. */
private const val PROPORCAO_PROVISORIA = 0.75f

/** Largura de tela suposta quando a janela ainda não foi medida. */
private const val LARGURA_DE_TELA_SUPOSTA_PX = 1080

/**
 * Cache de bitmaps decodificados, com teto em bytes.
 *
 * A chave inclui a largura alvo e a data do arquivo: reabrir a nota reaproveita,
 * mas colar a imagem de novo por cima do mesmo nome não devolve a antiga.
 */
object CacheDeImagens {
    private val cache: LruCache<String, ImageBitmap> = object : LruCache<String, ImageBitmap>(
        ((Runtime.getRuntime().maxMemory() / 1024L) / FATIA_DA_MEMORIA).toInt().coerceAtLeast(4096)
    ) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            ((value.width.toLong() * value.height.toLong() * 4L) / 1024L).toInt().coerceAtLeast(1)
    }

    fun obter(chave: String): ImageBitmap? = cache.get(chave)

    fun guardar(chave: String, imagem: ImageBitmap) {
        cache.put(chave, imagem)
    }

    fun limpar() = cache.evictAll()
}

/**
 * Decodifica o anexo em duas passadas: primeiro só os limites, depois a imagem
 * já sub-amostrada para a largura pedida. Sem isto, três fotos numa nota matam o
 * app por falta de memória -- 4000x3000 descomprime em 48 MB de bitmap.
 */
fun decodificarAnexo(caminhoAbsoluto: String, larguraAlvoPx: Int): ImageBitmap? {
    val arquivo = File(caminhoAbsoluto)
    if (!arquivo.isFile) return null

    val chave = "$caminhoAbsoluto|$larguraAlvoPx|${arquivo.lastModified()}|${arquivo.length()}"
    CacheDeImagens.obter(chave)?.let { return it }

    val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching { BitmapFactory.decodeFile(caminhoAbsoluto, limites) }.getOrNull()
    if (limites.outWidth <= 0 || limites.outHeight <= 0) return null

    val opcoes = BitmapFactory.Options().apply {
        inSampleSize = calcularAmostragem(limites.outWidth, larguraAlvoPx)
    }
    val bitmap = runCatching { BitmapFactory.decodeFile(caminhoAbsoluto, opcoes) }
        .getOrNull()
        ?: return null
    val imagem = bitmap.asImageBitmap()
    CacheDeImagens.guardar(chave, imagem)
    return imagem
}

/**
 * Desenha a imagem do anexo no modo leitura (Fase I.2).
 *
 * Só entende o endereço que [uriDeImagem] produz, e é isso que garante a decisão
 * I.7: um `![](https://...)` nunca chega aqui como imagem, porque o pré-passe não
 * o converte -- e mesmo que chegasse, [parseUriDeImagem] devolveria nulo.
 */
class TransformadorDeImagemDaNota(
    private val raizDoRepo: String,
    private val decodificar: (String, Int) -> ImageBitmap? = ::decodificarAnexo,
) : ImageTransformer {

    @Composable
    override fun transform(link: String): ImageData? {
        val pedido = parseUriDeImagem(link) ?: return null
        val densidade = LocalDensity.current
        val larguraDaJanela = LocalWindowInfo.current.containerSize.width
            .takeIf { it > 0 } ?: LARGURA_DE_TELA_SUPOSTA_PX
        val alvoPx = pedido.largura
            ?.let { with(densidade) { it.dp.toPx() }.toInt() }
            ?.coerceIn(1, larguraDaJanela)
            ?: larguraDaJanela

        val caminho = remember(raizDoRepo, pedido.caminho) {
            File(raizDoRepo, pedido.caminho).path
        }
        // Fora da composicao: decodificar aqui congelaria o quadro da rolagem.
        val imagem by produceState<ImageBitmap?>(null, caminho, alvoPx) {
            value = withContext(Dispatchers.IO) { decodificar(caminho, alvoPx) }
        }
        val bitmap = imagem ?: return null

        return ImageData(
            painter = BitmapPainter(bitmap),
            modifier = if (pedido.largura != null) {
                Modifier.width(pedido.largura.dp)
            } else {
                Modifier.fillMaxWidth()
            },
            contentScale = ContentScale.Fit,
        )
    }

    /**
     * Espaço reservado antes de a imagem chegar, para a lista não pular.
     *
     * Largura declarada na nota manda; sem ela, a largura natural, limitada pela
     * do contêiner -- é o mesmo que `max-width: 100%` faz na web.
     */
    override fun placeholderConfig(
        link: String,
        density: Density,
        containerSize: Size,
        imageWidth: ImageWidth,
        imageSize: Size,
        imageSizeChanged: ((link: String, Size) -> Unit)?,
    ): PlaceholderConfig {
        val pedido = parseUriDeImagem(link)
            ?: return super<ImageTransformer>.placeholderConfig(
                link, density, containerSize, imageWidth, imageSize, imageSizeChanged,
            )

        val larguraDoContainerDp = if (containerSize.isUnspecified) {
            null
        } else {
            with(density) { containerSize.width.toDp().value }
        }
        val naturalDp = if (imageSize.isUnspecified || imageSize.width <= 0f) {
            null
        } else {
            with(density) { imageSize.width.toDp().value }
        }
        val proporcao = if (imageSize.isUnspecified || imageSize.width <= 0f) {
            PROPORCAO_PROVISORIA
        } else {
            imageSize.height / imageSize.width
        }

        var largura = pedido.largura?.toFloat() ?: naturalDp ?: LARGURA_PROVISORIA_DP
        if (larguraDoContainerDp != null && larguraDoContainerDp > 0f) {
            largura = minOf(largura, larguraDoContainerDp)
        }
        return PlaceholderConfig(Size(largura, largura * proporcao))
    }
}
