package io.github.wiiznokes.gitnote.ui.screen.app.edit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
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
import io.github.wiiznokes.gitnote.ui.component.markdown.firstLineAtOrAfter
import io.github.wiiznokes.gitnote.ui.component.markdown.lineOfOffset
import io.github.wiiznokes.gitnote.ui.component.markdown.lineStartOffsets
import io.github.wiiznokes.gitnote.ui.component.markdown.missingWikilinkAnnotator
import io.github.wiiznokes.gitnote.ui.component.markdown.nearestAnchorAtOrBefore
import io.github.wiiznokes.gitnote.ui.component.markdown.nearestLineAtOrBefore
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val fastScrollThumbHeight = 56.dp

/** Quadros que a ancora espera os blocos assentarem na abertura da nota. */
private const val ANCHOR_SETTLE_FRAMES = 30

/** Espera da digitacao parar antes de remedir a linha do cursor. */
private const val ANCHOR_DEBOUNCE_MS = 120L

/** Altura de linha estimada, em multiplos do tamanho da fonte. */
private const val EDIT_LINE_HEIGHT_FACTOR = 1.5f

/** Intervalo minimo entre dois movimentos de cursor durante o arrasto. */
private const val FAST_SCROLL_EMIT_INTERVAL_MS = 90L

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
        val renderedLineStarts = remember(renderedContent) { lineStartOffsets(renderedContent) }
        val blockCoordinates = remember(renderedContent) {
            mutableMapOf<Int, LayoutCoordinates>()
        }
        var lastTouchedLine by remember(renderedContent) { mutableStateOf<Int?>(null) }
        val pendingReadAnchor = remember(textContent.text) { vm.consumeAnchor() }

        fun measuredBlockPositions(): Map<Int, Int> {
            val container = containerCoordinates ?: return emptyMap()
            if (!container.isAttached) return emptyMap()
            return blockCoordinates.mapNotNull { (line, coordinates) ->
                if (!coordinates.isAttached) return@mapNotNull null
                val y = container.localPositionOf(coordinates, Offset.Zero).y + scrollState.value
                line to y.roundToInt()
            }.toMap()
        }

        fun registerBlock(sourceOffset: Int, coordinates: LayoutCoordinates) {
            val line = lineOfOffset(renderedLineStarts, sourceOffset)
            blockCoordinates[line] = coordinates
        }

        // Uma varredura so, depois que os blocos assentam. Fazer isso dentro do
        // registerBlock custava uma passagem no mapa inteiro por bloco: O(n^2) na abertura.
        LaunchedEffect(renderedContent) {
            repeat(ANCHOR_SETTLE_FRAMES) {
                withFrameNanos { }
                if (lastTouchedLine != null) return@LaunchedEffect
                val positions = measuredBlockPositions()
                if (positions.isNotEmpty()) {
                    firstLineAtOrAfter(scrollState.value, positions)?.let(vm::rememberAnchor)
                    return@LaunchedEffect
                }
            }
        }

        LaunchedEffect(pendingReadAnchor, renderedContent) {
            if (pendingReadAnchor != null) {
                snapshotFlow { containerCoordinates }
                    .filter { it != null }
                    .first()
                withFrameNanos { }
                val y = nearestAnchorAtOrBefore(
                    line = pendingReadAnchor,
                    anchors = measuredBlockPositions(),
                ) ?: 0
                scrollState.scrollTo(y.coerceIn(0, scrollState.maxValue))
            }
        }

        LaunchedEffect(renderedContent, scrollState) {
            snapshotFlow { scrollState.value }.collect { scrollY ->
                withFrameNanos { }
                if (lastTouchedLine == null) {
                    val line = firstLineAtOrAfter(scrollY, measuredBlockPositions()) ?: 0
                    vm.rememberAnchor(line)
                }
            }
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
                .pointerInput(renderedContent) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val down = event.changes.firstOrNull {
                                it.pressed && !it.previousPressed
                            } ?: continue
                            val absoluteY = (down.position.y + scrollState.value).roundToInt()
                            val line = nearestLineAtOrBefore(
                                position = absoluteY,
                                anchors = measuredBlockPositions(),
                            ) ?: continue
                            lastTouchedLine = line
                            vm.rememberAnchor(line)
                        }
                    }
                }
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
                                registerBlock(sourceOffset, coordinates)
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
                            onBlockPositioned = ::registerBlock,
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
        val pendingEditAnchor = remember { vm.consumeAnchor() }
        LaunchedEffect(pendingEditAnchor) {
            if (pendingEditAnchor != null) {
                vm.moveCursorToLine(pendingEditAnchor)
                withFrameNanos { }
                textFocusRequester.requestFocus()
            }
        }
        val baseFontSize = MaterialTheme.typography.bodyLarge.fontSize
        var cursorLine by remember { mutableIntStateOf(-1) }
        LaunchedEffect(textContent.text, textContent.selection.start) {
            // A primeira medicao e imediata: trocar de modo depressa nao pode perder o lugar.
            // As seguintes esperam a digitacao parar, para nao varrer a nota a cada tecla.
            if (cursorLine >= 0) delay(ANCHOR_DEBOUNCE_MS)
            val line = lineOfOffset(textContent.text, textContent.selection.start)
            cursorLine = line
            vm.rememberAnchor(line)
        }
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
        val editLineCount = remember(textContent.text) {
            textContent.text.count { it == '\n' } + 1
        }
        val editLineHeight = with(LocalDensity.current) {
            baseFontSize.toPx() * EDIT_LINE_HEIGHT_FACTOR
        }
        Box(modifier = Modifier.fillMaxSize()) {
            GenericTextField(
                vm = vm,
                textFocusRequester = textFocusRequester,
                onFinished = onFinished,
                textContent = textContent,
                visualTransformation = visualTransformation,
            )
            FastScrollLineOverlay(
                lineCount = editLineCount,
                currentLine = cursorLine.coerceAtLeast(0),
                lineHeight = editLineHeight,
                onLineRequested = vm::moveCursorToLine,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun FastScrollOverlay(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    var viewportHeight by remember { mutableIntStateOf(0) }
    val thumbHeightPx = with(LocalDensity.current) { fastScrollThumbHeight.toPx() }
    val thumbOffset = fastScrollThumbOffset(
        scrollValue = scrollState.value,
        viewportHeight = viewportHeight.toFloat(),
        thumbHeight = thumbHeightPx,
        maxValue = scrollState.maxValue,
    )

    fun scrollToFinger(y: Float) {
        val target = fastScrollTargetOffset(
            fingerY = y,
            viewportHeight = viewportHeight.toFloat(),
            thumbHeight = thumbHeightPx,
            maxValue = scrollState.maxValue,
        ) ?: return
        coroutineScope.launch {
            scrollState.scrollTo(target)
        }
    }

    FastScrollGutter(
        canScroll = thumbOffset != null,
        thumbOffset = thumbOffset ?: 0,
        onViewportHeight = { viewportHeight = it },
        onDragStart = { y -> scrollToFinger(y) },
        onDrag = { change -> scrollToFinger(change.position.y) },
        onDragFinished = { },
        gestureKey = listOf(viewportHeight, scrollState.maxValue),
        modifier = modifier,
    )
}

/**
 * A mesma faixa de rolagem do modo leitura, para o modo edicao.
 *
 * O TextField do M3 nao expoe o proprio ScrollState, e icar a rolagem para um pai
 * quebrou o cursor (medido no handoff 07). Entao aqui o arrasto move o cursor, e o
 * TextField rola sozinho atras dele: o mesmo caminho de que a volta ao lugar depende.
 */
@Composable
internal fun FastScrollLineOverlay(
    lineCount: Int,
    currentLine: Int,
    lineHeight: Float,
    onLineRequested: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var viewportHeight by remember { mutableIntStateOf(0) }
    var previewLine by remember { mutableStateOf<Int?>(null) }
    var pendingLine by remember { mutableStateOf<Int?>(null) }
    var lastEmitMs by remember { mutableLongStateOf(0L) }
    val thumbHeightPx = with(LocalDensity.current) { fastScrollThumbHeight.toPx() }
    val visibleLines = estimatedVisibleLines(viewportHeight.toFloat(), lineHeight)

    fun lineAt(y: Float): Int? = fastScrollLineTarget(
        fingerY = y,
        viewportHeight = viewportHeight.toFloat(),
        thumbHeight = thumbHeightPx,
        lineCount = lineCount,
        visibleLines = visibleLines,
    )

    val thumbOffset = fastScrollLineThumbOffset(
        line = previewLine ?: currentLine,
        lineCount = lineCount,
        viewportHeight = viewportHeight.toFloat(),
        thumbHeight = thumbHeightPx,
        visibleLines = visibleLines,
    )

    FastScrollGutter(
        canScroll = thumbOffset != null,
        thumbOffset = thumbOffset ?: 0,
        onViewportHeight = { viewportHeight = it },
        onDragStart = { y ->
            val line = lineAt(y)
            if (line != null) {
                previewLine = line
                pendingLine = line
                lastEmitMs = 0L
                onLineRequested(line)
            }
        },
        onDrag = { change ->
            val line = lineAt(change.position.y)
            if (line != null) {
                previewLine = line
                pendingLine = line
                // Cada movimento do cursor refaz o live preview da nota inteira
                // (27 ms numa nota de 1.946 linhas). Sem esta redea o arrasto engasga.
                if (change.uptimeMillis - lastEmitMs >= FAST_SCROLL_EMIT_INTERVAL_MS) {
                    lastEmitMs = change.uptimeMillis
                    onLineRequested(line)
                }
            }
        },
        onDragFinished = {
            pendingLine?.let(onLineRequested)
            pendingLine = null
            previewLine = null
        },
        gestureKey = listOf(viewportHeight, lineCount, visibleLines),
        modifier = modifier,
    )
}

/**
 * A faixa de 28dp na borda direita: so captura depois do toque longo, para nao roubar
 * o toque do que esta embaixo (posicionar o cursor, no editor; wikilink, na leitura).
 */
@Composable
private fun FastScrollGutter(
    canScroll: Boolean,
    thumbOffset: Int,
    onViewportHeight: (Int) -> Unit,
    onDragStart: (Float) -> Unit,
    onDrag: (PointerInputChange) -> Unit,
    onDragFinished: () -> Unit,
    gestureKey: Any?,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var hideGeneration by remember { mutableIntStateOf(0) }

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
            .onSizeChanged { onViewportHeight(it.height) }
            .pointerInput(canScroll, gestureKey) {
                if (canScroll) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            visible = true
                            dragging = true
                            onDragStart(offset.y)
                        },
                        onDragEnd = {
                            dragging = false
                            hideGeneration++
                            onDragFinished()
                        },
                        onDragCancel = {
                            dragging = false
                            hideGeneration++
                            onDragFinished()
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            onDrag(change)
                        },
                    )
                }
            },
    ) {
        AnimatedVisibility(
            visible = visible && canScroll,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, thumbOffset) },
        ) {
            Box(
                modifier = Modifier
                    .width(7.dp)
                    .height(fastScrollThumbHeight)
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
            .horizontalScroll(rememberScrollState(initial = 0)),
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

        TableActionButton(
            currentValue = { vm.content.value },
            onInsert = vm::onTableInsert,
            onResize = vm::onTableResize,
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
