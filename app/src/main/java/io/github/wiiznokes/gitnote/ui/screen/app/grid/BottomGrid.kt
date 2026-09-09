package io.github.wiiznokes.gitnote.ui.screen.app.grid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.wiiznokes.gitnote.R
import io.github.wiiznokes.gitnote.data.room.Note
import io.github.wiiznokes.gitnote.ui.model.EditType
import io.github.wiiznokes.gitnote.ui.viewmodel.GridViewModel
import kotlin.math.roundToInt


@Composable
fun FloatingActionButtons(
    vm: GridViewModel,
    offset: Float,
    onEditClick: (Note, EditType) -> Unit,
    onFlashcardsClick: () -> Unit,
    availableFlashcards: Int,
) {
    Column(
        modifier = Modifier
            .offset { IntOffset(x = 0, y = -offset.roundToInt()) },
        horizontalAlignment = androidx.compose.ui.Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ExtendedFloatingActionButton(
            onClick = onFlashcardsClick,
            icon = {
                Icon(
                    Icons.Outlined.Style,
                    contentDescription = stringResource(R.string.flashcards),
                )
            },
            text = {
                Text(
                    if (availableFlashcards > 0) {
                        "${stringResource(R.string.flashcards)} · $availableFlashcards"
                    } else {
                        stringResource(R.string.flashcards)
                    }
                )
            },
        )
        FloatingActionButton(
            onClick = { onEditClick(vm.defaultNewNote(), EditType.Create) },
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "create note",
            )
        }
    }
}
