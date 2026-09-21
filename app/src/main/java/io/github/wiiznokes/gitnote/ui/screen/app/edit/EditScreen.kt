package io.github.wiiznokes.gitnote.ui.screen.app.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.wiiznokes.gitnote.R
import io.github.wiiznokes.gitnote.MyApp
import io.github.wiiznokes.gitnote.data.room.Note
import io.github.wiiznokes.gitnote.manager.ExtensionType
import io.github.wiiznokes.gitnote.manager.extensionType
import io.github.wiiznokes.gitnote.ui.component.RequestConfirmationDialog
import io.github.wiiznokes.gitnote.ui.component.SimpleIcon
import io.github.wiiznokes.gitnote.ui.component.markdown.ocorrencias
import io.github.wiiznokes.gitnote.ui.destination.EditParams
import io.github.wiiznokes.gitnote.ui.destination.resolveEditNote
import io.github.wiiznokes.gitnote.ui.model.EditType
import io.github.wiiznokes.gitnote.ui.viewmodel.edit.MarkDownVM
import io.github.wiiznokes.gitnote.ui.viewmodel.edit.TextVM
import io.github.wiiznokes.gitnote.ui.viewmodel.edit.newEditViewModel
import io.github.wiiznokes.gitnote.ui.viewmodel.edit.newMarkDownVM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext


private const val TAG = "EditScreen"


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    editParams: EditParams,
    onFinished: () -> Unit,
    onOpenNote: (Note, String?) -> Unit = { _, _ -> },
) {
    var openedNote by remember(editParams) { mutableStateOf<Note?>(null) }
    LaunchedEffect(editParams) {
        val note = withContext(Dispatchers.IO) {
            resolveEditNote(editParams) { relativePath ->
                MyApp.appModule.repoDatabase.repoDatabaseDao.noteByRelativePath(relativePath)
            }
        }
        if (note == null) {
            val path = (editParams as EditParams.Idle).relativePath
            val uiHelper = MyApp.appModule.uiHelper
            uiHelper.makeToast(uiHelper.getString(R.string.error_wikilink_not_found, path))
            onFinished()
        } else {
            openedNote = note
        }
    }
    val note = openedNote ?: return

    val extension = editParams.fileExtension()

    val vm = when (extensionType(extension.text)) {
        ExtensionType.Text -> newEditViewModel(editParams, note)
        ExtensionType.Markdown -> newMarkDownVM(editParams, note)
        null -> throw Exception("file extension not supported, but present in the database?? $extension")
    }

    val showShouldQuitDialog = rememberSaveable {
        mutableStateOf(false)
    }
    val showShouldOpenNoteDialog = rememberSaveable {
        mutableStateOf(false)
    }
    var pendingOpenNote by rememberSaveable { mutableStateOf<Note?>(null) }
    var pendingOpenSection by rememberSaveable { mutableStateOf<String?>(null) }
    var sumarioAberto by rememberSaveable { mutableStateOf(false) }
    var buscaAberta by rememberSaveable { mutableStateOf(false) }
    var termoBusca by rememberSaveable { mutableStateOf("") }
    var indiceBusca by rememberSaveable { mutableStateOf(0) }
    var resultadosBusca by remember { mutableStateOf<List<IntRange>>(emptyList()) }

    RequestConfirmationDialog(
        expanded = showShouldQuitDialog,
        text = stringResource(R.string.confirmation_quit_edit_dialog),
        onConfirmation = {
            vm.shouldSaveWhenQuitting = false
            onFinished()
        }
    )

    RequestConfirmationDialog(
        expanded = showShouldOpenNoteDialog,
        text = stringResource(R.string.confirmation_quit_edit_dialog),
        onConfirmation = {
            pendingOpenNote?.let { note ->
                vm.shouldSaveWhenQuitting = false
                pendingOpenNote = null
                onOpenNote(note, pendingOpenSection)
                pendingOpenSection = null
            }
        },
    )

    BackHandler {
        if (vm is MarkDownVM && vm.sugestaoWikilink.value.visivel) {
            vm.fecharSugestaoWikilink()
        } else if (buscaAberta) {
            buscaAberta = false
        } else if (sumarioAberto) {
            sumarioAberto = false
        } else if (vm.isPreviousNoteTheSame()) {
            vm.shouldSaveWhenQuitting = false
            onFinished()
        } else {
            showShouldQuitDialog.value = true
        }
    }

    val nameFocusRequester = remember { FocusRequester() }
    val textFocusRequester = remember { FocusRequester() }

    // tricks to request focus only one time
    var lastId: Boolean by rememberSaveable { mutableStateOf(false) }
    if (!lastId) {
        lastId = true
        LaunchedEffect(null) {
            if (vm.editType == EditType.Create) {
                nameFocusRequester.requestFocus()
            }
        }
    }

    val isReadOnlyModeActive =
        !vm.shouldForceNotReadOnlyMode.value && vm.prefs.isReadOnlyModeActive.getAsState().value

    val leituraMarkdown = vm is MarkDownVM && isReadOnlyModeActive
    val textoBusca = vm.content.value.text
    LaunchedEffect(buscaAberta, termoBusca, textoBusca, leituraMarkdown) {
        if (!buscaAberta || termoBusca.isEmpty() || leituraMarkdown) {
            resultadosBusca = emptyList()
        } else {
            delay(150)
            resultadosBusca = withContext(Dispatchers.Default) {
                ocorrencias(textoBusca, termoBusca)
            }
            indiceBusca = indiceBusca.coerceIn(0, (resultadosBusca.size - 1).coerceAtLeast(0))
        }
    }
    LaunchedEffect(resultadosBusca, indiceBusca, leituraMarkdown, buscaAberta) {
        if (buscaAberta && !leituraMarkdown) {
            resultadosBusca.getOrNull(indiceBusca)?.let(vm::selecionarOcorrencia)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            val backgroundColor = MaterialTheme.colorScheme.surfaceColorAtElevation(15.dp)
            Column {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backgroundColor
                ),
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (vm is MarkDownVM && vm.sugestaoWikilink.value.visivel) {
                                vm.fecharSugestaoWikilink()
                            } else if (vm.isPreviousNoteTheSame()) {
                                vm.shouldSaveWhenQuitting = false
                                onFinished()
                            } else {
                                showShouldQuitDialog.value = true
                            }
                        },
                    ) {
                        SimpleIcon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                        )
                    }
                },
                title = {

                    TextField(
                        textStyle = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(nameFocusRequester),
                        value = vm.name.value,
                        onValueChange = {
                            vm.name.value = it
                        },
                        readOnly = isReadOnlyModeActive,
                        singleLine = true,
                        placeholder = {
                            Text(text = stringResource(R.string.note_name))
                        },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.tertiary,
                            unfocusedTextColor = MaterialTheme.colorScheme.tertiary,
                            focusedContainerColor = backgroundColor,
                            unfocusedContainerColor = backgroundColor,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                textFocusRequester.requestFocus()
                            }
                        )
                    )
                },
                actions = {
                    IconButton(onClick = {
                        buscaAberta = !buscaAberta
                        if (buscaAberta) sumarioAberto = false
                    }) {
                        SimpleIcon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.search_in_note),
                        )
                    }
                    if (vm is MarkDownVM) {
                        IconButton(onClick = { sumarioAberto = !sumarioAberto }) {
                            SimpleIcon(
                                imageVector = Icons.AutoMirrored.Filled.List,
                                contentDescription = stringResource(R.string.note_outline),
                            )
                        }
                    }
                    IconButton(
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        onClick = {
                            vm.setReadOnlyMode(!isReadOnlyModeActive)
                        },
                    ) {
                        SimpleIcon(
                            imageVector = if (isReadOnlyModeActive) {
                                Icons.Default.Lock
                            } else {
                                Icons.Default.LockOpen
                            },
                        )
                    }
                }
            )
            if (buscaAberta) {
                BuscaNaNotaBarra(
                    termo = termoBusca,
                    indice = indiceBusca,
                    total = resultadosBusca.size,
                    onTermoChange = {
                        termoBusca = it
                        indiceBusca = 0
                        resultadosBusca = emptyList()
                    },
                    onAnterior = {
                        if (resultadosBusca.isNotEmpty()) {
                            indiceBusca = (indiceBusca - 1 + resultadosBusca.size) % resultadosBusca.size
                        }
                    },
                    onProximo = {
                        if (resultadosBusca.isNotEmpty()) {
                            indiceBusca = (indiceBusca + 1) % resultadosBusca.size
                        }
                    },
                    onFechar = { buscaAberta = false },
                )
            }
            }
        },
        floatingActionButton = {
            // bug: https://issuetracker.google.com/issues/224005027
            //AnimatedVisibility(visible = currentNoteFolderRelativePath.isNotEmpty()) {
            if (!isReadOnlyModeActive && vm.name.value.text.isNotEmpty()) {
                FloatingActionButton(
                    modifier = Modifier
                        .padding(bottom = bottomBarHeight),
                    containerColor = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(20.dp),
                    onClick = {
                        vm.save(onSuccess = onFinished)
                    }
                ) {
                    SimpleIcon(
                        imageVector = Icons.Default.Done,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {

            Box(
                modifier = Modifier.weight(1f)
            ) {

                val textContent = vm.content.value

                when (vm) {
                    is MarkDownVM -> {
                        MarkDownContent(
                            vm = vm,
                            textFocusRequester = textFocusRequester,
                            onFinished = onFinished,
                            onOpenNote = { note, section ->
                                if (vm.isPreviousNoteTheSame()) {
                                    onOpenNote(note, section)
                                } else {
                                    pendingOpenNote = note
                                    pendingOpenSection = section
                                    showShouldOpenNoteDialog.value = true
                                }
                            },
                            isReadOnlyModeActive = isReadOnlyModeActive,
                            textContent = textContent,
                            sumarioAberto = sumarioAberto,
                            onSumarioAbertoChange = { sumarioAberto = it },
                            termoBusca = if (buscaAberta && leituraMarkdown) termoBusca else "",
                            ocorrenciaBusca = resultadosBusca.getOrNull(indiceBusca),
                            onResultadosBusca = { encontrados ->
                                resultadosBusca = encontrados
                                indiceBusca = indiceBusca.coerceIn(0, (encontrados.size - 1).coerceAtLeast(0))
                            },
                        )
                    }

                    else -> {
                        GenericTextField(
                            vm = vm,
                            textFocusRequester = textFocusRequester,
                            onFinished = onFinished,
                            isReadOnlyModeActive = isReadOnlyModeActive,
                            textContent = textContent
                        )
                    }
                }
            }

            when (vm) {
                is MarkDownVM -> {
                    val textFormatExpanded =
                        rememberSaveable(isReadOnlyModeActive) { mutableStateOf(false) }

                    if (textFormatExpanded.value) {
                        TextFormatRow(vm = vm, textFormatExpanded = textFormatExpanded)
                    } else {
                        DefaultRow(
                            vm = vm,
                            isReadOnlyModeActive = isReadOnlyModeActive,
                            leftContent = {
                                SmallButton(
                                    onClick = {
                                        textFormatExpanded.value = true
                                    },
                                    enabled = !isReadOnlyModeActive,
                                    imageVector = Icons.Default.TextFormat,
                                    contentDescription = "text format"
                                )
                            }
                        )
                    }

                }

                else -> {
                    DefaultRow(
                        vm = vm,
                        isReadOnlyModeActive = isReadOnlyModeActive,
                    )
                }
            }
        }


    }
}

@Composable
fun GenericTextField(
    vm: TextVM,
    textFocusRequester: FocusRequester,
    onFinished: () -> Unit,
    isReadOnlyModeActive: Boolean = false,
    textContent: TextFieldValue,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    TextField(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(textFocusRequester),
        value = textContent,
        onValueChange = { vm.onValueChange(it) },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.background,
            unfocusedContainerColor = MaterialTheme.colorScheme.background,
            focusedTextColor = MaterialTheme.colorScheme.onBackground,
            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        keyboardActions = KeyboardActions(
            onDone = { vm.save(onSuccess = onFinished) }
        ),
        visualTransformation = visualTransformation,
        readOnly = isReadOnlyModeActive
    )


}
