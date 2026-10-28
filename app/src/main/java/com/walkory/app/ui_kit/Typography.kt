package com.walkory.app.ui_kit

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle

// Typography crosses dialog/popup composition roots; a density override does not.
internal fun Typography.scaled(scale: Float): Typography = if (scale == 1f) this else copy(
    displayLarge = displayLarge.scaled(scale), displayMedium = displayMedium.scaled(scale), displaySmall = displaySmall.scaled(scale),
    headlineLarge = headlineLarge.scaled(scale), headlineMedium = headlineMedium.scaled(scale), headlineSmall = headlineSmall.scaled(scale),
    titleLarge = titleLarge.scaled(scale), titleMedium = titleMedium.scaled(scale), titleSmall = titleSmall.scaled(scale),
    bodyLarge = bodyLarge.scaled(scale), bodyMedium = bodyMedium.scaled(scale), bodySmall = bodySmall.scaled(scale),
    labelLarge = labelLarge.scaled(scale), labelMedium = labelMedium.scaled(scale), labelSmall = labelSmall.scaled(scale),
    displayLargeEmphasized = displayLargeEmphasized.scaled(scale), displayMediumEmphasized = displayMediumEmphasized.scaled(scale), displaySmallEmphasized = displaySmallEmphasized.scaled(scale),
    headlineLargeEmphasized = headlineLargeEmphasized.scaled(scale), headlineMediumEmphasized = headlineMediumEmphasized.scaled(scale), headlineSmallEmphasized = headlineSmallEmphasized.scaled(scale),
    titleLargeEmphasized = titleLargeEmphasized.scaled(scale), titleMediumEmphasized = titleMediumEmphasized.scaled(scale), titleSmallEmphasized = titleSmallEmphasized.scaled(scale),
    bodyLargeEmphasized = bodyLargeEmphasized.scaled(scale), bodyMediumEmphasized = bodyMediumEmphasized.scaled(scale), bodySmallEmphasized = bodySmallEmphasized.scaled(scale),
    labelLargeEmphasized = labelLargeEmphasized.scaled(scale), labelMediumEmphasized = labelMediumEmphasized.scaled(scale), labelSmallEmphasized = labelSmallEmphasized.scaled(scale),
)

private fun TextStyle.scaled(scale: Float): TextStyle = copy(
    fontSize = fontSize * scale,
    lineHeight = lineHeight * scale,
    letterSpacing = if (letterSpacing.isSp) letterSpacing * scale else letterSpacing,
)
