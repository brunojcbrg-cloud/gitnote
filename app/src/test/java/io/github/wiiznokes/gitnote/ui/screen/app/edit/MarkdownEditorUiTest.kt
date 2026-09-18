package io.github.wiiznokes.gitnote.ui.screen.app.edit

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import io.github.wiiznokes.gitnote.ui.component.BaseDialog
import io.github.wiiznokes.gitnote.ui.viewmodel.edit.markdownSmartEditor
import io.github.wiiznokes.gitnote.ui.viewmodel.edit.insertTable
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MarkdownEditorUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun enterThroughRealTextFieldContinuesAList() {
        var observed = TextFieldValue("- item", selection = TextRange(6))

        composeRule.setContent {
            var value by remember { mutableStateOf(observed) }
            observed = value
            TextField(
                value = value,
                onValueChange = { next -> value = markdownSmartEditor(value, next) },
                modifier = Modifier.testTag("editor"),
            )
        }

        composeRule.onNodeWithTag("editor").performTextInput("\n")
        composeRule.runOnIdle {
            assertEquals("- item\n- ", observed.text)
            assertEquals(TextRange(9), observed.selection)
        }
    }

    @Test
    fun tableButtonDialogConfirmsDefaultThreeByTwoInRealTextField() {
        var observed = TextFieldValue("", selection = TextRange(0))

        composeRule.setContent {
            var value by remember { mutableStateOf(observed) }
            observed = value
            Column {
                TextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.testTag("table-editor"),
                )
                TableActionButton(
                    value = value,
                    onInsert = { columns, rows -> value = insertTable(value, columns, rows) },
                    onResize = { _, _ -> error("empty document cannot resize") },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Table").performClick()
        composeRule.onNodeWithText("New table").assertExists()
        composeRule.onNodeWithText("Insert table").performClick()
        composeRule.runOnIdle {
            assertEquals(
                "|  |  |  |\n| --- | --- | --- |\n|  |  |  |\n|  |  |  |\n\n",
                observed.text,
            )
            assertEquals(TextRange(2), observed.selection)
        }
    }

    @Test
    fun sameTableButtonOpensConfigurationWhenCursorIsInsideTable() {
        val source = "| head | value |\n| --- | --- |\n| row | data |"

        composeRule.setContent {
            val value = TextFieldValue(source, selection = TextRange(source.indexOf("row")))
            TableActionButton(
                value = value,
                onInsert = { _, _ -> error("table context must not insert") },
                onResize = { _, _ -> },
            )
        }

        composeRule.onNodeWithContentDescription("Table").performClick()
        composeRule.onNodeWithTag("table-dialog-title").assertExists()
        composeRule.onNodeWithTag("table-columns").assertExists()
        composeRule.onNodeWithTag("table-rows").assertExists()
    }

    @Test
    fun textFormatRowWithTwelveButtonsIsWiderThanScreen() {
        var widthPx = 0
        var density = 1f

        composeRule.setContent {
            density = LocalDensity.current.density
            Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Row(modifier = Modifier.onSizeChanged { widthPx = it.width }) {
                    repeat(12) { index ->
                        SmallButton(
                            onClick = {},
                            imageVector = Icons.Default.TableChart,
                            contentDescription = "probe-$index",
                        )
                        if (index == 2 || index == 6 || index == 10) SmallSeparator()
                    }
                }
            }
        }

        composeRule.runOnIdle {
            val widthDp = widthPx / density
            println("MEASURED_TEXT_FORMAT_ROW_WIDTH_DP=$widthDp")
            assertTrue(widthDp > 360f, "a barra mediu $widthDp dp e deveria exceder 360 dp")
        }
    }

    @Test
    @Config(sdk = [27], application = Application::class)
    fun initialPassObserverMeasuresLongPressWithoutBreakingTextSelection() {
        val source = "| head | value |\n| --- | --- |\n| body | data |"
        var observed = TextFieldValue(source, selection = TextRange(0))
        var durationMs = -1L
        var selectionWhenOpened = TextRange.Zero
        var dialogState: androidx.compose.runtime.MutableState<Boolean>? = null

        composeRule.setContent {
            var value by remember { mutableStateOf(observed) }
            val expanded = remember { mutableStateOf(false) }
            observed = value
            dialogState = expanded
            TextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier
                    .size(width = 320.dp, height = 180.dp)
                    .testTag("measured-long-press-editor")
                    .observeInitialPassLongPress { measuredDuration ->
                        durationMs = measuredDuration
                        selectionWhenOpened = observed.selection
                        expanded.value = true
                    },
            )
            BaseDialog(expanded = expanded) {
                Text("Measured table configuration")
            }
        }

        composeRule.onNodeWithTag("measured-long-press-editor").performTouchInput {
            longClick(center)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Measured table configuration").assertExists()
        println(
            "MEASURED_TABLE_LONG_PRESS duration_ms=$durationMs " +
                "selection=${selectionWhenOpened.start}..${selectionWhenOpened.end}",
        )
        assertTrue(durationMs >= 500L, "o observador não mediu a pressão longa")
        assertTrue(!selectionWhenOpened.collapsed, "a seleção do TextField foi perdida")

        composeRule.runOnIdle { dialogState!!.value = false }
        composeRule.onNodeWithTag("measured-long-press-editor").performTouchInput {
            click(Offset(width - 20f, centerY))
        }
        composeRule.runOnIdle {
            assertTrue(observed.selection.collapsed, "a seleção ficou presa depois de fechar o diálogo")
        }
    }

    /**
     * A parte A do handoff 07: o arrasto na faixa direita do editor pede uma linha.
     * Aqui se mede a ligacao do gesto. Que o TextField role ate o cursor e o mesmo
     * mecanismo de que a volta ao lugar depende, e so o aparelho mostra.
     */
    @Test
    fun longPressOnTheRightEdgeAsksForALineNearTheFinger() {
        var requested: Int? = null

        composeRule.setContent {
            Box(modifier = Modifier.size(width = 320.dp, height = 240.dp)) {
                TextField(
                    value = TextFieldValue("linha", selection = TextRange(0)),
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("long-editor"),
                )
                FastScrollLineOverlay(
                    lineCount = 200,
                    currentLine = 0,
                    lineHeight = 24f,
                    onLineRequested = { requested = it },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .testTag("gutter"),
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("gutter").performTouchInput {
            longClick(Offset(centerX, bottom - 10f))
        }
        composeRule.waitForIdle()

        val line = requested
        assertNotNull(line, "o arrasto na faixa direita nao pediu linha nenhuma")
        assertTrue(line > 150, "o dedo no fim da faixa pediu a linha $line de 200")
    }

    @Test
    fun aShortNoteHasNoGutterToAskForLines() {
        var requested: Int? = null

        composeRule.setContent {
            Box(modifier = Modifier.size(width = 320.dp, height = 240.dp)) {
                FastScrollLineOverlay(
                    lineCount = 4,
                    currentLine = 0,
                    lineHeight = 24f,
                    onLineRequested = { requested = it },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .testTag("gutter"),
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("gutter").performTouchInput {
            longClick(Offset(centerX, bottom - 10f))
        }
        composeRule.waitForIdle()

        assertEquals(null, requested, "a nota cabe na tela e mesmo assim moveu o cursor")
    }
}

private fun Modifier.observeInitialPassLongPress(onLongPress: (durationMs: Long) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val down = event.changes.firstOrNull { it.pressed && !it.previousPressed } ?: continue
                val startedAt = down.uptimeMillis
                val startedAtPosition = down.position
                var lastEventAt = startedAt
                var moved = false
                var pressed = true
                while (pressed) {
                    val next = awaitPointerEvent(PointerEventPass.Initial)
                    val change = next.changes.firstOrNull { it.id == down.id } ?: continue
                    lastEventAt = change.uptimeMillis
                    if ((change.position - startedAtPosition).getDistance() > viewConfiguration.touchSlop) {
                        moved = true
                    }
                    pressed = change.pressed
                }
                val duration = lastEventAt - startedAt
                if (!moved && duration >= viewConfiguration.longPressTimeoutMillis) {
                    onLongPress(duration)
                }
            }
        }
    }
