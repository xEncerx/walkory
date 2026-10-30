package com.walkory.app.features.inspections

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.walkory.app.R
import com.walkory.app.domain.RoomType
import com.walkory.app.ui_kit.*

@Composable
internal fun RoomTypePicker(onSelect: (RoomType, String) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val names = RoomType.entries.filter { it != RoomType.CUSTOM }.map { it to stringResource(it.roomLabel()) }
    AlertDialog(modifier = Modifier.dismissKeyboardOnOutsideTap(), onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.inspections_ready_rooms)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(WalkorySpacing.small)) {
            WalkoryTextField(query, { query = it }, Modifier.fillMaxWidth().testTag("room_type_search"), singleLine = true,
                label = { Text(stringResource(R.string.templates_search)) }, leadingIcon = { WalkoryIcon(Icons.Rounded.Search) })
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                val visible = names.filter { it.second.contains(query.trim(), ignoreCase = true) }
                if (visible.isEmpty()) item { Text(stringResource(R.string.inspections_summary_empty)) }
                items(visible, key = { it.first.name }) { (type, name) ->
                    ListItem(onClick = { onSelect(type, name) }, modifier = Modifier.fillMaxWidth().testTag("room_type_${type.name}"),
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)) { Text(name) }
                }
            }
        } }, confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
