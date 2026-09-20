package io.github.wiiznokes.gitnote.ui.screen.app.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import io.github.wiiznokes.gitnote.data.room.Note
import io.github.wiiznokes.gitnote.R
import io.github.wiiznokes.gitnote.ui.model.EditType
import io.github.wiiznokes.gitnote.ui.model.GridRow
import io.github.wiiznokes.gitnote.ui.viewmodel.GridViewModel
import java.text.DateFormat
import java.util.Date

@Composable
internal fun NoteListView(
    gridNotes: LazyPagingItems<GridRow>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    selectedNotes: Set<String>,
    showFullPathOfNotes: Boolean,
    onEditClick: (Note, EditType) -> Unit,
    vm: GridViewModel,
    totalParaMostrarTodas: Int?,
    pastaAtual: String,
    itensDeNavegacao: List<ItemDeNavegacao>,
    mostrarVazio: Boolean,
) {

    LazyColumn(
        modifier = modifier,
        state = listState
    ) {
        item {
            Spacer(modifier = Modifier.height(topSpacerHeight))
        }

        // Fase K.1: as mesmas pastas da grade escalonada, aqui como linhas comuns.
        items(
            count = itensDeNavegacao.size,
            key = { indice -> itensDeNavegacao[indice].chave },
        ) { indice ->
            LinhaDeNavegacao(
                item = itensDeNavegacao[indice],
                onAbrirPasta = vm::openFolder,
            )
        }

        if (mostrarVazio) {
            item {
                GradeVazia()
            }
        }

        items(
            count = gridNotes.itemCount,
            key = gridNotes.itemKey { it.id }
        ) { index ->
            val gridNote = gridNotes[index] ?: return@items
            NoteListRow(
                gridNote = gridNote,
                vm = vm,
                onEditClick = onEditClick,
                selectedNotes = selectedNotes,
                showFullPathOfNotes = showFullPathOfNotes,
                pastaAtual = pastaAtual,
            )
        }

        if (totalParaMostrarTodas != null) {
            item {
                Button(onClick = vm::mostrarTodas) {
                    Text(stringResource(R.string.show_all_notes, totalParaMostrarTodas))
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(topBarHeight + 10.dp))
        }
    }
}

@Composable
private fun NoteListRow(
    gridNote: GridRow,
    vm: GridViewModel,
    onEditClick: (Note, EditType) -> Unit,
    selectedNotes: Set<String>,
    showFullPathOfNotes: Boolean,
    pastaAtual: String,
) {
    val dropDownExpanded = remember { mutableStateOf(false) }
    val clickPosition = remember { mutableStateOf(Offset.Zero) }

    val formattedDate = remember(gridNote.lastModifiedTimeMillis) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(gridNote.lastModifiedTimeMillis))
    }

    // Fase K.2: listagem recursiva — o caminho a partir da pasta aberta ja diz de que
    // subpasta a nota veio, sem repetir o caminho inteiro em toda linha.
    val title = if (showFullPathOfNotes || !gridNote.isUnique) {
        gridNote.relativePath
    } else {
        gridNote.tituloRelativoA(pastaAtual)
    }

    val rowBackground =
        if (gridNote.selected) MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
        else MaterialTheme.colorScheme.surface

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBackground)
            .combinedClickable(
                onLongClick = { dropDownExpanded.value = true },
                onClick = {
                    if (selectedNotes.isEmpty()) {
                        vm.abrirNota(gridNote.relativePath) { note ->
                            onEditClick(note, EditType.Update)
                        }
                    } else {
                        vm.selectNote(gridNote.relativePath, add = !gridNote.selected)
                    }
                }
            )
            .pointerInteropFilter {
                clickPosition.value = Offset(it.x, it.y)
                false
            }
    ) {
        Box {
            NoteActionsDropdown(
                vm = vm,
                gridNote = gridNote,
                selectedNotes = selectedNotes,
                dropDownExpanded = dropDownExpanded,
                clickPosition = clickPosition
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )

                Column(
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = formattedDate,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(80.dp)
        )
    }
}
