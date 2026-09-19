package io.github.wiiznokes.gitnote.ui.component.markdown

enum class MdKind {
    H1,
    H2,
    H3,
    H4,
    H5,
    H6,
    BOLD,
    ITALIC,
    BOLD_ITALIC,
    STRIKE,
    HIGHLIGHT,
    INLINE_CODE,
    CODE_FENCE,
    QUOTE,
    BULLET,
    ORDERED,
    TASK_DONE,
    TASK_TODO,
    WIKILINK,
    LINK_TEXT,
    LINK_URL,
    MATH,
}

data class MdSpan(
    val kind: MdKind,
    val range: IntRange,
    val markers: List<IntRange>,
    val line: Int,
    val wikilink: WikilinkParts? = null,
    /** So em [MdKind.MATH]: o texto que substitui a formula na tela. */
    val math: MathText? = null,
)

data class WikilinkParts(
    val target: String,
    val section: String?,
    val alias: String?,
)
