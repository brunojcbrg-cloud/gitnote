package io.github.wiiznokes.gitnote.ui.component.markdown

import androidx.compose.ui.text.AnnotatedString
import com.mikepenz.markdown.model.MarkdownAnnotator
import com.mikepenz.markdown.model.MarkdownAnnotatorConfig
import com.mikepenz.markdown.model.markdownAnnotator
import com.mikepenz.markdown.model.markdownAnnotatorConfig
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode

/**
 * Restores the renderer's pre-0.32 behavior for soft line breaks without changing the source.
 */
internal val obsidianLineBreaksAnnotatorConfig: MarkdownAnnotatorConfig =
    markdownAnnotatorConfig(eolAsNewLine = true)

/**
 * JetBrains Markdown 0.7.5 tokenizes CRLF as a carriage-return whitespace followed by EOL.
 * Consume only that parser artifact so the native EOL option emits exactly one line break.
 */
internal fun obsidianLineBreaksAnnotator(
    annotate: AnnotatedString.Builder.(content: String, child: ASTNode) -> Boolean = { _, _ ->
        false
    },
): MarkdownAnnotator = markdownAnnotator(
    config = obsidianLineBreaksAnnotatorConfig,
) { content, child ->
    if (child.isCarriageReturnBeforeLineFeed(content)) {
        true
    } else {
        annotate.invoke(this, content, child)
    }
}

private fun ASTNode.isCarriageReturnBeforeLineFeed(content: String): Boolean =
    type == MarkdownTokenTypes.WHITE_SPACE &&
        getTextInNode(content).toString() == "\r" &&
        endOffset < content.length &&
        content[endOffset] == '\n' &&
        generateSequence(parent) { it.parent }.none {
            it.type == MarkdownElementTypes.CODE_FENCE || it.type == MarkdownElementTypes.CODE_SPAN
        }
