package io.github.wiiznokes.gitnote.ui.screen.app.edit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import io.github.wiiznokes.gitnote.R
import io.github.wiiznokes.gitnote.data.room.Note
import io.github.wiiznokes.gitnote.ui.component.markdown.HeadingAnchor
import io.github.wiiznokes.gitnote.ui.component.markdown.MarkdownLivePreviewTransformation
import io.github.wiiznokes.gitnote.ui.component.markdown.activeMarkdownLines
import io.github.wiiznokes.gitnote.ui.component.markdown.missingWikilinkAnnotator
import io.github.wiiznokes.gitnote.ui.component.markdown.parseWikilinkUri
import io.github.wiiznokes.gitnote.ui.component.markdown.preprocessWikilinksForReading
import io.github.wiiznokes.gitnote.ui.component.markdown.resolveSectionHeading
import io.github.wiiznokes.gitnote.ui.component.markdown.wikilinkNames
import io.github.wiiznokes.gitnote.ui.screen.app.grid.MarkdownCustomInner
import io.github.wiiznokes.gitnote.ui.screen.app.grid.markdownColorsThemed
import io.github.wiiznokes.gitnote.ui.screen.app.grid.markdownTypographyThemed
import io.github.wiiznokes.gitnote.ui.theme.markdownColorScheme
import io.github.wiiznokes.gitnote.ui.viewmodel.edit.MarkDownVM
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun MarkDownContent(
    vm: MarkDownVM,
    textFocusRequester: FocusRequester,
    onFinished: () -> Unit,
    onOpenNote: (Note, String?) -> Unit = { _, _ -> },
    isReadOnlyModeActive: Boolean,
    textContent: TextFieldValue,
) {
    val isMarkdownThemeActive by vm.prefs.isMarkdownThemeActive.getAsState()
    val markdownTheme by vm.prefs.markdownColorTheme.getAsState()
    val colors = markdownColorScheme(markdownTheme)

    if (isReadOnlyModeActive) {
        val scrollState = rememberScrollState()
        val coroutineScope = rememberCoroutineScope()
        val headingPositions = remember(textContent.text) {
            mutableStateMapOf<Int, HeadingAnchor>()
        }
        var containerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
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
        val uriHandler = remember(
            originalUriHandler,
            vm,
            onOpenNote,
            resolvedTargets,
            scrollState,
            coroutineScope,
            headingPositions,
        ) {
            object : UriHandler {
                override fun openUri(uri: String) {
                    val wikilink = parseWikilinkUri(uri)
                    if (wikilink == null) {
                        originalUriHandler.openUri(uri)
                    } else if (wikilink.isSection) {
                        val heading = resolveSectionHeading(wikilink.name, headingPositions.values)
                        if (heading == null) {
                            vm.showSectionNotFound(wikilink.name)
                        } else {
                            coroutineScope.launch {
                                scrollState.animateScrollTo(heading.y.coerceIn(0, scrollState.maxValue))
                            }
                        }
                    } else if (wikilink.isMissing) {
                        vm.showMissingWikilink(wikilink.name)
                    } else {
                        val targetPath = resolvedTargets?.get(wikilink.name)
                        if (targetPath != null) {
                            vm.openResolvedWikilink(
                                relativePath = targetPath,
                                name = wikilink.name,
                                section = wikilink.section,
                                onOpenNote = onOpenNote,
                            )
                        } else if (resolvedTargets != null) {
                            vm.showMissingWikilink(wikilink.name)
                        }
                    }
                }
            }
        }

        val pendingInitialSection = vm.pendingInitialSection()
        LaunchedEffect(pendingInitialSection, renderedContent) {
            if (pendingInitialSection != null) {
                snapshotFlow { headingPositions.values.toList() }
                    .filter { it.isNotEmpty() }
                    .first()
                withFrameNanos { }
                val heading = resolveSectionHeading(
                    pendingInitialSection,
                    headingPositions.values,
                )
                if (heading == null) {
                    vm.showSectionNotFound(pendingInitialSection)
                } else {
                    scrollState.scrollTo(heading.y.coerceIn(0, scrollState.maxValue))
                }
                vm.consumeInitialSection()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { containerCoordinates = it }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
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
                    val annotator = missingWikilinkAnnotator(
                        warningColor = MaterialTheme.colorScheme.error,
                        highlightColor = colors.highlight,
                        highlightBackground = colors.highlightBackground,
                    )

                    SelectionContainer {
                        MarkdownCustomInner(
                            content = renderedContent,
                            colors = readingColors,
                            typography = readingTypography,
                            annotator = annotator,
                            onHeadingPositioned = { text, sourceOffset, coordinates ->
                                val container = containerCoordinates
                                if (container != null && coordinates.isAttached) {
                                    val y = container
                                        .localPositionOf(coordinates, Offset.Zero)
                                        .y + scrollState.value
                                    headingPositions[sourceOffset] = HeadingAnchor(
                                        text = text,
                                        y = y.roundToInt(),
                                        sourceOffset = sourceOffset,
                                    )
                                }
                            },
                            modifier = Modifier.padding(15.dp),
                        )
                    }
                }
            }
            FastScrollOverlay(
                scrollState = scrollState,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
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
private fun FastScrollOverlay(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    var viewportHeight by remember { mutableIntStateOf(0) }
    var visible by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var hideGeneration by remember { mutableIntStateOf(0) }
    val thumbHeight = 56.dp
    val thumbHeightPx = with(LocalDensity.current) { thumbHeight.toPx() }
    val canScroll = scrollState.maxValue != Int.MAX_VALUE &&
        scrollState.maxValue > 0 &&
        viewportHeight > thumbHeightPx
    val trackRange = (viewportHeight - thumbHeightPx).coerceAtLeast(1f)

    fun scrollToFinger(y: Float) {
        if (!canScroll) return
        val fraction = ((y - thumbHeightPx / 2f) / trackRange).coerceIn(0f, 1f)
        coroutineScope.launch {
            scrollState.scrollTo((fraction * scrollState.maxValue).roundToInt())
        }
    }

    LaunchedEffect(visible, dragging, hideGeneration) {
        if (visible && !dragging) {
            delay(1_500)
            visible = false
        }
    }

    Box(
        modifier = modifier
            .width(28.dp)
            .fillMaxHeight()
            .onSizeChanged { viewportHeight = it.height }
            .pointerInput(canScroll, viewportHeight, scrollState.maxValue) {
                if (canScroll) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            visible = true
                            dragging = true
                            scrollToFinger(offset.y)
                        },
                        onDragEnd = {
                            dragging = false
                            hideGeneration++
                        },
                        onDragCancel = {
                            dragging = false
                            hideGeneration++
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            scrollToFinger(change.position.y)
                        },
                    )
                }
            },
    ) {
        val thumbOffset = if (canScroll) {
            (scrollState.value.toFloat() / scrollState.maxValue * trackRange).roundToInt()
        } else {
            0
        }
        AnimatedVisibility(
            visible = visible && canScroll,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, thumbOffset) },
        ) {
            Box(
                modifier = Modifier
                    .width(7.dp)
                    .height(thumbHeight)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.82f),
                        shape = RoundedCornerShape(percent = 50),
                    ),
            )
        }
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
