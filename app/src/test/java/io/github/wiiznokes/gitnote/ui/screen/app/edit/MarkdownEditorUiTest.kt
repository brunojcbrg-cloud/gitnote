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
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import io.github.wiiznokes.gitnote.ui.viewmodel.edit.editMarkdownValue
import io.github.wiiznokes.gitnote.ui.viewmodel.edit.insertTable
import io.github.wiiznokes.gitnote.ui.viewmodel.edit.toEdicaoDeTexto
import io.github.wiiznokes.gitnote.ui.viewmodel.edit.toTextFieldValue
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
                onValueChange = { next -> value = editMarkdownValue(value, next) },
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
                    currentValue = { value },
                    onInsert = { columns, rows ->
                        value = insertTable(value.toEdicaoDeTexto(), columns, rows)
                            .toTextFieldValue(value, clearComposition = true)
                    },
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
                currentValue = { value },
                onInsert = { _, _ -> error("table context must not insert") },
                onResize = { _, _ -> },
            )
        }

        composeRule.onNodeWithContentDescription("Table").performClick()
        composeRule.onNodeWithTag("table-dialog-title").assertTextEquals("Configure table")
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
