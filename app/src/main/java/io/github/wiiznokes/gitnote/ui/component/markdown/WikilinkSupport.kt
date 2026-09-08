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
import com.mikepenz.markdown.model.MarkdownAnnotator
import com.mikepenz.markdown.model.markdownAnnotator
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

data class WikilinkUri(
    val name: String,
    val isMissing: Boolean,
)

fun wikilinkNames(source: String): Set<String> = MarkdownScanner.scan(source)
    .asSequence()
    .filter { it.kind == MdKind.WIKILINK }
    .map { source.substring(it.range) }
    .toSet()

fun preprocessWikilinksForReading(
    source: String,
    existingNames: Set<String>? = null,
): String {
    val wikilinks = MarkdownScanner.scan(source)
        .filter { it.kind == MdKind.WIKILINK }
        .sortedBy { it.range.first }
    if (wikilinks.isEmpty()) return source

    return buildString(source.length + wikilinks.size * 24) {
        var sourceOffset = 0
        wikilinks.forEach { wikilink ->
            val fullStart = wikilink.markers.first().first
            val fullEnd = wikilink.markers.last().last + 1
            val name = source.substring(wikilink.range)
            val isMissing = existingNames != null && name !in existingNames

            append(source, sourceOffset, fullStart)
            append('[')
            append(escapeMarkdownLinkText(name))
            append("](")
            append(wikilinkUri(name, isMissing))
            append(')')
            sourceOffset = fullEnd
        }
        append(source, sourceOffset, source.length)
    }
}

fun parseWikilinkUri(value: String): WikilinkUri? {
    val uri = runCatching { URI(value) }.getOrNull() ?: return null
    if (!uri.scheme.equals(WIKILINK_SCHEME, ignoreCase = true)) return null

    val isMissing = when {
        uri.host.equals(WIKILINK_HOST, ignoreCase = true) -> false
        uri.host.equals(MISSING_WIKILINK_HOST, ignoreCase = true) -> true
        else -> return null
    }
    val encodedName = uri.rawQuery
        ?.split('&')
        ?.firstNotNullOfOrNull { part ->
            val separator = part.indexOf('=')
            if (separator > 0 && part.substring(0, separator) == "name") {
                part.substring(separator + 1)
            } else {
                null
            }
        }
        ?: return null
    val name = runCatching {
        URLDecoder.decode(encodedName, StandardCharsets.UTF_8.name())
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null

    return WikilinkUri(name = name, isMissing = isMissing)
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
fun missingWikilinkAnnotator(warningColor: Color): MarkdownAnnotator {
    val uriHandler = LocalUriHandler.current
    return remember(uriHandler, warningColor) {
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

        markdownAnnotator { content, child ->
            if (child.type != MarkdownElementTypes.INLINE_LINK) {
                return@markdownAnnotator false
            }
            val destination = child.findChildOfType(MarkdownElementTypes.LINK_DESTINATION)
                ?.getUnescapedTextInNode(content)
                ?: return@markdownAnnotator false
            val wikilink = parseWikilinkUri(destination)
                ?.takeIf { it.isMissing }
                ?: return@markdownAnnotator false

            withLink(
                LinkAnnotation.Url(
                    url = destination,
                    styles = linkStyles,
                    linkInteractionListener = linkInteractionListener,
                )
            ) {
                append(wikilink.name)
            }
            true
        }
    }
}

private fun wikilinkUri(name: String, isMissing: Boolean): String {
    val host = if (isMissing) MISSING_WIKILINK_HOST else WIKILINK_HOST
    val encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8.name())
        .replace("+", "%20")
    return "$WIKILINK_SCHEME://$host?name=$encodedName"
}

private fun escapeMarkdownLinkText(value: String): String = buildString(value.length) {
    value.forEach { character ->
        if (character == '\\' || character == '[' || character == ']') append('\\')
        append(character)
    }
}
