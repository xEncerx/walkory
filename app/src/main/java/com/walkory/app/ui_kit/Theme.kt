package com.walkory.app.ui_kit

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
private val LightColors = lightColorScheme(
    primary = Color(0xFF00696E), onPrimary = Color.White,
    primaryContainer = Color(0xFF9CF1F6), onPrimaryContainer = Color(0xFF002022),
    secondary = Color(0xFF4D6263), onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8E9), onSecondaryContainer = Color(0xFF051F20),
    tertiary = Color(0xFF4A607D), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD2E4FF), onTertiaryContainer = Color(0xFF001C3B),
    background = Color(0xFFF4FBFB), onBackground = Color(0xFF161D1D),
    surface = Color(0xFFF4FBFB), onSurface = Color(0xFF161D1D),
    surfaceVariant = Color(0xFFDDE4E4), onSurfaceVariant = Color(0xFF3F4948),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFEEF5F5),
    surfaceContainer = Color(0xFFE8EFEF), surfaceContainerHigh = Color(0xFFE2EAEA),
    surfaceContainerHighest = Color(0xFFDDE4E4),
    outline = Color(0xFF6F7979), outlineVariant = Color(0xFFBEC8C8),
    inverseSurface = Color(0xFF2B3232), inverseOnSurface = Color(0xFFECF2F2),
    inversePrimary = Color(0xFF80D5DA),
    error = Color(0xFFB3261E), onError = Color.White,
    errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF83D4D8), onPrimary = Color(0xFF063639),
    primaryContainer = Color(0xFF0B4F52), onPrimaryContainer = Color(0xFF9FF0F5),
    secondary = Color(0xFFB3CBCC), onSecondary = Color(0xFF1D3334),
    secondaryContainer = Color(0xFF354A4C), onSecondaryContainer = Color(0xFFCFE7E8),
    tertiary = Color(0xFFB2C8E5), onTertiary = Color(0xFF203249),
    tertiaryContainer = Color(0xFF38485A), onTertiaryContainer = Color(0xFFD3E4FA),
    background = Color(0xFF0D1515), onBackground = Color(0xFFDCE4E4),
    surface = Color(0xFF0D1515), onSurface = Color(0xFFDCE4E4),
    surfaceVariant = Color(0xFF394A4B), onSurfaceVariant = Color(0xFFB7CACB),
    surfaceContainerLowest = Color(0xFF080F0F), surfaceContainerLow = Color(0xFF161D1D),
    surfaceContainer = Color(0xFF1A2121), surfaceContainerHigh = Color(0xFF252B2B),
    surfaceContainerHighest = Color(0xFF2F3636),
    outline = Color(0xFF819495), outlineVariant = Color(0xFF394A4B),
    inverseSurface = Color(0xFFDCE4E4), inverseOnSurface = Color(0xFF2B3232),
    inversePrimary = Color(0xFF00696E),
    error = Color(0xFFF2B8B5), onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18), onErrorContainer = Color(0xFFF9DEDC),
)

private val WalkoryTypography = Typography()
val LocalReducedMotion = staticCompositionLocalOf { false }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WalkoryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    textScale: Float = 1f,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * textScale),
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colors,
            typography = WalkoryTypography,
            shapes = WalkoryShapes,
            motionScheme = if (reducedMotion) MotionScheme.standard() else MotionScheme.expressive(),
            content = content,
        )
    }
}