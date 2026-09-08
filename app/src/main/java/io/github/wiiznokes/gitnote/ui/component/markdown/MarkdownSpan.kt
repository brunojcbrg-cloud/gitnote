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
    LINK_TEXT,
    LINK_URL,
}

data class MdSpan(
    val kind: MdKind,
    val range: IntRange,
    val markers: List<IntRange>,
    val line: Int,
)
