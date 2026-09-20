package io.github.wiiznokes.gitnote.ui.screen.app.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.wiiznokes.gitnote.R
import io.github.wiiznokes.gitnote.ui.component.markdown.ItemDeSumario

@Composable
internal fun SumarioLateral(
    itens: List<ItemDeSumario>,
    linhaAtual: Int,
    onItemClick: (ItemDeSumario) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onRecolherTudo: (() -> Unit)? = null,
    onExpandirTudo: (() -> Unit)? = null,
    onRecolherAteNivel: ((Int) -> Unit)? = null,
) {
    val selecionado = itens.indexOfLast { it.linha <= linhaAtual }.coerceAtLeast(0)
    val listState = rememberLazyListState()
    LaunchedEffect(selecionado, itens) {
        if (itens.isNotEmpty()) listState.scrollToItem(selecionado)
    }

    BoxWithConstraints(modifier = modifier.fillMaxHeight()) {
        val largura = minOf(280.dp, maxWidth * 0.62f)
        Surface(
            modifier = Modifier
                .width(largura)
                .fillMaxHeight()
                .testTag("sumario-painel"),
            shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            shadowElevation = 8.dp,
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.note_outline),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_outline))
                    }
                }
                if (onRecolherTudo != null || onExpandirTudo != null || onRecolherAteNivel != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .testTag("sumario-acoes-dobra"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (onRecolherTudo != null) {
                            TextButton(onClick = onRecolherTudo) {
                                Text(stringResource(R.string.outline_collapse_all), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (onExpandirTudo != null) {
                            TextButton(onClick = onExpandirTudo) {
                                Text(stringResource(R.string.outline_expand_all), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (onRecolherAteNivel != null) {
                            val niveisPresentes = itens.map { it.nivel }.distinct().sorted()
                            for (nivel in niveisPresentes) {
                                TextButton(
                                    onClick = { onRecolherAteNivel(nivel) },
                                    modifier = Modifier.testTag("sumario-recolher-nivel-$nivel"),
                                ) {
                                    Text("H$nivel", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
                LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
                    items(itens, key = { it.offset }) { item ->
                        val atual = itens.getOrNull(selecionado) == item
                        val cor = if (atual) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        val fundo = if (atual) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                        Text(
                            text = item.texto,
                            style = if (item.nivel == 1) MaterialTheme.typography.titleSmall
                                else if (item.nivel <= 3) MaterialTheme.typography.bodyMedium
                                else MaterialTheme.typography.bodySmall,
                            fontWeight = if (atual) FontWeight.SemiBold else FontWeight.Normal,
                            color = cor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(fundo)
                                .clickable { onItemClick(item) }
                                .heightIn(min = 44.dp)
                                .padding(
                                    start = 12.dp * item.nivel,
                                    end = 12.dp,
                                    top = 10.dp,
                                    bottom = 10.dp,
                                ),
                        )
                    }
                }
            }
        }
    }
}
