package io.github.wiiznokes.gitnote.ui.screen.app.edit

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import io.github.wiiznokes.gitnote.ui.viewmodel.edit.markdownSmartEditor
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
