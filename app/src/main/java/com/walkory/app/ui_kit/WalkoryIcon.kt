package com.walkory.app.ui_kit

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun WalkoryIcon(imageVector: ImageVector, modifier: Modifier = Modifier, description: String? = null) {
    Icon(
        imageVector = imageVector,
        contentDescription = description,
        modifier = modifier.size(WalkoryLayout.iconSize),
    )
}
