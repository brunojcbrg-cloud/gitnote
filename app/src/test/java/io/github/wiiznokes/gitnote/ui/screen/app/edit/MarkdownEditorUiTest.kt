package io.github.wiiznokes.gitnote.ui.screen.app.edit

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.unit.dp
import io.github.wiiznokes.gitnote.ui.viewmodel.edit.markdownSmartEditor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
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
    fun cursorAtTheEndBringsTheHoistedParentScrollIntoView() {
        val source = (1..100).joinToString("\n") { "linha $it" }
        val focusRequester = FocusRequester()
        lateinit var parentScrollState: androidx.compose.foundation.ScrollState

        composeRule.setContent {
            parentScrollState = rememberScrollState()
            Box(
                modifier = Modifier
                    .size(width = 320.dp, height = 240.dp)
                    .verticalScroll(parentScrollState),
            ) {
                TextField(
                    value = TextFieldValue(source, selection = TextRange(source.length)),
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 240.dp)
                        .focusRequester(focusRequester),
                )
            }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        }

        composeRule.waitForIdle()
        println(
            "HOISTED_CURSOR_SCROLL max=${parentScrollState.maxValue} " +
                "value=${parentScrollState.value}",
        )
        composeRule.runOnIdle {
            assertTrue(parentScrollState.maxValue > 0, "the parent has no scroll range")
            assertTrue(parentScrollState.value > 0, "the cursor did not move the parent scroll")
        }
    }

    @Test
    fun fastScrollHitAreaDoesNotBlockANormalRightEdgeTap() {
        var observed = TextFieldValue("primeira linha\nsegunda linha", selection = TextRange(0))

        composeRule.setContent {
            var value by remember { mutableStateOf(observed) }
            val scrollState = rememberScrollState()
            observed = value
            FastScrollOverlay(
                scrollState = scrollState,
                modifier = Modifier.size(width = 320.dp, height = 240.dp),
            ) {
                TextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("edge-editor"),
                )
            }
        }

        val editor = composeRule.onNodeWithTag("edge-editor")
        editor.performTouchInput {
            click(Offset(right - 1f, 30f))
        }
        composeRule.waitForIdle()

        val selection = editor.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]
        assertTrue(selection.start > 0, "right-edge tap left the cursor at offset zero")
        assertTrue(observed.selection.start > 0)
    }

    @Test
    fun rightEdgeLongPressScrollsWithoutOpeningTextSelection() {
        val source = (1..100).joinToString("\n") { "linha longa $it" }
        var observed = TextFieldValue(source, selection = TextRange(0))
        lateinit var parentScrollState: androidx.compose.foundation.ScrollState

        composeRule.setContent {
            var value by remember { mutableStateOf(observed) }
            parentScrollState = rememberScrollState()
            observed = value
            FastScrollOverlay(
                scrollState = parentScrollState,
                modifier = Modifier.size(width = 320.dp, height = 240.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(parentScrollState),
                ) {
                    TextField(
                        value = value,
                        onValueChange = { value = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 240.dp)
                            .testTag("long-press-editor"),
                    )
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) { parentScrollState.maxValue > 0 }
        val editor = composeRule.onNodeWithTag("long-press-editor")
        editor.performTouchInput {
            longClick(Offset(right - 1f, bottom - 10f))
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { parentScrollState.value > 0 }

        val selection = editor.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]
        assertTrue(parentScrollState.value > 0)
        assertTrue(selection.collapsed, "long press opened a text selection instead of fast scroll")
        assertTrue(observed.selection.collapsed)
    }
}
