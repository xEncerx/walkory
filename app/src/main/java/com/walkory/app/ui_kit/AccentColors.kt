package com.walkory.app.ui_kit

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.walkory.app.domain.Accent

private data class AccentTones(val light: Color, val lightContainer: Color, val dark: Color, val darkContainer: Color)
private val AccentPalettes = mapOf(
    Accent.TEAL to AccentTones(Color(0xFF00696E), Color(0xFF9CF1F6), Color(0xFF83D4D8), Color(0xFF0B4F52)),
    Accent.BLUE to AccentTones(Color(0xFF175EA8), Color(0xFFD4E3FF), Color(0xFFA5C8FF), Color(0xFF004780)),
    Accent.GREEN to AccentTones(Color(0xFF326B35), Color(0xFFB5F2B1), Color(0xFF9BD597), Color(0xFF185221)),
    Accent.PURPLE to AccentTones(Color(0xFF74519B), Color(0xFFEEDBFF), Color(0xFFDBB8FF), Color(0xFF5B3981)),
    Accent.CORAL to AccentTones(Color(0xFF9D423B), Color(0xFFFFDAD5), Color(0xFFFFB4A9), Color(0xFF7E2B25)),
    Accent.AMBER to AccentTones(Color(0xFF785900), Color(0xFFFFDF99), Color(0xFFF3C24B), Color(0xFF5B4200)),
    Accent.MONOCHROME to AccentTones(Color(0xFF5E5E5E), Color(0xFFE2E2E2), Color(0xFFC6C6C6), Color(0xFF464646)),
)

internal fun ColorScheme.withAccent(accent: Accent, dark: Boolean): ColorScheme {
    val tones = AccentPalettes.getValue(accent)
    val main = if (dark) tones.dark else tones.light
    val container = if (dark) tones.darkContainer else tones.lightContainer
    return copy(
        primary = main, onPrimary = main.readableText(), primaryContainer = container, onPrimaryContainer = container.readableText(),
        secondary = main, onSecondary = main.readableText(), secondaryContainer = container, onSecondaryContainer = container.readableText(),
        tertiary = main, onTertiary = main.readableText(), tertiaryContainer = container, onTertiaryContainer = container.readableText(),
        inversePrimary = if (dark) tones.light else tones.dark,
    )
}

// Choose the stronger black/white pair, including for wallpaper-derived colors.
private fun Color.readableText(): Color = if (luminance() > 0.179f) Color.Black else Color.White

internal fun ColorScheme.withHigherContrast(dark: Boolean): ColorScheme {
    val ink = if (dark) Color.White else Color.Black
    return copy(
        onPrimary = primary.readableText(), onPrimaryContainer = primaryContainer.readableText(),
        onSecondary = secondary.readableText(), onSecondaryContainer = secondaryContainer.readableText(),
        onTertiary = tertiary.readableText(), onTertiaryContainer = tertiaryContainer.readableText(),
        onBackground = ink, onSurface = ink, onSurfaceVariant = ink, outline = ink,
        outlineVariant = if (dark) Color(0xFFBBBBBB) else Color(0xFF444444),
        onError = error.readableText(), onErrorContainer = errorContainer.readableText(),
        inverseOnSurface = inverseSurface.readableText(),
    )
}
