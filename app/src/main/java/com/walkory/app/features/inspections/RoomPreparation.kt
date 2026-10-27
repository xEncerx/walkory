package com.walkory.app.features.inspections

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.walkory.app.R
import com.walkory.app.domain.*
import com.walkory.app.ui_kit.*

@Composable
internal fun PreparedRoomForm(
    state: InspectionCreationState, id: String, onRename: (String, String) -> Unit, onMacro: () -> Unit,
    onAdd: (String) -> Unit, onEdit: (String, String, String, String) -> Unit, onRemove: (String, String) -> Unit,
    onMove: (String, String, String) -> Unit, onDone: () -> Unit,
) {
    val room = state.draft?.rooms?.find { it.id == id } ?: return
    var validation by rememberSaveable { mutableStateOf(false) }
    var removeId by rememberSaveable { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = WalkoryLayout.contentWidth).fillMaxSize().testTag("room_preparation")) {
            LazyColumn(Modifier.weight(1f).testTag("room_checks"), state = list, contentPadding = PaddingValues(WalkorySpacing.medium),
                verticalArrangement = Arrangement.spacedBy(WalkorySpacing.medium)) {
                item("name") {
                    WalkoryRequiredField(room.name, { onRename(id, it) }, stringResource(R.string.inspections_room_name), validation,
                        Modifier.testTag("prepared_room_name"))
                }
                item("macro") {
                    OutlinedButton(onMacro, enabled = room.name.isNotBlank(), modifier = Modifier.testTag("room_choose_macro")) { Text(stringResource(R.string.inspections_choose_macro)) }
                    Text(stringResource(R.string.inspections_preparation_body))
                }
                if (room.checks.isEmpty()) item("empty") { Text(stringResource(R.string.inspections_empty_checks)) }
                itemsIndexed(room.checks, key = { _, check -> check.id }) { index, check ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(WalkorySpacing.medium), verticalArrangement = Arrangement.spacedBy(WalkorySpacing.small)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CheckReorderHandle(check.id, list, false, stringResource(R.string.templates_drag)) { from, to -> onMove(id, from, to) }
                                Text(stringResource(R.string.templates_number, index + 1), Modifier.weight(1f))
                                IconButton({ onMove(id, check.id, room.checks[index - 1].id) }, enabled = index > 0, modifier = Modifier.testTag("room_check_up_$index")) {
                                    WalkoryIcon(Icons.Rounded.ArrowUpward, description = stringResource(R.string.templates_up))
                                }
                                IconButton({ onMove(id, check.id, room.checks[index + 1].id) }, enabled = index < room.checks.lastIndex, modifier = Modifier.testTag("room_check_down_$index")) {
                                    WalkoryIcon(Icons.Rounded.ArrowDownward, description = stringResource(R.string.templates_down))
                                }
                            }
                            WalkoryRequiredField(check.title, { onEdit(id, check.id, it, check.guidance) }, stringResource(R.string.templates_item_title), validation,
                                Modifier.testTag("room_check_title_$index"))
                            OutlinedTextField(check.guidance, { onEdit(id, check.id, check.title, it) }, label = { Text(stringResource(R.string.templates_guidance)) },
                                modifier = Modifier.fillMaxWidth().testTag("room_check_guidance_$index"))
                            TextButton({ removeId = check.id }, Modifier.testTag("room_check_remove_$index")) { Text(stringResource(R.string.templates_remove_item)) }
                        }
                    }
                }
            }
            FlowRow(Modifier.padding(horizontal = WalkorySpacing.medium), horizontalArrangement = Arrangement.spacedBy(WalkorySpacing.small)) {
                OutlinedButton({ onAdd(id) }, Modifier.testTag("room_add_check")) { Text(stringResource(R.string.templates_add_item)) }
                Button({ validation = true; if (room.name.isNotBlank() && room.checks.all { it.title.isNotBlank() }) onDone() }, Modifier.testTag("room_done")) {
                    Text(stringResource(R.string.inspections_room_done))
                }
            }
        }
    }
    if (removeId != null) WalkoryConfirmation(stringResource(R.string.templates_remove_title), stringResource(R.string.inspections_remove_check_body),
        stringResource(R.string.templates_remove_item), { removeId?.let { onRemove(id, it) }; removeId = null }, { removeId = null })
}

@Composable
internal fun RoomMacroForm(state: InspectionCreationState, roomId: String, onRetry: () -> Unit, onSelect: (ChecklistTemplate?) -> Unit) {
    val draft = state.draft ?: return
    val room = draft.rooms.find { it.id == roomId } ?: return
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var replacing by rememberSaveable { mutableStateOf(false) }
    val select: (ChecklistTemplate?) -> Unit = { template ->
        if (room.checks.isEmpty()) onSelect(template)
        else { selectedId = template?.id?.value; replacing = true }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("room_macros"), contentPadding = PaddingValues(WalkorySpacing.medium),
        verticalArrangement = Arrangement.spacedBy(WalkorySpacing.medium)) {
        item { Text(stringResource(R.string.inspections_macro_body)) }
        item { OutlinedButton({ select(null) }, Modifier.testTag("room_macro_empty")) { Text(stringResource(R.string.inspections_empty_macro)) } }
        when {
            state.loadingTemplates -> item { WalkoryLoading() }
            state.templateError -> item { WalkoryError(onRetry) }
            else -> {
                val available = state.templates.filter { template ->
                    val origin = template.origin as? TemplateOrigin.BuiltIn
                    origin == null || origin.role == draft.role
                }.sortedBy { template -> (template.origin as? TemplateOrigin.BuiltIn)?.roomType != room.type }
                items(available, key = { it.id.value }) { template ->
                    WalkoryRecordRow(template.name, pluralStringResource(R.plurals.inspections_checks, template.checks.size, template.checks.size),
                        false, { select(template) }, Modifier.testTag("room_macro_${template.id.value}"))
                }
            }
        }
    }
    if (replacing) WalkoryConfirmation(stringResource(R.string.inspections_replace_title), stringResource(R.string.inspections_replace_body),
        stringResource(R.string.inspections_replace), {
            val selected = state.templates.find { it.id.value == selectedId }
            // A removed catalog entry is not an instruction to erase the room.
            if (selectedId == null || selected != null) onSelect(selected)
            replacing = false
        }, { replacing = false })
}
