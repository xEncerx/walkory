package com.walkory.app.features.inspections

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.walkory.app.R
import com.walkory.app.domain.*
import com.walkory.app.ui_kit.*

@Composable
internal fun ResultTextDialog(title: Int, initial: String, initialGuidance: String?, required: Boolean,
    onSave: (String, String) -> Boolean, onDismiss: () -> Unit, issue: Boolean = false) {
    var value by rememberSaveable { mutableStateOf(initial) }
    var guidance by rememberSaveable { mutableStateOf(initialGuidance.orEmpty()) }
    var validation by rememberSaveable { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(title)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(WalkorySpacing.medium)) {
            OutlinedTextField(value, { value = it }, label = { Text(stringResource(when {
                issue -> R.string.inspections_issue_description
                title == R.string.inspections_rename_room -> R.string.inspections_room_name
                else -> R.string.templates_item_title
            })) },
                isError = validation && required && value.isBlank(), modifier = Modifier.fillMaxWidth().testTag("result_edit_text"))
            if (validation && required && value.isBlank()) Text(stringResource(if (issue) R.string.inspections_empty_issue else R.string.common_required_field), color = MaterialTheme.colorScheme.error)
            if (initialGuidance != null) OutlinedTextField(guidance, { guidance = it }, label = { Text(stringResource(R.string.templates_guidance)) },
                modifier = Modifier.fillMaxWidth().testTag("result_edit_guidance"))
        }
    }, confirmButton = { TextButton({ validation = true; if ((!required || value.isNotBlank()) && onSave(value, guidance)) onDismiss() }, Modifier.testTag("result_edit_save")) {
        Text(stringResource(R.string.inspections_save))
    } }, dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.common_cancel)) } })
}

@Composable
internal fun ResultTemplateDialog(record: InspectionResultState, room: InspectionRoom, model: InspectionWorkflowViewModel, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(room.name) }
    var validation by rememberSaveable { mutableStateOf(false) }
    val saving = record.templateStatus == SaveStatus.SAVING
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text(stringResource(R.string.inspections_save_template)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(WalkorySpacing.medium)) {
            Text(stringResource(R.string.inspections_template_content_only))
            OutlinedTextField(name, { name = it }, enabled = !saving && record.templateStatus != SaveStatus.SAVED,
                label = { Text(stringResource(R.string.templates_name)) }, isError = validation && name.isBlank(), modifier = Modifier.fillMaxWidth().testTag("result_template_name"))
            if (validation && name.isBlank()) Text(stringResource(R.string.common_required_field), color = MaterialTheme.colorScheme.error)
            WalkorySaveStatus(record.templateStatus, { model.saveTemplate(record.inspection.id, room.id, name) })
        }
    }, confirmButton = {
        if (record.templateStatus == SaveStatus.SAVED) TextButton(onDismiss) { Text(stringResource(R.string.inspections_room_done)) }
        else TextButton({ validation = true; if (name.isNotBlank()) model.saveTemplate(record.inspection.id, room.id, name) }, enabled = !saving, modifier = Modifier.testTag("result_template_save")) { Text(stringResource(R.string.templates_save)) }
    }, dismissButton = { if (!saving) TextButton(onDismiss) { Text(stringResource(R.string.common_cancel)) } })
}

@Composable
internal fun AddResultRoomDialog(inspection: Inspection, templates: InspectionCreationState, onRetry: () -> Unit,
    onAdd: (RoomType, String, ChecklistTemplate?) -> Unit, onDismiss: () -> Unit) {
    var type by rememberSaveable { mutableStateOf(RoomType.KITCHEN) }
    var name by rememberSaveable { mutableStateOf<String?>(null) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var typeMenu by remember { mutableStateOf(false) }
    var templateMenu by remember { mutableStateOf(false) }
    var validation by rememberSaveable { mutableStateOf(false) }
    val actualName = name ?: stringResource(type.roomLabel())
    val available = templates.templates.filter { (it.origin as? TemplateOrigin.BuiltIn)?.role?.let { role -> role == inspection.details.role } != false }
        .sortedBy { (it.origin as? TemplateOrigin.BuiltIn)?.roomType != type }
    val template = available.find { it.id.value == selected }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.inspections_add_room)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(WalkorySpacing.medium)) {
            Box {
                OutlinedButton({ typeMenu = true }, Modifier.testTag("result_room_type")) { Text(stringResource(type.roomLabel())) }
                DropdownMenu(typeMenu, { typeMenu = false }) { RoomType.entries.forEach { option ->
                    DropdownMenuItem({ Text(stringResource(option.roomLabel())) }, { type = option; typeMenu = false })
                } }
            }
            WalkoryRequiredField(actualName, { name = it }, stringResource(R.string.inspections_room_name), validation, Modifier.testTag("result_room_name"))
            Text(stringResource(R.string.inspections_macro_body))
            Box {
                OutlinedButton({ templateMenu = true }, Modifier.testTag("result_room_template")) { Text(template?.name ?: stringResource(R.string.inspections_empty_macro)) }
                DropdownMenu(templateMenu, { templateMenu = false }) {
                    DropdownMenuItem({ Text(stringResource(R.string.inspections_empty_macro)) }, { selected = null; templateMenu = false })
                    available.forEach { item -> DropdownMenuItem({ Text(item.name) }, { selected = item.id.value; templateMenu = false }) }
                }
            }
            if (templates.loadingTemplates) WalkoryLoading()
            if (templates.templateError) WalkoryError(onRetry)
        }
    }, confirmButton = { TextButton({ validation = true; if (actualName.isNotBlank() && (selected == null || template != null)) onAdd(type, actualName, template) },
        enabled = selected == null || template != null, modifier = Modifier.testTag("result_room_add")) { Text(stringResource(R.string.inspections_add_room)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.common_cancel)) } })
}
