package com.walkory.app.ui_kit

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun WalkoryRecordRow(title: String, supporting: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        selected = selected,
        onClick = onClick,
        modifier = modifier.heightIn(min = WalkoryLayout.minimumTouchTarget),
        supportingContent = { Text(supporting) },
    ) { Text(title) }
}

@Composable
fun WalkoryActionRow(title: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(onClick = onClick, modifier = modifier.heightIn(min = WalkoryLayout.minimumTouchTarget)) { Text(title) }
}
