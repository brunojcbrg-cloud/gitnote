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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.wiiznokes.gitnote.R
import io.github.wiiznokes.gitnote.ui.component.BaseDialog
import io.github.wiiznokes.gitnote.ui.viewmodel.edit.buildTable
import io.github.wiiznokes.gitnote.ui.viewmodel.edit.parseTable
import io.github.wiiznokes.gitnote.ui.viewmodel.edit.resizeTableAt
import io.github.wiiznokes.gitnote.ui.viewmodel.edit.tableRegionAt

@Composable
internal fun TableActionButton(
    value: TextFieldValue,
    onInsert: (columns: Int, bodyRows: Int) -> Unit,
    onResize: (columns: Int, bodyRows: Int) -> Unit,
) {
    val expanded = rememberSaveable { mutableStateOf(false) }
    val region = remember(value.text, value.selection.start) {
        tableRegionAt(value.text, value.selection.min)
    }
    val table = remember(value.text, region) {
        region?.let { parseTable(value.text, it.headerLine) }
    }
    SmallButton(
        onClick = { expanded.value = true },
        imageVector = Icons.Default.TableChart,
        contentDescription = stringResource(R.string.table),
    )
    TableSizeDialog(
        expanded = expanded,
        title = stringResource(if (table == null) R.string.new_table else R.string.configure_table),
        actionText = stringResource(if (table == null) R.string.insert_table else R.string.configure_table),
        initialColumns = table?.header?.size ?: 3,
        initialRows = table?.rows?.size ?: 2,
        lossFor = { columns, rows ->
            if (table == null) 0 else resizeTableAt(value, columns, rows)?.lostNonEmptyCells ?: 0
        },
        onValidation = if (table == null) onInsert else onResize,
    )
}

@Composable
internal fun TableSizeDialog(
    expanded: MutableState<Boolean>,
    title: String,
    actionText: String,
    initialColumns: Int,
    initialRows: Int,
    lossFor: (columns: Int, bodyRows: Int) -> Int = { _, _ -> 0 },
    onValidation: (columns: Int, bodyRows: Int) -> Unit,
) {
    var columnsText by rememberSaveable { mutableStateOf(initialColumns.toString()) }
    var rowsText by rememberSaveable { mutableStateOf(initialRows.toString()) }
    var pendingLoss by rememberSaveable { mutableStateOf<Int?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(expanded.value, initialColumns, initialRows) {
        if (expanded.value) {
            columnsText = initialColumns.toString()
            rowsText = initialRows.toString()
            pendingLoss = null
            withFrameNanos { }
            focusRequester.requestFocus()
        }
    }

    BaseDialog(expanded = expanded) {
        val columns = columnsText.toIntOrNull()
        val rows = rowsText.toIntOrNull()
        val valid = columns != null && columns in 1..10 && rows != null && rows in 1..50

        Text(text = title, modifier = Modifier.testTag("table-dialog-title"))
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .testTag("table-columns"),
            value = columnsText,
            onValueChange = {
                columnsText = it
                pendingLoss = null
            },
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
            onValueChange = {
                rowsText = it
                pendingLoss = null
            },
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
        pendingLoss?.let { loss ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.table_content_loss_confirmation, loss),
                modifier = Modifier.testTag("table-loss-warning"),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            enabled = valid,
            onClick = {
                val loss = lossFor(columns!!, rows!!)
                if (loss > 0 && pendingLoss != loss) {
                    pendingLoss = loss
                } else {
                    onValidation(columns!!, rows!!)
                    expanded.value = false
                }
            },
        ) {
            Text(if (pendingLoss == null) actionText else stringResource(R.string.continue_action))
        }
    }
}
