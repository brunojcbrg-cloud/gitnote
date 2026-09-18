package io.github.wiiznokes.gitnote.ui.screen.app.edit

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.wiiznokes.gitnote.R
import io.github.wiiznokes.gitnote.ui.component.BaseDialog
import io.github.wiiznokes.gitnote.ui.viewmodel.edit.buildTable

@Composable
internal fun TableActionButton(
    onInsert: (columns: Int, bodyRows: Int) -> Unit,
) {
    val expanded = rememberSaveable { mutableStateOf(false) }
    SmallButton(
        onClick = { expanded.value = true },
        imageVector = Icons.Default.TableChart,
        contentDescription = stringResource(R.string.table),
    )
    TableSizeDialog(
        expanded = expanded,
        title = stringResource(R.string.new_table),
        actionText = stringResource(R.string.insert_table),
        initialColumns = 3,
        initialRows = 2,
        onValidation = onInsert,
    )
}

@Composable
internal fun TableSizeDialog(
    expanded: MutableState<Boolean>,
    title: String,
    actionText: String,
    initialColumns: Int,
    initialRows: Int,
    onValidation: (columns: Int, bodyRows: Int) -> Unit,
) {
    var columnsText by rememberSaveable { mutableStateOf(initialColumns.toString()) }
    var rowsText by rememberSaveable { mutableStateOf(initialRows.toString()) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(expanded.value, initialColumns, initialRows) {
        if (expanded.value) {
            columnsText = initialColumns.toString()
            rowsText = initialRows.toString()
            focusRequester.requestFocus()
        }
    }

    BaseDialog(expanded = expanded) {
        val columns = columnsText.toIntOrNull()
        val rows = rowsText.toIntOrNull()
        val valid = columns != null && columns in 1..10 && rows != null && rows in 1..50

        Text(text = title)
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .testTag("table-columns"),
            value = columnsText,
            onValueChange = { columnsText = it },
            label = { Text(stringResource(R.string.table_columns)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("table-rows"),
            value = rowsText,
            onValueChange = { rowsText = it },
            label = { Text(stringResource(R.string.table_rows_without_header)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = stringResource(R.string.table_preview))
        if (valid) {
            Text(
                text = buildTable(columns!!, rows!!, "\n"),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .testTag("table-preview"),
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            enabled = valid,
            onClick = {
                onValidation(columns!!, rows!!)
                expanded.value = false
            },
        ) {
            Text(actionText)
        }
    }
}
