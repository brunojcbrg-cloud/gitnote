package io.github.wiiznokes.gitnote.ui.component.markdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import com.mikepenz.markdown.model.MarkdownAnnotator
import com.mikepenz.markdown.utils.getUnescapedTextInNode
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.findChildOfType
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

private const val WIKILINK_SCHEME = "gitnote"
private const val WIKILINK_HOST = "note"
private const val MISSING_WIKILINK_HOST = "missing-note"
private const val SECTION_WIKILINK_HOST = "section"
private const val HIGHLIGHT_HOST = "highlight"
private const val HIGHLIGHT_URI = "$WIKILINK_SCHEME://$HIGHLIGHT_HOST"

data class WikilinkUri(
    val name: String,
    val isMissing: Boolean,
    val isSection: Boolean,
    val section: String? = null,
)

data class HeadingAnchor(
    val text: String,
    val y: Int,
    val sourceOffset: Int = 0,
)

fun normalizeSectionHeading(value: String): String = value
    .trim()
    .replace(Regex("[ \\t]+"), " ")
    .lowercase(Locale.ROOT)

fun resolveSectionHeading(section: String, headings: Collection<HeadingAnchor>): HeadingAnchor? {
    val ordered = headings.sortedWith(compareBy<HeadingAnchor>({ it.sourceOffset }, { it.y }))
    val normalizedSection = normalizeSectionHeading(section)
    ordered.firstOrNull { normalizeSectionHeading(it.text) == normalizedSection }?.let { return it }

    val withoutSpaces = normalizedSection.replace(" ", "")
    return ordered.filter {
        normalizeSectionHeading(it.text).replace(" ", "") == withoutSpaces
    }.singleOrNull()
}

fun wikilinkNames(source: String): Set<String> = MarkdownScanner.scan(source)
    .asSequence()
    .filter { it.kind == MdKind.WIKILINK }
    .mapNotNull { it.wikilink?.target?.takeIf { target -> target.isNotEmpty() } }
    .toSet()

fun preprocessWikilinksForReading(
    source: String,
    existingNames: Set<String>? = null,
): String {
    val replacements = MarkdownScanner.scan(source)
        .filter { it.kind == MdKind.WIKILINK || it.kind == MdKind.HIGHLIGHT }
        .sortedBy { it.range.first }
    if (replacements.isEmpty()) return source

    return buildString(source.length + replacements.size * 24) {
        var sourceOffset = 0
        replacements.forEach { span ->
            val fullStart = span.markers.first().first
            val fullEnd = span.markers.last().last + 1
            val displayText = source.substring(span.range)

            append(source, sourceOffset, fullStart)
            append('[')
            append(escapeMarkdownLinkText(displayText))
            append("](")
            if (span.kind == MdKind.HIGHLIGHT) {
                append(HIGHLIGHT_URI)
            } else {
                val parts = checkNotNull(span.wikilink)
                val isSection = parts.target.isEmpty()
                val name = if (isSection) checkNotNull(parts.section) else parts.target
                val isMissing = !isSection && existingNames != null && name !in existingNames
                append(wikilinkUri(name, isMissing, isSection, parts.section))
            }
            append(')')
            sourceOffset = fullEnd
        }
        append(source, sourceOffset, source.length)
    }
}

fun parseWikilinkUri(value: String): WikilinkUri? {
    val uri = runCatching { URI(value) }.getOrNull() ?: return null
    if (!uri.scheme.equals(WIKILINK_SCHEME, ignoreCase = true)) return null

    val type = when {
        uri.host.equals(WIKILINK_HOST, ignoreCase = true) -> false to false
        uri.host.equals(MISSING_WIKILINK_HOST, ignoreCase = true) -> true to false
        uri.host.equals(SECTION_WIKILINK_HOST, ignoreCase = true) -> false to true
        else -> return null
    }
    val name = queryValue(uri.rawQuery, "name")?.takeIf { it.isNotBlank() } ?: return null
    val section = queryValue(uri.rawQuery, "section")?.takeIf { it.isNotBlank() }

    return WikilinkUri(
        name = name,
        isMissing = type.first,
        isSection = type.second,
        section = if (type.second) name else section,
    )
}

fun resolveWikilinkTargets(
    names: Set<String>,
    currentParentPath: String,
    candidatePaths: List<String>,
): Map<String, String?> {
    val candidatesByName = candidatePaths
        .asSequence()
        .mapNotNull { path ->
            val fullName = path.substringAfterLast('/')
            if (!fullName.endsWith(".md", ignoreCase = true)) return@mapNotNull null
            fullName.dropLast(3).lowercase(Locale.ROOT) to path
        }
        .groupBy(keySelector = { it.first }, valueTransform = { it.second })

    return names.associateWith { name ->
        candidatesByName[name.lowercase(Locale.ROOT)]?.minWithOrNull(
            compareBy<String>(
                {
                    val parentPath = it.substringBeforeLast('/', missingDelimiterValue = "")
                    if (parentPath == currentParentPath) 0 else 1
                },
                { it.lowercase(Locale.ROOT) },
                { it },
            )
        )
    }
}

@Composable
fun missingWikilinkAnnotator(
    warningColor: Color,
    highlightColor: Color,
    highlightBackground: Color,
): MarkdownAnnotator {
    val uriHandler = LocalUriHandler.current
    return remember(uriHandler, warningColor, highlightColor, highlightBackground) {
        val linkStyles = TextLinkStyles(
            style = SpanStyle(
                color = warningColor,
                fontWeight = FontWeight.Bold,
                textDecoration = TextDecoration.Underline,
            )
        )
        val linkInteractionListener = LinkInteractionListener { link ->
            (link as? LinkAnnotation.Url)?.url?.let(uriHandler::openUri)
        }

        obsidianLineBreaksAnnotator { content, child ->
            if (child.type != MarkdownElementTypes.INLINE_LINK) {
                return@obsidianLineBreaksAnnotator false
            }
            val destination = child.findChildOfType(MarkdownElementTypes.LINK_DESTINATION)
                ?.getUnescapedTextInNode(content)
                ?: return@obsidianLineBreaksAnnotator false
            val displayText = child.findChildOfType(MarkdownElementTypes.LINK_TEXT)
                ?.getUnescapedTextInNode(content)
                ?.removePrefix("[")
                ?.removeSuffix("]")

            if (destination == HIGHLIGHT_URI) {
                withStyle(
                    SpanStyle(
                        color = highlightColor,
                        background = highlightBackground,
                    )
                ) {
                    append(displayText.orEmpty())
                }
                return@obsidianLineBreaksAnnotator true
            }
            val wikilink = parseWikilinkUri(destination)
                ?.takeIf { it.isMissing }
                ?: return@obsidianLineBreaksAnnotator false

            withLink(
                LinkAnnotation.Url(
                    url = destination,
                    styles = linkStyles,
                    linkInteractionListener = linkInteractionListener,
                )
            ) {
                append(displayText ?: wikilink.name)
            }
            true
        }
    }
}

private fun wikilinkUri(
    name: String,
    isMissing: Boolean,
    isSection: Boolean,
    section: String?,
): String {
    val host = when {
        isSection -> SECTION_WIKILINK_HOST
        isMissing -> MISSING_WIKILINK_HOST
        else -> WIKILINK_HOST
    }
    val query = buildList {
        add("name=" + encodeQueryValue(name))
        if (!isSection && !section.isNullOrBlank()) add("section=" + encodeQueryValue(section))
    }.joinToString("&")
    return "$WIKILINK_SCHEME://$host?$query"
}

private fun queryValue(query: String?, key: String): String? {
    val encoded = query
        ?.split('&')
        ?.firstNotNullOfOrNull { part ->
            val separator = part.indexOf('=')
            if (separator > 0 && part.substring(0, separator) == key) {
                part.substring(separator + 1)
            } else {
                null
            }
        }
        ?: return null
    return runCatching {
        URLDecoder.decode(encoded, StandardCharsets.UTF_8.name())
    }.getOrNull()
}

private fun encodeQueryValue(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

private fun escapeMarkdownLinkText(value: String): String = buildString(value.length) {
    value.forEach { character ->
        if (character == '\\' || character == '[' || character == ']') append('\\')
        append(character)
    }
}
