package io.github.wiiznokes.gitnote.ui.screen.app.flashcard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import io.github.wiiznokes.gitnote.R
import io.github.wiiznokes.gitnote.data.room.Note
import io.github.wiiznokes.gitnote.flashcard.FlashcardRating
import io.github.wiiznokes.gitnote.ui.component.markdown.missingWikilinkAnnotator
import io.github.wiiznokes.gitnote.ui.component.markdown.preprocessWikilinksForReading
import io.github.wiiznokes.gitnote.ui.screen.app.grid.MarkdownCustomInner
import io.github.wiiznokes.gitnote.ui.screen.app.grid.markdownColorsThemed
import io.github.wiiznokes.gitnote.ui.screen.app.grid.markdownTypographyThemed
import io.github.wiiznokes.gitnote.ui.theme.markdownColorScheme
import io.github.wiiznokes.gitnote.ui.viewmodel.FlashcardDeckSummary
import io.github.wiiznokes.gitnote.ui.viewmodel.FlashcardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardDeckScreen(
    onBack: () -> Unit,
    onReview: (deckPath: String, folderPath: String?) -> Unit,
) {
    val vm: FlashcardViewModel = viewModel()
    val loading by vm.isLoading.collectAsStateWithLifecycle()
    val decks by vm.decks.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val selectedFolder by vm.selectedFolder.collectAsStateWithLifecycle()
    var folderMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeContent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.flashcards)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.go_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedButton(onClick = { folderMenuExpanded = true }) {
                    Text(
                        if (selectedFolder.isNullOrEmpty()) {
                            stringResource(R.string.all_folders)
                        } else {
                            checkNotNull(selectedFolder)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                DropdownMenu(
                    expanded = folderMenuExpanded,
                    onDismissRequest = { folderMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.all_folders)) },
                        onClick = {
                            vm.selectFolder(null)
                            folderMenuExpanded = false
                        },
                    )
                    folders.forEach { folder ->
                        DropdownMenuItem(
                            text = { Text(folder.relativePath) },
                            onClick = {
                                vm.selectFolder(folder.relativePath)
                                folderMenuExpanded = false
                            },
                        )
                    }
                }
            }

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                decks.isEmpty() -> EmptyDecks()

                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(decks, key = { it.path }) { deck ->
                        DeckRow(
                            deck = deck,
                            onClick = { onReview(deck.path, selectedFolder) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardReviewScreen(
    deckPath: String,
    folderPath: String?,
    onBack: () -> Unit,
    onOpenNote: (Note) -> Unit,
) {
    val vm: FlashcardViewModel = viewModel()
    val state by vm.reviewState.collectAsState()
    LaunchedEffect(deckPath, folderPath) { vm.startReview(deckPath, folderPath) }
    val current = state?.current

    Scaffold(
        contentWindowInsets = WindowInsets.safeContent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            deckPath.ifEmpty { "#flashcards" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        state?.let {
                            Text(
                                "${it.completed}/${it.total}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.go_back))
                    }
                },
                actions = {
                    current?.let { reviewCard ->
                        IconButton(onClick = { onOpenNote(reviewCard.note) }) {
                            Icon(
                                Icons.Outlined.Description,
                                stringResource(R.string.open_source_note),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state == null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }

            current == null -> ReviewComplete(
                completed = state?.completed ?: 0,
                modifier = Modifier.fillMaxSize().padding(padding),
            )

            else -> ReviewCardContent(
                vm = vm,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

@Composable
private fun DeckRow(deck: FlashcardDeckSummary, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(
                deck.displayName,
                fontWeight = if (deck.depth == 0) FontWeight.SemiBold else FontWeight.Medium,
            )
        },
        supportingContent = {
            Text(
                stringResource(
                    R.string.flashcard_deck_count,
                    deck.availableCount,
                    deck.totalCount,
                ),
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (deck.depth * 20).dp)
            .clickable(enabled = deck.availableCount > 0, onClick = onClick),
    )
}

@Composable
private fun EmptyDecks() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.no_flashcards_in_filter),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun ReviewComplete(completed: Int, modifier: Modifier = Modifier) {
    Box(modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.review_complete),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.reviewed_cards_count, completed),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReviewCardContent(
    vm: FlashcardViewModel,
    modifier: Modifier = Modifier,
) {
    val state = checkNotNull(vm.reviewState.collectAsState().value)
    val current = checkNotNull(state.current)
    val previews = vm.previews()

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            current.card.context.joinToString(" > "),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        FlashcardMarkdown(vm, current.card.question)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (!state.revealed) {
            Button(
                onClick = vm::revealAnswer,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(R.string.reveal_answer))
            }
        } else {
            FlashcardMarkdown(vm, current.card.answer)
            Spacer(Modifier.height(8.dp))
            previews.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { preview ->
                        ReviewChoiceButton(
                            rating = preview.rating,
                            interval = if (preview.schedule.interval == 0) {
                                stringResource(R.string.flashcard_one_minute)
                            } else {
                                stringResource(
                                    R.string.flashcard_days_interval,
                                    preview.schedule.interval,
                                )
                            },
                            enabled = !state.saving,
                            onClick = { vm.review(preview.rating) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        state.error?.let { error ->
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ReviewChoiceButton(
    rating: FlashcardRating,
    interval: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = when (rating) {
        FlashcardRating.AGAIN -> stringResource(R.string.flashcard_again)
        FlashcardRating.HARD -> stringResource(R.string.flashcard_hard)
        FlashcardRating.GOOD -> stringResource(R.string.flashcard_good)
        FlashcardRating.EASY -> stringResource(R.string.flashcard_easy)
    }
    if (rating == FlashcardRating.GOOD) {
        FilledTonalButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
        ) { Text("$label — $interval") }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
        ) { Text("$label — $interval") }
    }
}

@Composable
private fun FlashcardMarkdown(vm: FlashcardViewModel, content: String) {
    val isMarkdownThemeActive by vm.prefs.isMarkdownThemeActive.getAsState()
    val markdownTheme by vm.prefs.markdownColorTheme.getAsState()
    val palette = markdownColorScheme(markdownTheme)
    val rendered = remember(content) { preprocessWikilinksForReading(content) }
    val noOpUriHandler = remember {
        object : UriHandler {
            override fun openUri(uri: String) = Unit
        }
    }
    CompositionLocalProvider(LocalUriHandler provides noOpUriHandler) {
        MarkdownCustomInner(
            content = rendered,
            colors = if (isMarkdownThemeActive) markdownColorsThemed(palette) else markdownColor(),
            typography = if (isMarkdownThemeActive) {
                markdownTypographyThemed(palette)
            } else {
                markdownTypography()
            },
            annotator = missingWikilinkAnnotator(MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
