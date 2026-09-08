package io.github.wiiznokes.gitnote.ui.component.markdown

import com.mikepenz.markdown.model.MarkdownAnnotatorConfig
import com.mikepenz.markdown.model.markdownAnnotatorConfig

/**
 * Restores the renderer's pre-0.32 behavior for soft line breaks without changing the source.
 */
internal val obsidianLineBreaksAnnotatorConfig: MarkdownAnnotatorConfig =
    markdownAnnotatorConfig(eolAsNewLine = true)
