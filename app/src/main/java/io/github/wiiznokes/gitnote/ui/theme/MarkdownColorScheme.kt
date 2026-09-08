package io.github.wiiznokes.gitnote.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.wiiznokes.gitnote.MyApp
import io.github.wiiznokes.gitnote.R

enum class MarkdownTheme {
    MATERIAL,
    OBSIDIAN,
    SOLARIZED;

    override fun toString(): String {
        val resource = when (this) {
            MATERIAL -> R.string.markdown_theme_material
            OBSIDIAN -> R.string.markdown_theme_obsidian
            SOLARIZED -> R.string.markdown_theme_solarized
        }
        return MyApp.appModule.uiHelper.getString(resource)
    }
}

data class MarkdownColorScheme(
    val h1: Color,
    val h2: Color,
    val h3: Color,
    val h4: Color,
    val emphasis: Color,
    val emphasis2: Color,
    val code: Color,
    val codeBackground: Color,
    val quote: Color,
    val listMarker: Color,
    val link: Color,
    val highlight: Color,
    val highlightBackground: Color,
)

@Composable
fun markdownColorScheme(theme: MarkdownTheme): MarkdownColorScheme {
    val material = MaterialTheme.colorScheme
    val isDark = material.background.luminance() < 0.5f

    return when (theme) {
        MarkdownTheme.MATERIAL -> materialMarkdownColorScheme(material)
        MarkdownTheme.OBSIDIAN -> obsidianMarkdownColorScheme(isDark)
        MarkdownTheme.SOLARIZED -> solarizedMarkdownColorScheme(isDark)
    }
}

private fun materialMarkdownColorScheme(colors: ColorScheme) = MarkdownColorScheme(
    h1 = colors.primary,
    h2 = colors.secondary,
    h3 = colors.tertiary,
    h4 = colors.primary,
    emphasis = colors.tertiary,
    emphasis2 = colors.secondary,
    code = colors.onSecondaryContainer,
    codeBackground = colors.secondaryContainer.copy(alpha = 0.55f),
    quote = colors.secondary,
    listMarker = colors.tertiary,
    link = colors.primary,
    highlight = colors.onTertiaryContainer,
    highlightBackground = colors.tertiaryContainer.copy(alpha = 0.7f),
)

private fun obsidianMarkdownColorScheme(isDark: Boolean) = if (isDark) {
    MarkdownColorScheme(
        h1 = Color(0xFF82AAFF),
        h2 = Color(0xFF89DDFF),
        h3 = Color(0xFFC792EA),
        h4 = Color(0xFF80CBC4),
        emphasis = Color(0xFFF78C6C),
        emphasis2 = Color(0xFFFFCB6B),
        code = Color(0xFFC3E88D),
        codeBackground = Color(0xFF2D333B),
        quote = Color(0xFFB0BEC5),
        listMarker = Color(0xFFFFA726),
        link = Color(0xFF80CBC4),
        highlight = Color(0xFFFFE082),
        highlightBackground = Color(0xFF5D4E20),
    )
} else {
    MarkdownColorScheme(
        h1 = Color(0xFF2457A7),
        h2 = Color(0xFF007C91),
        h3 = Color(0xFF7B3FA1),
        h4 = Color(0xFF26766F),
        emphasis = Color(0xFFB63B1E),
        emphasis2 = Color(0xFF8A5B00),
        code = Color(0xFF2E6B22),
        codeBackground = Color(0xFFE7ECEF),
        quote = Color(0xFF52606D),
        listMarker = Color(0xFFC45A00),
        link = Color(0xFF006B73),
        highlight = Color(0xFF5D4300),
        highlightBackground = Color(0xFFFFE8A3),
    )
}

private fun solarizedMarkdownColorScheme(isDark: Boolean) = if (isDark) {
    MarkdownColorScheme(
        h1 = Color(0xFF268BD2),
        h2 = Color(0xFF2AA198),
        h3 = Color(0xFF6C71C4),
        h4 = Color(0xFF859900),
        emphasis = Color(0xFFCB4B16),
        emphasis2 = Color(0xFFB58900),
        code = Color(0xFF859900),
        codeBackground = Color(0xFF073642),
        quote = Color(0xFF93A1A1),
        listMarker = Color(0xFFB58900),
        link = Color(0xFF2AA198),
        highlight = Color(0xFFFDF6E3),
        highlightBackground = Color(0xFF586E75),
    )
} else {
    MarkdownColorScheme(
        h1 = Color(0xFF006FAD),
        h2 = Color(0xFF167A72),
        h3 = Color(0xFF5E63B6),
        h4 = Color(0xFF697D00),
        emphasis = Color(0xFFB33A0E),
        emphasis2 = Color(0xFF8F6B00),
        code = Color(0xFF5F7000),
        codeBackground = Color(0xFFEEE8D5),
        quote = Color(0xFF586E75),
        listMarker = Color(0xFF9B7200),
        link = Color(0xFF087E75),
        highlight = Color(0xFF3B4A4E),
        highlightBackground = Color(0xFFFFE9A6),
    )
}
