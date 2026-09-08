package io.github.wiiznokes.gitnote.ui.screen.app.edit

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import io.github.wiiznokes.gitnote.R
import io.github.wiiznokes.gitnote.data.room.Note
import io.github.wiiznokes.gitnote.ui.component.markdown.MarkdownLivePreviewTransformation
import io.github.wiiznokes.gitnote.ui.component.markdown.activeMarkdownLines
import io.github.wiiznokes.gitnote.ui.component.markdown.missingWikilinkAnnotator
import io.github.wiiznokes.gitnote.ui.component.markdown.parseWikilinkUri
import io.github.wiiznokes.gitnote.ui.component.markdown.preprocessWikilinksForReading
import io.github.wiiznokes.gitnote.ui.component.markdown.wikilinkNames
import io.github.wiiznokes.gitnote.ui.screen.app.grid.MarkdownCustomInner
import io.github.wiiznokes.gitnote.ui.screen.app.grid.markdownColorsThemed
import io.github.wiiznokes.gitnote.ui.screen.app.grid.markdownTypographyThemed
import io.github.wiiznokes.gitnote.ui.theme.markdownColorScheme
import io.github.wiiznokes.gitnote.ui.viewmodel.edit.MarkDownVM

@Composable
fun MarkDownContent(
    vm: MarkDownVM,
    textFocusRequester: FocusRequester,
    onFinished: () -> Unit,
    onOpenNote: (Note) -> Unit = {},
    isReadOnlyModeActive: Boolean,
    textContent: TextFieldValue,
) {
    val isMarkdownThemeActive by vm.prefs.isMarkdownThemeActive.getAsState()
    val markdownTheme by vm.prefs.markdownColorTheme.getAsState()
    val colors = markdownColorScheme(markdownTheme)

    if (isReadOnlyModeActive) {
        val names = remember(textContent.text) { wikilinkNames(textContent.text) }
        var resolvedTargets by remember(textContent.text, vm.previousNote.relativePath) {
            mutableStateOf<Map<String, String?>?>(null)
        }
        LaunchedEffect(names, vm.previousNote.relativePath) {
            resolvedTargets = vm.resolveWikilinks(names)
        }
        val existingNames = resolvedTargets
            ?.filterValues { it != null }
            ?.keys
        val renderedContent = remember(textContent.text, existingNames) {
            preprocessWikilinksForReading(textContent.text, existingNames)
        }
        val originalUriHandler = LocalUriHandler.current
        val uriHandler = remember(originalUriHandler, vm, onOpenNote, resolvedTargets) {
            object : UriHandler {
                override fun openUri(uri: String) {
                    val wikilink = parseWikilinkUri(uri)
                    if (wikilink == null) {
                        originalUriHandler.openUri(uri)
                    } else if (wikilink.isMissing) {
                        vm.showMissingWikilink(wikilink.name)
                    } else {
                        val targetPath = resolvedTargets?.get(wikilink.name)
                        if (targetPath != null) {
                            vm.openResolvedWikilink(
                                relativePath = targetPath,
                                name = wikilink.name,
                                onOpenNote = onOpenNote,
                            )
                        } else if (resolvedTargets != null) {
                            vm.showMissingWikilink(wikilink.name)
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                val readingColors = if (isMarkdownThemeActive) {
                    markdownColorsThemed(colors)
                } else {
                    markdownColor()
                }
                val readingTypography = if (isMarkdownThemeActive) {
                    markdownTypographyThemed(colors)
                } else {
                    markdownTypography()
                }
                val annotator = missingWikilinkAnnotator(MaterialTheme.colorScheme.error)

                SelectionContainer {
                    MarkdownCustomInner(
                        content = renderedContent,
                        colors = readingColors,
                        typography = readingTypography,
                        annotator = annotator,
                        modifier = Modifier.padding(15.dp),
                    )
                }
            }
        }
    } else {
        val baseFontSize = MaterialTheme.typography.bodyLarge.fontSize
        val visualTransformation = remember(
            textContent.text,
            textContent.selection,
            colors,
            isMarkdownThemeActive,
            baseFontSize,
        ) {
            if (!isMarkdownThemeActive) {
                VisualTransformation.None
            } else {
                MarkdownLivePreviewTransformation(
                    colors = colors,
                    activeLines = activeMarkdownLines(
                        text = textContent.text,
                        selectionStart = textContent.selection.start,
                        selectionEnd = textContent.selection.end,
                    ),
                    baseFontSize = baseFontSize,
                )
            }
        }
        GenericTextField(
            vm = vm,
            textFocusRequester = textFocusRequester,
            onFinished = onFinished,
            textContent = textContent,
            visualTransformation = visualTransformation,
        )
    }
}


@Composable
fun TextFormatRow(
    vm: MarkDownVM,
    modifier: Modifier = Modifier,
    textFormatExpanded: MutableState<Boolean>
) {
    val isMarkdownThemeActive by vm.prefs.isMarkdownThemeActive.getAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(bottomBarHeight)
            .scrollable(rememberScrollState(initial = 0), orientation = Orientation.Horizontal),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        SmallButton(
            onClick = { vm.onTitle() },
            imageVector = Icons.Default.Title,
            contentDescription = "title"
        )

        SmallButton(
            onClick = { vm.onBold() },
            imageVector = Icons.Default.FormatBold,
            contentDescription = "bold"
        )
        SmallButton(
            onClick = { vm.onItalic() },
            imageVector = Icons.Default.FormatItalic,
            contentDescription = "italic"
        )

        SmallSeparator()

        SmallButton(
            onClick = { vm.setMarkdownTheme(!isMarkdownThemeActive) },
            imageVector = if (isMarkdownThemeActive) {
                Icons.Default.Palette
            } else {
                Icons.Outlined.Palette
            },
            contentDescription = stringResource(
                if (isMarkdownThemeActive) {
                    R.string.markdown_theme_deactivate
                } else {
                    R.string.markdown_theme_activate
                }
            ),
        )

        SmallButton(
            onClick = { vm.onLink() },
            imageVector = Icons.Default.Link,
            contentDescription = "link"
        )

        SmallButton(
            onClick = { vm.onCode() },
            imageVector = Icons.Default.Code,
            contentDescription = "code"
        )
        SmallButton(
            onClick = { vm.onQuote() },
            imageVector = Icons.Default.FormatQuote,
            contentDescription = "quote"
        )

        SmallSeparator()

        SmallButton(
            onClick = { vm.onUnorderedList() },
            imageVector = Icons.AutoMirrored.Filled.List,
            contentDescription = "unordered list"
        )
        SmallButton(
            onClick = { vm.onNumberedList() },
            imageVector = Icons.Default.FormatListNumbered,
            contentDescription = "list number"
        )
        SmallButton(
            onClick = { vm.onTaskList() },
            imageVector = Icons.Default.Checklist,
            contentDescription = "checklist"
        )


        SmallSeparator()

        SmallButton(
            onClick = {
                textFormatExpanded.value = false
            },
            imageVector = Icons.Default.Close,
            contentDescription = "close"
        )
    }
}
