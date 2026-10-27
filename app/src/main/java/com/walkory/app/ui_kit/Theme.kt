package com.walkory.app.ui_kit

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF006A62),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9EF2E6),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF4B635F),
    secondaryContainer = Color(0xFFCEE8E2),
    tertiary = Color(0xFF456179),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF82D5C9),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005048),
    onPrimaryContainer = Color(0xFF9EF2E6),
    secondary = Color(0xFFB2CCC6),
    secondaryContainer = Color(0xFF334B46),
    tertiary = Color(0xFFADCAE6),
)

private val WalkoryTypography = Typography()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WalkoryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialExpressiveTheme(
        colorScheme = colors,
        typography = WalkoryTypography,
        content = content,
    )
}