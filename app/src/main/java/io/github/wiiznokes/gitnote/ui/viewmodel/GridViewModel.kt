package io.github.wiiznokes.gitnote.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import io.github.wiiznokes.gitnote.MyApp
import io.github.wiiznokes.gitnote.R
import io.github.wiiznokes.gitnote.data.AppPreferences
import io.github.wiiznokes.gitnote.data.room.Abertura
import io.github.wiiznokes.gitnote.data.room.Note
import io.github.wiiznokes.gitnote.data.room.NoteFolder
import io.github.wiiznokes.gitnote.data.room.RepoDatabase
import io.github.wiiznokes.gitnote.ui.model.SortOrder
import io.github.wiiznokes.gitnote.helper.NameValidation
import io.github.wiiznokes.gitnote.manager.StorageManager
import io.github.wiiznokes.gitnote.ui.model.FileExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GridViewModel : ViewModel() {

    private data class CondicoesDaGrade(
        val pasta: String,
        val ordem: SortOrder,
        val query: String,
        val teto: Int?,
    )

    companion object {
        private const val TAG = "GridViewModel"
    }


    private val storageManager: StorageManager = MyApp.appModule.storageManager

    val prefs: AppPreferences = MyApp.appModule.appPreferences
    private val db: RepoDatabase = MyApp.appModule.repoDatabase
    private val dao = db.repoDatabaseDao
    private val historicoDao = MyApp.appModule.historicoDatabase.dao
    val uiHelper = MyApp.appModule.uiHelper

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _teto = MutableStateFlow<Int?>(TetoDeNotas.PRIMEIRA_LEVA)

    fun mostrarTodas() {
        _teto.value = null
    }

    val syncState = storageManager.syncState

    fun consumeOkSyncState() {
        viewModelScope.launch {
            storageManager.consumeOkSyncState()
        }
    }

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()


    private val _currentNoteFolderRelativePath = MutableStateFlow(
        PastaInicial.escolher(
            rememberLastOpenedFolder = prefs.rememberLastOpenedFolder.getBlocking(),
            lastOpenedFolder = prefs.lastOpenedFolder.getBlocking(),
            pastaPadrao = prefs.pastaPadrao.getBlocking(),
        )
    )
    val currentNoteFolderRelativePath: StateFlow<String>
        get() = _currentNoteFolderRelativePath.asStateFlow()


    private val _selectedNotes: MutableStateFlow<Set<String>> = MutableStateFlow(emptySet())

    val selectedNotes: StateFlow<Set<String>>
        get() = _selectedNotes.asStateFlow()


    init {
        Log.d(TAG, "init")

        val pastaInicial = _currentNoteFolderRelativePath.value
        if (pastaInicial != "") {
            viewModelScope.launch {
                if (!dao.isFolderExist(pastaInicial)) {
                    Log.d(TAG, "pasta inicial \"$pastaInicial\" nao existe, caindo para a raiz")
                    _currentNoteFolderRelativePath.emit("")
                    uiHelper.makeToast(uiHelper.getString(R.string.default_startup_folder_not_found))
                }
            }
        }
    }

    suspend fun refreshSelectedNotes() {
        selectedNotes.value.filter { relativePath ->
            dao.isNoteExist(relativePath)
        }.toSet().let { newSelectedNotes ->
            _selectedNotes.emit(newSelectedNotes)
        }
    }

    /**
     * O card/linha da grade so guarda [io.github.wiiznokes.gitnote.ui.model.GridRow]
     * (sem conteudo, Fase B do handoff 10). Quem precisa da nota inteira — abrir no
     * editor ou apagar — busca sob demanda aqui.
     */
    fun abrirNota(relativePath: String, onNoteLoaded: (Note) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            dao.noteByRelativePath(relativePath)?.let { note ->
                val agora = System.currentTimeMillis()
                historicoDao.upsert(Abertura(relativePath, agora))
                dao.updateLastOpened(relativePath, agora)
                withContext(Dispatchers.Main) {
                    onNoteLoaded(note.copy(lastOpenedTimeMillis = agora))
                }
            }
        }
    }

    fun refresh() {
        CoroutineScope(Dispatchers.IO).launch {
            _isRefreshing.emit(true)
            storageManager.updateDatabaseAndRepo()
            refreshSelectedNotes()
            _isRefreshing.emit(false)
        }
    }

    fun updateSettings(f: suspend AppPreferences.() -> Unit) {
        viewModelScope.launch { prefs.f() }
    }

    fun search(query: String) {
        viewModelScope.launch {
            _query.emit(query)
        }
    }

    fun clearQuery() {
        viewModelScope.launch {
            _query.emit("")
        }
    }

    fun openFolder(relativePath: String) {
        viewModelScope.launch {
            _teto.emit(TetoDeNotas.PRIMEIRA_LEVA)
            _currentNoteFolderRelativePath.emit(relativePath)
            prefs.lastOpenedFolder.update(relativePath)
        }
    }

    fun createNoteFolder(relativeParentPath: String, name: String): Boolean {
        if (!NameValidation.check(name)) {
            uiHelper.makeToast(uiHelper.getString(R.string.error_invalid_name))
            return false
        }

        val relativePath = "$relativeParentPath/$name"

        val noteFolder = NoteFolder.new(
            relativePath = relativePath
        )

        if (noteFolder.toFolderFs(prefs.repoPathBlocking()).exist()) {
            uiHelper.makeToast(uiHelper.getString(R.string.error_folder_already_exist))
            return false
        }

        CoroutineScope(Dispatchers.IO).launch {
            storageManager.createNoteFolder(noteFolder)
        }

        return true
    }


    /**
     * @param add true if the note must be selected, false otherwise
     */
    fun selectNote(relativePath: String, add: Boolean) = viewModelScope.launch {
        if (add) {
            selectedNotes.value.plus(relativePath)
        } else {
            selectedNotes.value.minus(relativePath)
        }.let {
            _selectedNotes.emit(it)
        }
    }

    fun unselectAllNotes() = viewModelScope.launch {
        _selectedNotes.emit(emptySet())
    }

    fun deleteSelectedNotes() {
        CoroutineScope(Dispatchers.IO).launch {
            val currentSelectedPaths = selectedNotes.value
            unselectAllNotes()
            val notes = currentSelectedPaths.mapNotNull { dao.noteByRelativePath(it) }
            storageManager.deleteNotes(notes)
        }
    }

    fun deleteNote(relativePath: String) {
        CoroutineScope(Dispatchers.IO).launch {
            dao.noteByRelativePath(relativePath)?.let { note ->
                storageManager.deleteNote(note)
            }
        }
    }

    fun deleteFolder(noteFolder: NoteFolder) {
        CoroutineScope(Dispatchers.IO).launch {
            storageManager.deleteNoteFolder(noteFolder)
        }
    }


    fun defaultNewNote(): Note {

        val defaultName = query.value.let {
            if (NameValidation.check(it)) {
                it
            } else ""
        }

        val defaultExtension = FileExtension.match(prefs.defaultExtension.getBlocking())
        val defaultFullName = "$defaultName.${defaultExtension.text}"

        val currentNoteFolderRelativePath = currentNoteFolderRelativePath.value

        val parent = if (currentNoteFolderRelativePath == "") {
            prefs.defaultPathForNewNote.getBlocking()
        } else currentNoteFolderRelativePath

        return Note.new(
            relativePath = "$parent/$defaultFullName",
        )
    }


    @OptIn(ExperimentalCoroutinesApi::class)
    val gridNotes = combine(
        currentNoteFolderRelativePath,
        prefs.sortOrder.getFlow(),
        query,
        _teto,
    ) { currentNoteFolderRelativePath, sortOrder, query, teto ->
        CondicoesDaGrade(currentNoteFolderRelativePath, sortOrder, query, teto)
    }.flatMapLatest { condicoes ->

        Pager(
            config = PagingConfig(pageSize = 50),
            pagingSourceFactory = {
                if (condicoes.query.isEmpty()) {
                    dao.gridNotes(
                        condicoes.pasta,
                        condicoes.ordem,
                        TetoDeNotas.paraConsulta(condicoes.query, condicoes.teto),
                    )
                } else {
                    dao.gridNotesWithQuery(condicoes.pasta, condicoes.ordem, condicoes.query)
                }
            }
        ).flow.cachedIn(viewModelScope)
    }.combine(selectedNotes) { gridNotes, selectedNotes ->
        gridNotes.map { gridRow ->
            gridRow.copy(
                selected = selectedNotes.contains(gridRow.relativePath)
            )
        }
    }.stateIn(
        CoroutineScope(Dispatchers.IO), SharingStarted.WhileSubscribed(5000), PagingData.empty()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val totalParaMostrarTodas = combine(currentNoteFolderRelativePath, query, _teto) { pasta, busca, teto ->
        Triple(pasta, busca, teto)
    }.flatMapLatest { (pasta, busca, teto) ->
        if (busca.isNotEmpty() || teto == null) {
            flowOf(null)
        } else {
            dao.countNotesInFolder(pasta).map { total ->
                TetoDeNotas.totalParaRodape(busca, teto, total)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // todo: use pager
    @OptIn(ExperimentalCoroutinesApi::class)
    val drawerFolders = combine(
        currentNoteFolderRelativePath,
        prefs.sortOrderFolder.getFlow(),
    ) { currentNoteFolderRelativePath, sortOrder ->
        Pair(currentNoteFolderRelativePath, sortOrder)
    }.flatMapLatest { pair ->
        val (currentNoteFolderRelativePath, sortOrder) = pair
        dao.drawerFolders(currentNoteFolderRelativePath, sortOrder)
    }.stateIn(
        CoroutineScope(Dispatchers.IO), SharingStarted.WhileSubscribed(5000), emptyList()
    )

    fun reloadDatabase() {
        CoroutineScope(Dispatchers.IO).launch {
            val res = storageManager.updateDatabase(force = true)
            res.onFailure {
                uiHelper.makeToast("$it")
            }
        }
    }
}
