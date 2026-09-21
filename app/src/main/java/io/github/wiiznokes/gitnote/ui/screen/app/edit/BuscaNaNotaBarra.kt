package io.github.wiiznokes.gitnote.ui.screen.app.edit

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.wiiznokes.gitnote.R

@Composable
internal fun BuscaNaNotaBarra(
    termo: String,
    indice: Int,
    total: Int,
    onTermoChange: (String) -> Unit,
    onAnterior: () -> Unit,
    onProximo: () -> Unit,
    onFechar: () -> Unit,
) {
    val descricaoAnterior = stringResource(R.string.previous_occurrence)
    val descricaoProximo = stringResource(R.string.next_occurrence)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = termo,
            onValueChange = onTermoChange,
            modifier = Modifier.weight(1f).testTag("note-search-field"),
            singleLine = true,
            label = { Text(stringResource(R.string.search_in_note)) },
        )
        Text(text = if (total == 0) "0 / 0" else "${indice + 1} / $total")
        IconButton(onClick = onAnterior, enabled = total > 0) {
            Text("‹", modifier = Modifier.semantics {
                contentDescription = descricaoAnterior
            })
        }
        IconButton(onClick = onProximo, enabled = total > 0) {
            Text("›", modifier = Modifier.semantics {
                contentDescription = descricaoProximo
            })
        }
        IconButton(onClick = onFechar) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_search))
        }
    }
}
