package io.github.wiiznokes.gitnote.ui.screen.app.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.wiiznokes.gitnote.R

/**
 * Tela inicial do Life SO.
 *
 * As notas passaram a ser UMA funcao do app, nao o app inteiro: esta tela e o ponto de
 * entrada de onde as funcoes sao abertas. Para acrescentar uma funcao nova, basta somar
 * um HomeFunctionCard aqui e um destino em AppDestination.
 */
@Composable
fun HomeScreen(
    onNotesClick: () -> Unit,
    onFlashcardsClick: () -> Unit,
    onSendLessonClick: () -> Unit,
    onLessonHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeContent,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))

            HomeFunctionCard(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                title = stringResource(R.string.home_notes),
                detail = stringResource(R.string.home_notes_detail),
                onClick = onNotesClick,
            )
            Spacer(modifier = Modifier.height(12.dp))
            HomeFunctionCard(
                icon = Icons.Filled.Style,
                title = stringResource(R.string.flashcards),
                detail = stringResource(R.string.home_flashcards_detail),
                onClick = onFlashcardsClick,
            )
            Spacer(modifier = Modifier.height(12.dp))
            HomeFunctionCard(
                icon = Icons.Filled.UploadFile,
                title = stringResource(R.string.home_send_lesson),
                detail = stringResource(R.string.home_send_lesson_detail),
                onClick = onSendLessonClick,
            )
            Spacer(modifier = Modifier.height(12.dp))
            HomeFunctionCard(
                icon = Icons.Filled.History,
                title = stringResource(R.string.home_lesson_history),
                detail = stringResource(R.string.home_lesson_history_detail),
                onClick = onLessonHistoryClick,
            )
            Spacer(modifier = Modifier.height(12.dp))
            HomeFunctionCard(
                icon = Icons.Filled.Settings,
                title = stringResource(R.string.settings),
                detail = stringResource(R.string.home_settings_detail),
                onClick = onSettingsClick,
            )
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun HomeFunctionCard(
    icon: ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    // Card(onClick=...) e API experimental do Material3; clickable evita o opt-in.
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(30.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.size(16.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
