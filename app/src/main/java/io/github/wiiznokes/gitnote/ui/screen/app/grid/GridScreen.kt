package io.github.wiiznokes.gitnote.ui.screen.app.grid

import android.annotation.SuppressLint
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import io.github.wiiznokes.gitnote.R
import io.github.wiiznokes.gitnote.data.room.Note
import io.github.wiiznokes.gitnote.ui.component.CustomDropDown
import io.github.wiiznokes.gitnote.ui.component.CustomDropDownModel
import io.github.wiiznokes.gitnote.ui.model.EditType
import io.github.wiiznokes.gitnote.ui.model.GridRow
import io.github.wiiznokes.gitnote.ui.model.NoteViewType
import io.github.wiiznokes.gitnote.ui.screen.app.DrawerFolderModel
import io.github.wiiznokes.gitnote.ui.screen.app.DrawerScreen
import io.github.wiiznokes.gitnote.ui.viewmodel.GridViewModel
import java.text.DateFormat
import java.util.Date


private const val TAG = "GridScreen"

private const val maxOffset = -500f
internal val topBarHeight = 80.dp

internal val topSpacerHeight = topBarHeight + 40.dp + 15.dp

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun GridScreen(
    onSettingsClick: () -> Unit,
    onEditClick: (Note, EditType) -> Unit,
) {

    val vm: GridViewModel = viewModel()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // Fase K.1: as pastas sao as MESMAS que a gaveta ja usa — nenhuma consulta nova.
    // Sobem para ca so para alimentar tambem a grade.
    val pastaAtual by vm.currentNoteFolderRelativePath.collectAsState()
    val pastas by vm.drawerFolders.collectAsState()

    ModalNavigationDrawer(drawerState = drawerState, drawerContent = {
        ModalDrawerSheet {
            val pastaPadrao by vm.prefs.pastaPadrao.getAsState()
            DrawerScreen(
                drawerState = drawerState,
                currentNoteFolderRelativePath = pastaAtual,
                drawerFolders = pastas,
                openFolder = vm::openFolder,
                deleteFolder = vm::deleteFolder,
                createNoteFolder = vm::createNoteFolder,
                pastaPadrao = pastaPadrao,
            )
        }
    }) {

        val selectedNotes by vm.selectedNotes.collectAsState()

        if (selectedNotes.isNotEmpty()) {
            BackHandler {
                vm.unselectAllNotes()
            }
        }

        val noteViewType by vm.prefs.noteViewType.getAsState()

        val searchFocusRequester = remember { FocusRequester() }

        val offset = remember { mutableFloatStateOf(0f) }

        Scaffold(
            contentWindowInsets = WindowInsets.safeContent,
            containerColor = MaterialTheme.colorScheme.background,
            floatingActionButton = {

                if (selectedNotes.isEmpty()) {
                    FloatingActionButtons(
                        vm = vm,
                        offset = offset.floatValue,
                        onEditClick = onEditClick,
                    )
                }

            }) { padding ->

            val nestedScrollConnection = rememberNestedScrollConnection(
                offset = offset,
            )


            GridView(
                vm = vm,
                onEditClick = onEditClick,
                selectedNotes = selectedNotes,
                nestedScrollConnection = nestedScrollConnection,
                padding = padding,
                noteViewType = noteViewType,
                pastaAtual = pastaAtual,
                pastas = pastas,
            )

            TopBar(
                offset = offset.floatValue,
                selectedNotesNumber = selectedNotes.size,
                drawerState = drawerState,
                onSettingsClick = onSettingsClick,
                searchFocusRequester = searchFocusRequester,
                padding = padding,
                onReloadDatabase = {
                    vm.reloadDatabase()
                },
                query = vm.query.collectAsState().value,
                clearQuery = vm::clearQuery,
                search = vm::search,
                noteViewType = vm.prefs.noteViewType.getAsState().value,
                syncState = vm.syncState.collectAsState().value,
                consumeOkSyncState = vm::consumeOkSyncState,
                isReadOnlyModeActive = vm.prefs.isReadOnlyModeActive.getAsState().value,
                updateSettings = vm::updateSettings,
                unselectAllNotes = vm::unselectAllNotes,
                deleteSelectedNotes = vm::deleteSelectedNotes,
            )

        }
    }
}


@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterialApi::class,
    ExperimentalComposeUiApi::class
)
@Composable
private fun GridView(
    vm: GridViewModel,
    nestedScrollConnection: NestedScrollConnection,
    onEditClick: (Note, EditType) -> Unit,
    selectedNotes: Set<String>,
    padding: PaddingValues,
    noteViewType: NoteViewType,
    pastaAtual: String,
    pastas: List<DrawerFolderModel>,
) {
    val gridNotes = vm.gridNotes.collectAsLazyPagingItems()
    val query = vm.query.collectAsState()
    val totalParaMostrarTodas by vm.totalParaMostrarTodas.collectAsState()

    val itensDeNavegacao = remember(pastaAtual, query.value, pastas) {
        PastasNaGrade.itens(pastaAtual, query.value, pastas)
    }

    val mostrarVazio = PastasNaGrade.mostrarVazio(
        itens = itensDeNavegacao,
        quantidadeDeNotas = gridNotes.itemCount,
        notasCarregando = gridNotes.loadState.refresh is LoadState.Loading,
    )


    val isRefreshing by vm.isRefreshing.collectAsStateWithLifecycle()
    val pullRefreshState = rememberPullRefreshState(isRefreshing, {
        Log.d(TAG, "pull refresh")
        vm.refresh()
    })

    val showFullPathOfNotes = vm.prefs.showFullPathOfNotes.getAsState()

    Box {

        // todo: scroll even when there is nothing to scroll
        // todo: add scroll bar

        val commonModifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
            .nestedScroll(nestedScrollConnection)

        when (noteViewType) {
            NoteViewType.Grid -> {
                val gridState = rememberLazyStaggeredGridState()

                LaunchedEffect(query.value) {
                    gridState.animateScrollToItem(index = 0)
                }

                GridNotesView(
                    gridNotes = gridNotes,
                    gridState = gridState,
                    modifier = commonModifier,
                    selectedNotes = selectedNotes,
                    showFullPathOfNotes = showFullPathOfNotes.value,
                    onEditClick = onEditClick,
                    vm = vm,
                    totalParaMostrarTodas = totalParaMostrarTodas,
                    pastaAtual = pastaAtual,
                    itensDeNavegacao = itensDeNavegacao,
                    mostrarVazio = mostrarVazio,
                )
            }

            NoteViewType.List -> {
                val listState = rememberLazyListState()

                LaunchedEffect(query.value) {
                    listState.animateScrollToItem(index = 0)
                }

                NoteListView(
                    gridNotes = gridNotes,
                    listState = listState,
                    modifier = commonModifier,
                    selectedNotes = selectedNotes,
                    showFullPathOfNotes = showFullPathOfNotes.value,
                    onEditClick = onEditClick,
                    vm = vm,
                    totalParaMostrarTodas = totalParaMostrarTodas,
                    pastaAtual = pastaAtual,
                    itensDeNavegacao = itensDeNavegacao,
                    mostrarVazio = mostrarVazio,
                )
            }
        }

        // fix me: https://stackoverflow.com/questions/74594418/pullrefreshindicator-overlaps-with-scrollabletabrow
        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = topBarHeight + padding.calculateTopPadding()),
            backgroundColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            scale = true
        )
    }

}


@Composable
private fun GridNotesView(
    gridNotes: LazyPagingItems<GridRow>,
    gridState: LazyStaggeredGridState,
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


    val noteMinWidth = vm.prefs.noteMinWidth.getAsState()
    val showFullNoteHeight = vm.prefs.showFullNoteHeight.getAsState()

    LazyVerticalStaggeredGrid(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 3.dp),
        columns = StaggeredGridCells.Adaptive(noteMinWidth.value.size.dp),
        state = gridState
    ) {
        item(span = StaggeredGridItemSpan.FullLine) {
            Spacer(modifier = Modifier.height(topSpacerHeight))
        }

        // Fase K.1: pastas acima das notas, ocupando a linha inteira. O teto de 10 da
        // Fase D e das notas; pasta nao entra nessa conta.
        items(
            count = itensDeNavegacao.size,
            key = { indice -> itensDeNavegacao[indice].chave },
            span = { StaggeredGridItemSpan.FullLine },
        ) { indice ->
            LinhaDeNavegacao(
                item = itensDeNavegacao[indice],
                onAbrirPasta = vm::openFolder,
            )
        }

        if (mostrarVazio) {
            item(span = StaggeredGridItemSpan.FullLine) {
                GradeVazia()
            }
        }

        items(
            count = gridNotes.itemCount,
            key = gridNotes.itemKey { it.id }
        ) { index ->
            val gridRow = gridNotes[index] ?: return@items

            NoteCard(
                gridNote = gridRow,
                vm = vm,
                onEditClick = onEditClick,
                selectedNotes = selectedNotes,
                showFullPathOfNotes = showFullPathOfNotes,
                showFullNoteHeight = showFullNoteHeight.value,
                pastaAtual = pastaAtual,
                modifier = Modifier.padding(3.dp)
            )
        }

        if (totalParaMostrarTodas != null) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Button(onClick = vm::mostrarTodas) {
                    Text(stringResource(R.string.show_all_notes, totalParaMostrarTodas))
                }
            }
        }

        item(span = StaggeredGridItemSpan.FullLine) {
            Spacer(modifier = Modifier.height(topBarHeight + 10.dp))
        }
    }
}

@Composable
private fun NoteCard(
    gridNote: GridRow,
    vm: GridViewModel,
    onEditClick: (Note, EditType) -> Unit,
    selectedNotes: Set<String>,
    showFullPathOfNotes: Boolean,
    showFullNoteHeight: Boolean,
    pastaAtual: String,
    modifier: Modifier = Modifier,
) {
    val dropDownExpanded = remember {
        mutableStateOf(false)
    }

    val clickPosition = remember {
        mutableStateOf(Offset.Zero)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = if (dropDownExpanded.value) {
            BorderStroke(
                width = 2.dp, color = MaterialTheme.colorScheme.primary
            )
        } else if (gridNote.selected) {
            BorderStroke(
                width = 2.dp, color = MaterialTheme.colorScheme.onSurface
            )
        } else {
            BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(1000.dp)
            )
        },
        modifier = modifier
            .sizeIn(
                maxHeight = if (showFullNoteHeight) Dp.Unspecified else 500.dp
            )
            .combinedClickable(onLongClick = {
                dropDownExpanded.value = true
            }, onClick = {
                if (selectedNotes.isEmpty()) {
                    vm.abrirNota(gridNote.relativePath) { note ->
                        onEditClick(note, EditType.Update)
                    }
                } else {
                    vm.selectNote(
                        gridNote.relativePath, add = !gridNote.selected
                    )
                }
            })
            .pointerInteropFilter {
                clickPosition.value = Offset(it.x, it.y)
                false
            },
    ) {
        Box {

            NoteActionsDropdown(
                vm = vm,
                gridNote = gridNote,
                selectedNotes = selectedNotes,
                dropDownExpanded = dropDownExpanded,
                clickPosition = clickPosition
            )

            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.Start,
            ) {
                // Fase K.2: a listagem voltou a ser recursiva, entao o titulo mostra o
                // caminho a partir da pasta aberta — nota da propria pasta continua so
                // com o nome. Sem isso, duas notas de mesmo nome em subpastas
                // diferentes ficariam identicas na tela.
                val title = if (showFullPathOfNotes || !gridNote.isUnique) {
                    gridNote.relativePath
                } else {
                    gridNote.tituloRelativoA(pastaAtual)
                }
                Text(
                    text = title,
                    modifier = Modifier.padding(bottom = 6.dp),
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.tertiary
                )

                val formattedDate = remember(gridNote.lastModifiedTimeMillis) {
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(Date(gridNote.lastModifiedTimeMillis))
                }
                Text(
                    text = formattedDate,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun NoteActionsDropdown(
    vm: GridViewModel,
    gridNote: GridRow,
    selectedNotes: Set<String>,
    dropDownExpanded: MutableState<Boolean>,
    clickPosition: MutableState<Offset>,
) {

    // need this box for clickPosition
    Box {
        CustomDropDown(
            expanded = dropDownExpanded,
            shape = MaterialTheme.shapes.medium,
            options = listOf(
                CustomDropDownModel(
                    text = stringResource(R.string.delete_this_note),
                    onClick = { vm.deleteNote(gridNote.relativePath) }),
                if (selectedNotes.isEmpty()) CustomDropDownModel(
                    text = stringResource(R.string.select_multiple_notes),
                    onClick = { vm.selectNote(gridNote.relativePath, true) }) else null,
            ),
            clickPosition = clickPosition
        )
    }
}

// https://stackoverflow.com/questions/73079388/android-jetpack-compose-keyboard-not-close
// https://medium.com/@debdut.saha.1/top-app-bar-animation-using-nestedscrollconnection-like-facebook-jetpack-compose-b446c109ee52
// todo: fix scroll is blocked when the full size of the grid is the screen,
//  the stretching will cause tbe offset to not change
@Composable
private fun rememberNestedScrollConnection(
    offset: MutableFloatState,
): NestedScrollConnection {


    val keyboardController = LocalSoftwareKeyboardController.current

    return remember {
        var shouldBlock = false

        object : NestedScrollConnection {
            fun calculateOffset(delta: Float): Offset {
                offset.floatValue = (offset.floatValue + delta).coerceIn(maxOffset, 0f)
                //Log.d(TAG, "calculateOffset(newOffset: ${offset.floatValue}, delta: $delta)")
                return Offset.Zero
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                //Log.d(TAG, "onPreScroll(available: ${available.y})")
                if (!shouldBlock) keyboardController?.hide()

                return calculateOffset(available.y)
            }

            override fun onPostScroll(
                consumed: Offset, available: Offset, source: NestedScrollSource
            ): Offset {
                //Log.d(TAG, "onPostScroll(consumed: ${consumed.y}, available: ${available.y})")
                return calculateOffset(available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                shouldBlock = true
                return super.onPreFling(available)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                shouldBlock = false
                return super.onPostFling(consumed, available)
            }

        }
    }
}
