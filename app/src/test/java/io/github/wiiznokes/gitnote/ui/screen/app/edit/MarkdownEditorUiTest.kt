package io.github.wiiznokes.gitnote.ui.screen.app.edit

import android.app.Application
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import io.github.wiiznokes.gitnote.ui.viewmodel.edit.markdownSmartEditor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

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

}
