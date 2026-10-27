package com.walkory.app.ui_kit

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object WalkorySpacing {
    val small = 8.dp
    val medium = 16.dp
    val large = 24.dp
    val extraLarge = 32.dp
}

object WalkoryLayout {
    val contentWidth = 720.dp
    val listPaneWidth = 360.dp
    val minimumTouchTarget = 48.dp
    val iconSize = 24.dp
    val floatingActionClearance = 88.dp
}

internal val WalkoryShapes = Shapes(
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
