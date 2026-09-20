package io.github.wiiznokes.gitnote.ui.screen.app.grid

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.mikepenz.markdown.compose.MarkdownSuccess
import com.mikepenz.markdown.compose.components.CurrentComponentsBridge
import com.mikepenz.markdown.compose.components.MarkdownComponent
import com.mikepenz.markdown.compose.components.MarkdownComponents
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.m3.elements.MarkdownCheckBox
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.ImageTransformer
import com.mikepenz.markdown.model.MarkdownAnimations
import com.mikepenz.markdown.model.MarkdownAnnotator
import com.mikepenz.markdown.model.MarkdownColors
import com.mikepenz.markdown.model.MarkdownDimens
import com.mikepenz.markdown.model.MarkdownExtendedSpans
import com.mikepenz.markdown.model.MarkdownInlineContent
import com.mikepenz.markdown.model.MarkdownPadding
import com.mikepenz.markdown.model.MarkdownTypography
import com.mikepenz.markdown.model.NoOpImageTransformerImpl
import com.mikepenz.markdown.model.ReferenceLinkHandler
import com.mikepenz.markdown.model.ReferenceLinkHandlerImpl
import com.mikepenz.markdown.model.State
import com.mikepenz.markdown.model.markdownAnimations
import com.mikepenz.markdown.model.markdownAnnotator
import com.mikepenz.markdown.model.markdownDimens
import com.mikepenz.markdown.model.markdownExtendedSpans
import com.mikepenz.markdown.model.markdownInlineContent
import com.mikepenz.markdown.model.markdownPadding
import com.mikepenz.markdown.utils.getUnescapedTextInNode
import io.github.wiiznokes.gitnote.ui.theme.MarkdownColorScheme
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser


@Composable
fun MarkdownCustomInner(
    content: String,
    colors: MarkdownColors = markdownColor(),
    typography: MarkdownTypography = markdownTypographyScaled(),
    modifier: Modifier = Modifier.fillMaxSize(),
    padding: MarkdownPadding = markdownPadding(),
    dimens: MarkdownDimens = markdownDimens(),
    flavour: MarkdownFlavourDescriptor = GFMFlavourDescriptor(),
    parser: MarkdownParser = MarkdownParser(flavour),
    imageTransformer: ImageTransformer = NoOpImageTransformerImpl(),
    annotator: MarkdownAnnotator = markdownAnnotator(),
    extendedSpans: MarkdownExtendedSpans = markdownExtendedSpans(),
    inlineContent: MarkdownInlineContent = markdownInlineContent(),
    onHeadingPositioned: ((text: String, sourceOffset: Int, coordinates: LayoutCoordinates) -> Unit)? = null,
    onBlockPositioned: ((sourceOffset: Int, coordinates: LayoutCoordinates) -> Unit)? = null,
    onHeadingCollapseToggle: ((sourceOffset: Int) -> Unit)? = null,
    isHeadingCollapsed: ((sourceOffset: Int) -> Boolean)? = null,
    components: MarkdownComponents = markdownComponentsWithHeadingPositions(
        onHeadingPositioned,
        onBlockPositioned,
        onHeadingCollapseToggle,
        isHeadingCollapsed,
    ),
    animations: MarkdownAnimations = markdownAnimations(),
    referenceLinkHandler: ReferenceLinkHandler = ReferenceLinkHandlerImpl(),
    lookupLinks: Boolean = true,
    loading: @Composable (modifier: Modifier) -> Unit = { Box(modifier) },
    success: @Composable (state: State.Success, components: MarkdownComponents, modifier: Modifier) -> Unit = { state, components, modifier ->
        MarkdownSuccess(state = state, components = components, modifier = modifier)
    },
    error: @Composable (modifier: Modifier) -> Unit = { Box(modifier) },
) = com.mikepenz.markdown.compose.Markdown(
    content = content,
    colors = colors,
    typography = typography,
    modifier = modifier,
    padding = padding,
    dimens = dimens,
    flavour = flavour,
    parser = parser,
    imageTransformer = imageTransformer,
    annotator = annotator,
    extendedSpans = extendedSpans,
    inlineContent = inlineContent,
    components = components,
    animations = animations,
    referenceLinkHandler = referenceLinkHandler,
    lookupLinks = lookupLinks,
    loading = loading,
    success = success,
    error = error,
)

private fun markdownComponentsWithHeadingPositions(
    onHeadingPositioned: ((String, Int, LayoutCoordinates) -> Unit)?,
    onBlockPositioned: ((Int, LayoutCoordinates) -> Unit)?,
    onHeadingCollapseToggle: ((Int) -> Unit)? = null,
    isHeadingCollapsed: ((Int) -> Boolean)? = null,
): MarkdownComponents {
    val checkbox: MarkdownComponent = {
        MarkdownCheckBox(
            it.content,
            it.node,
            it.typography.text,
        )
    }
    fun heading(delegate: MarkdownComponent) =
        positionedHeading(delegate, onHeadingPositioned, onHeadingCollapseToggle, isHeadingCollapsed)
    return markdownComponents(
        codeFence = positionedBlock(CurrentComponentsBridge.codeFence, onBlockPositioned),
        codeBlock = positionedBlock(CurrentComponentsBridge.codeBlock, onBlockPositioned),
        heading1 = heading(CurrentComponentsBridge.heading1),
        heading2 = heading(CurrentComponentsBridge.heading2),
        heading3 = heading(CurrentComponentsBridge.heading3),
        heading4 = heading(CurrentComponentsBridge.heading4),
        heading5 = heading(CurrentComponentsBridge.heading5),
        heading6 = heading(CurrentComponentsBridge.heading6),
        blockQuote = positionedBlock(CurrentComponentsBridge.blockQuote, onBlockPositioned),
        paragraph = positionedBlock(CurrentComponentsBridge.paragraph, onBlockPositioned),
        orderedList = positionedBlock(CurrentComponentsBridge.orderedList, onBlockPositioned),
        unorderedList = positionedBlock(CurrentComponentsBridge.unorderedList, onBlockPositioned),
        table = positionedBlock(CurrentComponentsBridge.table, onBlockPositioned),
        checkbox = positionedBlock(checkbox, onBlockPositioned),
    )
}

private fun positionedHeading(
    delegate: MarkdownComponent,
    onHeadingPositioned: ((String, Int, LayoutCoordinates) -> Unit)?,
    onHeadingCollapseToggle: ((Int) -> Unit)?,
    isHeadingCollapsed: ((Int) -> Boolean)?,
): MarkdownComponent {
    if (onHeadingPositioned == null && onHeadingCollapseToggle == null) return delegate
    return { model ->
        val text = model.node.findChildOfType(MarkdownTokenTypes.ATX_CONTENT)
            ?.getUnescapedTextInNode(model.content)
        val offset = model.node.startOffset
        Box(
            modifier = Modifier.onGloballyPositioned { coordinates ->
                if (text != null) onHeadingPositioned?.invoke(text, offset, coordinates)
            }
        ) {
            if (onHeadingCollapseToggle != null) {
                val recolhido = isHeadingCollapsed?.invoke(offset) == true
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onHeadingCollapseToggle(offset) },
                ) {
                    Icon(
                        imageVector = if (recolhido) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        delegate(model)
                    }
                }
            } else {
                delegate(model)
            }
        }
    }
}

private fun positionedBlock(
    delegate: MarkdownComponent,
    onBlockPositioned: ((Int, LayoutCoordinates) -> Unit)?,
): MarkdownComponent {
    val callback = onBlockPositioned ?: return delegate
    return { model ->
        Box(
            modifier = Modifier.onGloballyPositioned { coordinates ->
                callback(model.node.startOffset, coordinates)
            }
        ) {
            delegate(model)
        }
    }
}


private fun TextStyle.scaled(scale: Float): TextStyle = copy(
    fontSize = fontSize.scale(scale),
    lineHeight = lineHeight.scale(scale),
    letterSpacing = letterSpacing.scale(scale)
)

private fun TextUnit.scale(scale: Float): TextUnit =
    if (this.isSpecified) this * scale else this

@Composable
fun markdownTypographyScaled(
    scale: Float = 0.85f
): MarkdownTypography = markdownTypography(
    h1 = MaterialTheme.typography.headlineLarge,
    h2 = MaterialTheme.typography.headlineMedium,
    h3 = MaterialTheme.typography.headlineSmall,
    h4 = MaterialTheme.typography.titleLarge,
    h5 = MaterialTheme.typography.titleMedium,
    h6 = MaterialTheme.typography.titleSmall,
    text = MaterialTheme.typography.bodyLarge.scaled(scale),
    code = MaterialTheme.typography.bodyMedium
        .scaled(scale)
        .copy(fontFamily = FontFamily.Monospace),
    inlineCode = MaterialTheme.typography.bodyLarge
        .scaled(scale)
        .copy(fontFamily = FontFamily.Monospace),
    quote = MaterialTheme.typography.bodyMedium
        .scaled(scale)
        .plus(SpanStyle(fontStyle = FontStyle.Italic)),
    paragraph = MaterialTheme.typography.bodyLarge.scaled(scale),
    ordered = MaterialTheme.typography.bodyLarge.scaled(scale),
    bullet = MaterialTheme.typography.bodyLarge.scaled(scale),
    list = MaterialTheme.typography.bodyLarge.scaled(scale),
    table = MaterialTheme.typography.bodyLarge.scaled(scale),
    textLink = TextLinkStyles(
        style = MaterialTheme.typography.bodyLarge
            .scaled(scale)
            .copy(
                fontWeight = FontWeight.Bold,
                textDecoration = TextDecoration.Underline
            )
            .toSpanStyle()
    )
)

@Composable
fun markdownColorsThemed(
    colors: MarkdownColorScheme,
): MarkdownColors = markdownColor(
    codeBackground = colors.codeBackground,
    inlineCodeBackground = colors.codeBackground,
)

@Composable
fun markdownTypographyThemed(
    colors: MarkdownColorScheme,
    scale: Float = 1f,
): MarkdownTypography = markdownTypography(
    h1 = MaterialTheme.typography.headlineLarge.copy(color = colors.h1),
    h2 = MaterialTheme.typography.headlineMedium.copy(color = colors.h2),
    h3 = MaterialTheme.typography.headlineSmall.copy(color = colors.h3),
    h4 = MaterialTheme.typography.titleLarge.copy(color = colors.h4),
    h5 = MaterialTheme.typography.titleMedium.copy(color = colors.h4),
    h6 = MaterialTheme.typography.titleSmall.copy(color = colors.h4),
    text = MaterialTheme.typography.bodyLarge.scaled(scale),
    code = MaterialTheme.typography.bodyMedium
        .scaled(scale)
        .copy(
            color = colors.code,
            fontFamily = FontFamily.Monospace,
        ),
    inlineCode = MaterialTheme.typography.bodyLarge
        .scaled(scale)
        .copy(
            color = colors.code,
            fontFamily = FontFamily.Monospace,
        ),
    quote = MaterialTheme.typography.bodyMedium
        .scaled(scale)
        .copy(color = colors.quote)
        .plus(SpanStyle(fontStyle = FontStyle.Italic)),
    paragraph = MaterialTheme.typography.bodyLarge.scaled(scale),
    ordered = MaterialTheme.typography.bodyLarge
        .scaled(scale)
        .copy(color = colors.listMarker),
    bullet = MaterialTheme.typography.bodyLarge
        .scaled(scale)
        .copy(color = colors.listMarker),
    list = MaterialTheme.typography.bodyLarge.scaled(scale),
    table = MaterialTheme.typography.bodyLarge.scaled(scale),
    textLink = TextLinkStyles(
        style = MaterialTheme.typography.bodyLarge
            .scaled(scale)
            .copy(
                color = colors.link,
                fontWeight = FontWeight.Bold,
                textDecoration = TextDecoration.Underline,
            )
            .toSpanStyle(),
    ),
)
