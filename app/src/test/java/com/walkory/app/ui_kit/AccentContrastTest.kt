package com.walkory.app.ui_kit

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.walkory.app.domain.Accent
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentContrastTest {
    @Test fun everyPresetKeepsNormalTextLegibleInBothModesAndHigherContrast() {
        for (dark in listOf(false, true)) for (accent in Accent.entries) for (higher in listOf(false, true)) {
            val preset = (if (dark) darkColorScheme() else lightColorScheme()).withAccent(accent, dark)
            val colors = if (higher) preset.withHigherContrast(dark) else preset
            listOf(
                colors.primary to colors.onPrimary, colors.primaryContainer to colors.onPrimaryContainer,
                colors.secondary to colors.onSecondary, colors.secondaryContainer to colors.onSecondaryContainer,
                colors.tertiary to colors.onTertiary, colors.tertiaryContainer to colors.onTertiaryContainer,
                colors.surface to colors.onSurface, colors.surface to colors.onSurfaceVariant,
                colors.error to colors.onError, colors.errorContainer to colors.onErrorContainer,
            ).forEach { (background, foreground) ->
                val ratio = contrast(background, foreground)
                assertTrue("$accent dark=$dark higher=$higher contrast=$ratio", ratio >= 4.5f)
            }
        }
    }
    private fun contrast(a: Color, b: Color): Float {
        val x = a.luminance(); val y = b.luminance()
        return (maxOf(x, y) + 0.05f) / (minOf(x, y) + 0.05f)
    }
}
