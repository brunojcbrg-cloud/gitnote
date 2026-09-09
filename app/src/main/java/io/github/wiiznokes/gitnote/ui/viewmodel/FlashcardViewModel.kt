package io.github.wiiznokes.gitnote.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.wiiznokes.gitnote.MyApp
import io.github.wiiznokes.gitnote.data.AppPreferences
import io.github.wiiznokes.gitnote.data.room.Note
import io.github.wiiznokes.gitnote.data.room.NoteFolder
import io.github.wiiznokes.gitnote.flashcard.FlashcardNoteUpdater
import io.github.wiiznokes.gitnote.flashcard.FlashcardParser
import io.github.wiiznokes.gitnote.flashcard.FlashcardRating
import io.github.wiiznokes.gitnote.flashcard.ParsedFlashcard
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

data class ReviewCard(
    val note: Note,
    val card: ParsedFlashcard,
)

data class FlashcardReviewState(
    val deckPath: String,
    val queue: List<ReviewCard>,
    val completed: Int,
    val total: Int,
    val revealed: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
) {
    val current: ReviewCard?
        get() = queue.firstOrNull()
}

class FlashcardViewModel : ViewModel() {
    val prefs: AppPreferences = MyApp.appModule.appPreferences
    private val dao = MyApp.appModule.repoDatabase.repoDatabaseDao
    private val storageManager = MyApp.appModule.storageManager
    private val uiHelper = MyApp.appModule.uiHelper

    private var allReviewCards: List<ReviewCard> = emptyList()
    private val initialLoad: Job

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
            _selectedFolder.value = folderPath
            val queue = filteredCards(folderPath)
                .filter { reviewCard -> reviewCard.card.decks.any { it.inDeck(deckPath) } }
                .filter { it.card.isAvailable(LocalDate.now()) }
                .sortedWith(
                    compareBy<ReviewCard> { it.note.relativePath }
                        .thenByDescending { it.card.sourceRange.first },
                )
            _reviewState.value = FlashcardReviewState(
                deckPath = deckPath,
                queue = queue,
                completed = 0,
                total = queue.size,
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
            val initialEase = initialEaseFor(current)
            val schedule = Sm2OsrScheduler.next(
                previous = current.card.schedule,
                rating = rating,
                today = LocalDate.now(),
                initialEase = initialEase,
            )
            val newContent = runCatching {
                FlashcardNoteUpdater.update(current.note.content, current.card, schedule)
            }.getOrElse { error ->
                failReview(state, error)
                return@launch
            }
            val newNote = current.note.copy(
                content = newContent,
                lastModifiedTimeMillis = Instant.now().toEpochMilli(),
            )
            val result = withContext(Dispatchers.IO) {
                storageManager.updateNote(newNote, current.note)
            }
            val saveError = result.exceptionOrNull()
            if (saveError != null) {
                failReview(state, saveError)
                return@launch
            }

            val remaining = state.queue.drop(1).map { queued ->
                if (queued.note.relativePath == newNote.relativePath) queued.copy(note = newNote)
                else queued
            }.toMutableList()

            if (rating == FlashcardRating.AGAIN) {
                val reparsed = FlashcardParser.parse(newContent, newNote.nameWithoutExtension())
                    .firstOrNull {
                        it.sourceRange == current.card.sourceRange &&
                            it.question == current.card.question && it.answer == current.card.answer
                    }
                if (reparsed != null) remaining += ReviewCard(newNote, reparsed)
            }

            allReviewCards = allReviewCards.filter {
                it.note.relativePath != newNote.relativePath
            } + FlashcardParser.parse(newContent, newNote.nameWithoutExtension()).map {
                ReviewCard(newNote, it)
            }
            rebuildDecks()

            _reviewState.value = state.copy(
                queue = remaining,
                completed = state.completed + if (rating == FlashcardRating.AGAIN) 0 else 1,
                revealed = false,
                saving = false,
                error = null,
            )
        }
    }

    fun previews(): List<ScheduledReview> {
        val current = _reviewState.value?.current ?: return emptyList()
        return Sm2OsrScheduler.previews(
            previous = current.card.schedule,
            today = LocalDate.now(),
            initialEase = initialEaseFor(current),
        )
    }

    private fun initialEaseFor(current: ReviewCard): Int = Sm2OsrScheduler.initialEase(
        allReviewCards.asSequence()
            .filter { it.note.relativePath == current.note.relativePath }
            .mapNotNull { it.card.schedule }
            .toList(),
    )

    private suspend fun loadCards() {
        val (notes, noteFolders) = withContext(Dispatchers.IO) {
            dao.allNotes() to dao.allNoteFolders()
        }
        allReviewCards = notes.asSequence()
            .filter { it.fileExtension() is FileExtension.Md }
            .flatMap { note ->
                FlashcardParser.parse(note.content, note.nameWithoutExtension())
                    .map { ReviewCard(note, it) }
            }
            .toList()
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

    private fun filteredCards(folderPath: String?): List<ReviewCard> = allReviewCards.filter {
        folderPath == null || it.note.parentPath() == folderPath ||
            it.note.parentPath().startsWith("$folderPath/")
    }

    private fun String.inDeck(parentDeck: String): Boolean =
        parentDeck.isEmpty() || this == parentDeck || startsWith("$parentDeck/")

    private fun failReview(previousState: FlashcardReviewState, error: Throwable) {
        val message = error.message ?: error.toString()
        uiHelper.makeToast(message)
        _reviewState.value = previousState.copy(saving = false, error = message)
    }
}
