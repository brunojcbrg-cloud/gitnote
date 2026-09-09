package io.github.wiiznokes.gitnote.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.wiiznokes.gitnote.MyApp
import io.github.wiiznokes.gitnote.R
import io.github.wiiznokes.gitnote.data.AppPreferences
import io.github.wiiznokes.gitnote.data.room.Note
import io.github.wiiznokes.gitnote.data.room.NoteFolder
import io.github.wiiznokes.gitnote.flashcard.ConcurrentNoteChangeException
import io.github.wiiznokes.gitnote.flashcard.FlashcardParser
import io.github.wiiznokes.gitnote.flashcard.FlashcardQueue
import io.github.wiiznokes.gitnote.flashcard.FlashcardRating
import io.github.wiiznokes.gitnote.flashcard.ReviewCard
import io.github.wiiznokes.gitnote.flashcard.ScheduledReview
import io.github.wiiznokes.gitnote.flashcard.Sm2OsrScheduler
import io.github.wiiznokes.gitnote.ui.model.FileExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate

private const val TAG = "FlashcardViewModel"

data class FlashcardDeckSummary(
    val path: String,
    val availableCount: Int,
    val totalCount: Int,
) {
    val depth: Int
        get() = if (path.isEmpty()) 0 else path.count { it == '/' } + 1

    val displayName: String
        get() = path.substringAfterLast('/').ifEmpty { "#flashcards" }
}

data class FlashcardReviewState(
    val deckPath: String,
    val queue: List<ReviewCard<Note>>,
    val completed: Int,
    val total: Int,
    val revealed: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val previews: List<ScheduledReview> = emptyList(),
) {
    val current: ReviewCard<Note>?
        get() = queue.firstOrNull()
}

class FlashcardViewModel : ViewModel() {
    val prefs: AppPreferences = MyApp.appModule.appPreferences
    private val dao = MyApp.appModule.repoDatabase.repoDatabaseDao
    private val storageManager = MyApp.appModule.storageManager
    private val uiHelper = MyApp.appModule.uiHelper

    private var allReviewCards: List<ReviewCard<Note>> = emptyList()
    private val queueEngine = FlashcardQueue<Note>(
        noteKey = Note::relativePath,
        noteContent = Note::content,
        noteTitle = Note::nameWithoutExtension,
        withContent = { note, content ->
            note.copy(content = content, lastModifiedTimeMillis = Instant.now().toEpochMilli())
        },
    )
    private val initialLoad: Job
    private var hasPendingReviewChanges = false
    private var flushWhenReviewFinishesSaving = false

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _availableCount = MutableStateFlow(0)
    val availableCount: StateFlow<Int> = _availableCount.asStateFlow()

    private val _folders = MutableStateFlow<List<NoteFolder>>(emptyList())
    val folders: StateFlow<List<NoteFolder>> = _folders.asStateFlow()

    private val _selectedFolder = MutableStateFlow<String?>(null)
    val selectedFolder: StateFlow<String?> = _selectedFolder.asStateFlow()

    private val _decks = MutableStateFlow<List<FlashcardDeckSummary>>(emptyList())
    val decks: StateFlow<List<FlashcardDeckSummary>> = _decks.asStateFlow()

    private val _reviewState = MutableStateFlow<FlashcardReviewState?>(null)
    val reviewState: StateFlow<FlashcardReviewState?> = _reviewState.asStateFlow()

    init {
        initialLoad = viewModelScope.launch { loadCards() }
    }

    fun selectFolder(relativePath: String?) {
        _selectedFolder.value = relativePath
        rebuildDecks()
    }

    fun startReview(deckPath: String, folderPath: String?) {
        viewModelScope.launch {
            initialLoad.join()
            flushWhenReviewFinishesSaving = false
            _selectedFolder.value = folderPath
            val queue = filteredCards(folderPath)
                .filter { reviewCard -> reviewCard.card.decks.any { it.inDeck(deckPath) } }
                .filter { it.card.isAvailable(LocalDate.now()) }
                .let(queueEngine::orderForReview)
            _reviewState.value = FlashcardReviewState(
                deckPath = deckPath,
                queue = queue,
                completed = 0,
                total = queue.size,
                previews = previewsFor(queue.firstOrNull()),
            )
        }
    }

    fun revealAnswer() {
        _reviewState.value = _reviewState.value?.copy(revealed = true, error = null)
    }

    fun review(rating: FlashcardRating) {
        val state = _reviewState.value ?: return
        val current = state.current ?: return
        if (state.saving || !state.revealed) return

        viewModelScope.launch {
            _reviewState.value = state.copy(saving = true, error = null)
            val schedule = state.previews.firstOrNull { it.rating == rating }?.schedule
            if (schedule == null) {
                failReview(
                    state,
                    IllegalStateException(uiHelper.getString(R.string.flashcard_schedule_unavailable)),
                )
                return@launch
            }
            val transition = runCatching {
                queueEngine.review(
                    queue = state.queue,
                    expectedCardCount = allReviewCards.count {
                        it.note.relativePath == current.note.relativePath
                    },
                    schedule = schedule,
                    requeueCurrent = rating == FlashcardRating.AGAIN,
                )
            }.getOrElse { error ->
                failReview(state, error)
                return@launch
            }
            val newNote = transition.updatedNote
            if (newNote == null) {
                val mismatch = checkNotNull(transition.cardCountMismatch)
                Log.e(
                    TAG,
                    "Dropping remaining cards for ${mismatch.noteKey}: " +
                        "expected ${mismatch.expected}, reparsed ${mismatch.actual}",
                )
                val message = uiHelper.getString(R.string.flashcard_card_count_changed)
                uiHelper.makeToast(message)
                _reviewState.value = state.copy(
                    queue = transition.queue,
                    saving = false,
                    error = message,
                    previews = previewsFor(transition.queue.firstOrNull()),
                )
                if (transition.queue.isEmpty() || flushWhenReviewFinishesSaving) {
                    flushPendingReviewChanges()
                }
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                storageManager.updateNoteLocalOnly(newNote, current.note)
            }
            val saveError = result.exceptionOrNull()
            if (saveError != null) {
                failReview(state, saveError)
                return@launch
            }
            hasPendingReviewChanges = true

            allReviewCards = allReviewCards.filter {
                it.note.relativePath != newNote.relativePath
            } + transition.cardsInUpdatedNote
            rebuildDecks()

            _reviewState.value = state.copy(
                queue = transition.queue,
                completed = state.completed + if (rating == FlashcardRating.AGAIN) 0 else 1,
                revealed = false,
                saving = false,
                error = null,
                previews = previewsFor(transition.queue.firstOrNull()),
            )
            if (transition.queue.isEmpty() || flushWhenReviewFinishesSaving) {
                flushPendingReviewChanges()
            }
        }
    }

    fun onReviewScreenExit() {
        flushWhenReviewFinishesSaving = true
        flushPendingReviewChanges()
    }

    fun previews(): List<ScheduledReview> = _reviewState.value?.previews.orEmpty()

    private fun previewsFor(current: ReviewCard<Note>?): List<ScheduledReview> = current?.let {
        Sm2OsrScheduler.previews(
            previous = it.card.schedule,
            today = LocalDate.now(),
            initialEase = initialEaseFor(it),
        )
    }.orEmpty()

    private fun initialEaseFor(current: ReviewCard<Note>): Int = Sm2OsrScheduler.initialEase(
        allReviewCards.asSequence()
            .filter { it.note.relativePath == current.note.relativePath }
            .mapNotNull { it.card.schedule }
            .toList(),
    )

    private suspend fun loadCards() {
        val (notes, noteFolders) = withContext(Dispatchers.IO) {
            dao.notesContainingTag("#flashcards") to dao.allNoteFolders()
        }
        allReviewCards = withContext(Dispatchers.Default) {
            notes.asSequence()
                .filter { it.fileExtension() is FileExtension.Md }
                .flatMap { note ->
                    FlashcardParser.parse(note.content, note.nameWithoutExtension())
                        .mapIndexed { index, card -> ReviewCard(note, card, index) }
                }
                .toList()
        }
        _folders.value = noteFolders.filter { it.relativePath.isNotEmpty() }
        rebuildDecks()
        _isLoading.value = false
    }

    private fun rebuildDecks() {
        val cards = filteredCards(_selectedFolder.value)
        val today = LocalDate.now()
        val paths = linkedSetOf<String>()
        cards.forEach { reviewCard ->
            reviewCard.card.decks.forEach { deck ->
                paths += ""
                if (deck.isNotEmpty()) {
                    val segments = deck.split('/')
                    for (length in 1..segments.size) {
                        paths += segments.take(length).joinToString("/")
                    }
                }
            }
        }
        _decks.value = paths.map { path ->
            val matching = cards.filter { card -> card.card.decks.any { it.inDeck(path) } }
            FlashcardDeckSummary(
                path = path,
                availableCount = matching.count { it.card.isAvailable(today) },
                totalCount = matching.size,
            )
        }.filter { it.totalCount > 0 }
        _availableCount.value = allReviewCards.count { it.card.isAvailable(today) }
    }

    private fun filteredCards(folderPath: String?): List<ReviewCard<Note>> = allReviewCards.filter {
        folderPath == null || it.note.parentPath() == folderPath ||
            it.note.parentPath().startsWith("$folderPath/")
    }

    private fun String.inDeck(parentDeck: String): Boolean =
        parentDeck.isEmpty() || this == parentDeck || startsWith("$parentDeck/")

    private fun failReview(previousState: FlashcardReviewState, error: Throwable) {
        val message = if (error is ConcurrentNoteChangeException) {
            uiHelper.getString(R.string.note_changed_on_disk)
        } else {
            error.message ?: error.toString()
        }
        uiHelper.makeToast(message)
        _reviewState.value = previousState.copy(saving = false, error = message)
    }

    private fun flushPendingReviewChanges() {
        if (!hasPendingReviewChanges) return
        hasPendingReviewChanges = false
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                storageManager.commitAndPushPending("gitnote reviewed flashcards")
            }
            result.onFailure { error ->
                hasPendingReviewChanges = true
                Log.e(TAG, "Failed to commit or push pending flashcard reviews", error)
            }
        }
    }
}
