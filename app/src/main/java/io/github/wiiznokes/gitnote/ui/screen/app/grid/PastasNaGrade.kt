package io.github.wiiznokes.gitnote.ui.screen.app.grid

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.wiiznokes.gitnote.R
import io.github.wiiznokes.gitnote.ui.screen.app.DrawerFolderModel
import io.github.wiiznokes.gitnote.utils.getParentPath

/**
 * Fase K.1 do handoff 10: pasta e item navegavel da grade, nao so da gaveta.
 *
 * Antes da Fase K, as 513 pastas do vault que so tem subpastas abriam em branco —
 * inclusive a pasta padrao do Bruno (`06_Conhecimento`, 6 subpastas e nenhuma nota
 * direta). A fonte de dados nao e nova: e o mesmo `drawerFolders` que ja alimenta a
 * gaveta, com o `noteCount` que ele ja traz.
 */
sealed interface ItemDeNavegacao {

    /** Chave estavel para as listas preguicosas. */
    val chave: String

    /** Volta para a pasta mae. So existe fora da raiz. */
    data class SubirUmNivel(val destino: String) : ItemDeNavegacao {
        override val chave: String get() = "subir_um_nivel"
    }

    data class Pasta(
        val relativePath: String,
        val nome: String,
        val quantidadeDeNotas: Int,
    ) : ItemDeNavegacao {
        override val chave: String get() = "pasta_$relativePath"
    }
}

internal object PastasNaGrade {

    /**
     * Os itens que aparecem **acima** das notas.
     *
     * Durante a busca a lista e vazia: o resultado da busca e de notas e e recursivo,
     * entao pasta nenhuma entra (regra explicita da K.1).
     */
    fun itens(
        pastaAtual: String,
        query: String,
        pastas: List<DrawerFolderModel>,
    ): List<ItemDeNavegacao> {

        if (query.isNotEmpty()) return emptyList()

        val subir: List<ItemDeNavegacao> = if (pastaAtual.isEmpty()) {
            emptyList()
        } else {
            listOf(ItemDeNavegacao.SubirUmNivel(destino = getParentPath(pastaAtual)))
        }

        return subir + pastas.map { pasta ->
            ItemDeNavegacao.Pasta(
                relativePath = pasta.noteFolder.relativePath,
                nome = pasta.noteFolder.fullName(),
                quantidadeDeNotas = pasta.noteCount,
            )
        }
    }

    /**
     * Estado vazio de verdade: nem pasta nem nota. Tela em branco nunca mais.
     *
     * [notasCarregando] evita a mensagem piscando enquanto a primeira pagina chega —
     * so se afirma "vazio" quando o Paging ja terminou de carregar.
     */
    fun mostrarVazio(
        itens: List<ItemDeNavegacao>,
        quantidadeDeNotas: Int,
        notasCarregando: Boolean,
    ): Boolean {
        if (notasCarregando || quantidadeDeNotas > 0) return false
        return itens.none { it is ItemDeNavegacao.Pasta }
    }
}

@Composable
internal fun LinhaDeNavegacao(
    item: ItemDeNavegacao,
    onAbrirPasta: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotulo = when (item) {
        is ItemDeNavegacao.SubirUmNivel -> stringResource(R.string.go_up_one_level)
        is ItemDeNavegacao.Pasta -> item.nome
    }

    val destino = when (item) {
        is ItemDeNavegacao.SubirUmNivel -> item.destino
        is ItemDeNavegacao.Pasta -> item.relativePath
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onAbrirPasta(destino) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = when (item) {
                    is ItemDeNavegacao.SubirUmNivel -> Icons.AutoMirrored.Filled.KeyboardReturn
                    is ItemDeNavegacao.Pasta -> Icons.Rounded.Folder
                },
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.secondary,
            )

            Text(
                text = rotulo,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (item is ItemDeNavegacao.Pasta) {
                Text(
                    text = item.quantidadeDeNotas.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.align(Alignment.BottomStart),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(80.dp),
        )
    }
}

@Composable
internal fun GradeVazia(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.empty_folder),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
