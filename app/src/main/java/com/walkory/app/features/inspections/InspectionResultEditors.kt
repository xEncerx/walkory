package com.walkory.app.features.inspections

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.walkory.app.R
import com.walkory.app.domain.*
import com.walkory.app.ui_kit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun ResultRoomScreen(record: InspectionResultState, room: InspectionRoom, editing: Boolean, model: InspectionWorkflowViewModel,
    onCheck: (CheckId) -> Unit, onEditList: () -> Unit, onBack: () -> Unit, onRemoved: () -> Unit) {
    val id = record.inspection.id
    var adding by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var removing by rememberSaveable { mutableStateOf(false) }
    var savingTemplate by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(WalkorySpacing.medium), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                TextButton({ adding = true }, Modifier.testTag("result_add_quick")) { Text(stringResource(R.string.templates_add_quick)) }
                if (editing) FloatingActionButton(onBack, Modifier.testTag("result_list_done")) {
                    WalkoryIcon(Icons.Rounded.Save, description = stringResource(R.string.inspections_room_done))
                }
            }
        }) { padding ->
    LazyColumn(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).testTag("result_room"), state = list, contentPadding = PaddingValues(WalkorySpacing.medium),
        verticalArrangement = Arrangement.spacedBy(WalkorySpacing.medium)) {
        item("actions") {
            if (!editing) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(room.name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    IconButton({ renaming = true }, Modifier.testTag("result_rename_room")) {
                        WalkoryIcon(Icons.Rounded.Edit, description = stringResource(R.string.inspections_rename_room))
                    }
                    Box {
                        IconButton({ menu = true }, Modifier.testTag("result_room_menu")) {
                            WalkoryIcon(Icons.Rounded.MoreVert, description = stringResource(R.string.inspections_room_actions))
                        }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text(stringResource(R.string.inspections_save_template)) },
                                { menu = false; model.clearTemplateStatus(id); savingTemplate = true }, enabled = room.checks.isNotEmpty(),
                                modifier = Modifier.testTag("result_save_template"))
                            DropdownMenuItem({ Text(stringResource(R.string.inspections_remove_room)) },
                                { menu = false; removing = true }, modifier = Modifier.testTag("result_remove_room"))
                        }
                    }
                }
                OutlinedButton(onEditList, Modifier.fillMaxWidth().testTag("result_edit_list")) { Text(stringResource(R.string.inspections_edit_list)) }
            }
            if (room.checks.isEmpty()) Text(stringResource(R.string.inspections_empty_checks))
        }
        itemsIndexed(room.checks, key = { _, check -> check.id.value }) { index, check ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(WalkorySpacing.small)) {
                    if (editing) Row(verticalAlignment = Alignment.CenterVertically) {
                        CheckReorderHandle(check.id.value, list, false, stringResource(R.string.templates_drag)) { from, to -> model.moveCheck(id, room.id, CheckId(from), CheckId(to)) }
                        IconButton({ model.moveCheck(id, room.id, check.id, room.checks[index - 1].id) }, enabled = index > 0, modifier = Modifier.testTag("result_up_$index")) {
                            WalkoryIcon(Icons.Rounded.ArrowUpward, description = stringResource(R.string.templates_up))
                        }
                        IconButton({ model.moveCheck(id, room.id, check.id, room.checks[index + 1].id) }, enabled = index < room.checks.lastIndex, modifier = Modifier.testTag("result_down_$index")) {
                            WalkoryIcon(Icons.Rounded.ArrowDownward, description = stringResource(R.string.templates_down))
                        }
                    }
                    ListItem(onClick = { onCheck(check.id) }, modifier = Modifier.testTag("result_check_${check.id.value}"),
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(WalkorySpacing.small)) {
                                Text(stringResource(check.state.resultLabel()))
                                Text(stringResource(R.string.inspections_issue_counts, check.issues.size, if (check.state == CheckState.HAS_ISSUE) 1 else 0))
                            }
                        }) { Text(check.title) }
                }
            }
        }
        item("add") { OutlinedButton({ adding = true }, Modifier.testTag("result_add_check")) { Text(stringResource(R.string.templates_add_item)) } }
    }
    }
    if (adding) ResultTextDialog(R.string.inspections_new_check, "", "", true,
        { title, guidance -> model.addCheck(id, room.id, title, guidance) }, { adding = false })
    if (renaming) ResultTextDialog(R.string.inspections_rename_room, room.name, null, true,
        { name, _ -> model.renameRoom(id, room.id, name) }, { renaming = false })
    if (removing) WalkoryConfirmation(stringResource(R.string.inspections_remove_room_title, room.name), stringResource(R.string.inspections_delete_room_evidence),
        stringResource(R.string.inspections_remove_room), { if (model.removeRoom(id, room.id)) { removing = false; onRemoved() } }, { removing = false })
    if (savingTemplate) ResultTemplateDialog(record, room, model) { savingTemplate = false }
}

@Composable
internal fun ResultCheckScreen(id: InspectionId, room: InspectionRoom, check: InspectionCheck, initialIssue: String?, model: InspectionWorkflowViewModel,
    photographs: com.walkory.app.features.media.PhotographViewModel) {
    val workflow by model.state.collectAsStateWithLifecycle()
    val saveStatus = workflow.records[id]?.saveStatus ?: SaveStatus.IDLE
    var editing by rememberSaveable { mutableStateOf(false) }
    var addingIssue by rememberSaveable { mutableStateOf(false) }
    var editingIssue by rememberSaveable { mutableStateOf(initialIssue) }
    var removingIssue by rememberSaveable { mutableStateOf<String?>(null) }
    var choosingState by rememberSaveable { mutableStateOf(false) }
    val chooseState: (CheckState) -> Unit = { model.setState(id, check.id, it); choosingState = false }
    LazyColumn(Modifier.fillMaxSize().testTag("result_check_detail"), contentPadding = PaddingValues(WalkorySpacing.medium),
        verticalArrangement = Arrangement.spacedBy(WalkorySpacing.medium)) {
        item {
            Text(room.name)
            check.guidance?.takeIf { it.isNotBlank() }?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(WalkorySpacing.medium)) { Text(stringResource(R.string.inspections_guidance), style = MaterialTheme.typography.titleMedium); Text(it) }
                }
            }
            ResultStateSelector(check.state) { model.setState(id, check.id, it) }
        }
        item {
            WalkoryTextField(check.comment.orEmpty(), { model.comment(id, check.id, it) }, label = { Text(stringResource(R.string.inspections_comment)) },
                modifier = Modifier.fillMaxWidth().testTag("result_comment"))
        }
        item("condition_photos") {
            com.walkory.app.features.media.PhotographCollection(id, PhotographOwner.Condition(check.id), check.conditionPhotos,
                photographs, { owner, photo, caption -> model.caption(id, owner, photo, caption) },
                { owner, photo -> model.removePhoto(id, owner, photo) }, saveStatus, { model.retry(id) })
        }
        itemsIndexed(check.issues, key = { _, issue -> issue.id.value }) { index, issue ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(WalkorySpacing.medium)) {
                    Text(stringResource(R.string.inspections_issue_number, index + 1), style = MaterialTheme.typography.titleMedium)
                    Text(issue.description?.takeIf { it.isNotBlank() } ?: stringResource(R.string.inspections_issue_without_text))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(WalkorySpacing.small)) {
                        TextButton({ editingIssue = issue.id.value }, Modifier.testTag("result_issue_edit_${issue.id.value}")) { Text(stringResource(R.string.inspections_edit_issue)) }
                        TextButton({ removingIssue = issue.id.value }, Modifier.testTag("result_issue_remove_${issue.id.value}")) { Text(stringResource(R.string.inspections_delete_issue)) }
                    }
                    com.walkory.app.features.media.PhotographCollection(id, PhotographOwner.IssueEvidence(check.id, issue.id), issue.photos,
                        photographs, { owner, photo, caption -> model.caption(id, owner, photo, caption) },
                        { owner, photo -> model.removePhoto(id, owner, photo) }, saveStatus, { model.retry(id) })
                }
            }
        }
        item {
            OutlinedButton({ addingIssue = true }, Modifier.testTag("result_add_issue")) { Text(stringResource(R.string.inspections_add_issue)) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(WalkorySpacing.small)) {
                TextButton({ editing = true }, Modifier.testTag("result_edit_check")) { Text(stringResource(R.string.inspections_edit_check)) }
            }
        }
    }
    if (editing) ResultTextDialog(R.string.inspections_edit_check, check.title, check.guidance.orEmpty(), true,
        { title, guidance -> model.editCheck(id, check.id, title, guidance) }, { editing = false })
    if (addingIssue) NewPhotoIssueDialog(id, check, model, photographs) { addingIssue = false }
    check.issues.find { it.id.value == editingIssue }?.let { issue ->
        ResultTextDialog(R.string.inspections_edit_issue, issue.description.orEmpty(), null, issue.photos.isEmpty(),
            { description, _ -> model.editIssue(id, check.id, issue.id, description) }, { editingIssue = null }, issue = true)
    }
    check.issues.find { it.id.value == removingIssue }?.let { issue ->
        WalkoryConfirmation(stringResource(R.string.inspections_delete_issue), stringResource(R.string.inspections_delete_issue_evidence),
            stringResource(R.string.inspections_delete_issue), {
                if (model.deleteIssue(id, check.id, issue.id)) { removingIssue = null; choosingState = check.issues.size == 1 }
            }, { removingIssue = null })
    }
    if (choosingState) AlertDialog(onDismissRequest = { choosingState = false }, title = { Text(stringResource(R.string.inspections_last_issue_title)) },
        text = { Column { Text(stringResource(R.string.inspections_last_issue_body)); CheckState.entries.forEach { state ->
            TextButton({ chooseState(state) }, Modifier.fillMaxWidth().testTag("last_issue_state_${state.name}")) { Text(stringResource(state.resultLabel())) }
        } } }, confirmButton = { TextButton({ choosingState = false }) { Text(stringResource(R.string.common_cancel)) } })
}

@Composable
internal fun ResultStateSelector(state: CheckState, onSelect: (CheckState) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ menu = true }, Modifier.testTag("result_state")) { Text(stringResource(R.string.inspections_state_value, stringResource(state.resultLabel()))) }
        DropdownMenu(menu, { menu = false }) {
            CheckState.entries.forEach { option -> DropdownMenuItem({ Text(stringResource(option.resultLabel())) }, { menu = false; onSelect(option) },
                modifier = Modifier.testTag("result_state_${option.name}")) }
        }
    }
}
