package com.walkory.app.features.inspections

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.walkory.app.R
import com.walkory.app.domain.*
import com.walkory.app.ui_kit.*
import java.time.LocalDate
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun InspectionForm(tag: String, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = WalkoryLayout.contentWidth).fillMaxSize().verticalScroll(rememberScrollState())
            .padding(WalkorySpacing.medium).testTag(tag), verticalArrangement = Arrangement.spacedBy(WalkorySpacing.medium), content = content)
    }
}

@Composable
internal fun InspectionRoleForm(state: InspectionCreationState, onRole: (InspectionRole) -> Unit, onNext: () -> Unit) {
    InspectionForm("inspection_role") {
        Text(stringResource(R.string.inspections_role_body))
        InspectionRole.entries.forEach { role ->
            FilterChip(state.draft?.role == role, { onRole(role) }, label = { Text(stringResource(role.label())) },
                modifier = Modifier.testTag("inspection_role_${role.name}"))
        }
        Button(onNext, enabled = state.draft?.role != null, modifier = Modifier.testTag("inspection_role_next")) {
            Text(stringResource(R.string.inspections_next_details))
        }
    }
}

internal fun InspectionRole.label() = if (this == InspectionRole.TENANT) R.string.inspections_tenant else R.string.inspections_landlord

@Composable
internal fun formattedDate(date: LocalDate): String = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(LocalConfiguration.current.locales[0]))

@Composable
internal fun InspectionDetailsForm(state: InspectionCreationState, onDetails: (String, String, String) -> Unit, onDate: (LocalDate) -> Unit,
    propertyName: String?, onProperty: () -> Unit, onNext: () -> Unit) {
    val draft = state.draft ?: return
    var showDate by rememberSaveable { mutableStateOf(false) }
    InspectionForm("inspection_details") {
        Text(stringResource(draft.role!!.label()))
        WalkoryRequiredField(draft.name, { onDetails(it, draft.address, draft.inspector) }, stringResource(R.string.inspections_name), state.validation,
            Modifier.testTag("inspection_name"))
        WalkoryRequiredField(draft.address, { onDetails(draft.name, it, draft.inspector) }, stringResource(R.string.inspections_address), state.validation,
            Modifier.testTag("inspection_address"))
        OutlinedButton({ showDate = true }, Modifier.fillMaxWidth().testTag("inspection_date")) {
            Text(stringResource(R.string.inspections_date_value, formattedDate(LocalDate.parse(draft.date))))
        }
        WalkoryTextField(draft.inspector, { onDetails(draft.name, draft.address, it) }, label = { Text(stringResource(R.string.inspections_inspector)) },
            modifier = Modifier.fillMaxWidth().testTag("inspection_inspector"))
        OutlinedButton(onProperty, Modifier.fillMaxWidth().testTag("inspection_property")) {
            Text(propertyName ?: stringResource(R.string.objects_link))
        }
        Button(onNext, Modifier.testTag("inspection_details_next")) { Text(stringResource(R.string.inspections_next_rooms)) }
    }
    if (showDate) InspectionDateDialog(LocalDate.parse(draft.date), { onDate(it); showDate = false }, { showDate = false })
}

@Composable
internal fun InspectionRoomsForm(
    state: InspectionCreationState, onAdd: (RoomType, String) -> String?, onEdit: (String) -> Unit,
    onRemove: (String) -> Unit, onNext: () -> Unit,
) {
    val draft = state.draft ?: return
    var adding by rememberSaveable { mutableStateOf(false) }
    var removeId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val roomRequired = stringResource(R.string.inspections_room_required)
    Box(Modifier.fillMaxSize()) {
    InspectionForm("inspection_rooms") {
        Text(stringResource(R.string.inspections_rooms_body))
        draft.rooms.forEach { room ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(WalkorySpacing.medium)) {
                    WalkoryRecordRow(room.name, pluralStringResource(R.plurals.inspections_checks, room.checks.size, room.checks.size), false,
                        { onEdit(room.id) }, Modifier.testTag("prepared_room_${room.id}"))
                    if (state.validation && (room.name.isBlank() || room.checks.any { it.title.isBlank() })) Text(stringResource(R.string.inspections_room_invalid), color = MaterialTheme.colorScheme.error)
                    TextButton({ removeId = room.id }) { Text(stringResource(R.string.inspections_remove_room)) }
                }
            }
        }
        if (state.validation && draft.rooms.isEmpty()) Text(stringResource(R.string.inspections_room_required), color = MaterialTheme.colorScheme.error)
        OutlinedButton({ adding = true }, Modifier.testTag("inspection_add_room")) { Text(stringResource(R.string.inspections_add_room)) }
        Button({
            if (draft.rooms.isEmpty()) scope.launch { snackbar.showSnackbar(roomRequired) }
            onNext()
        }, Modifier.testTag("inspection_rooms_next")) { Text(stringResource(R.string.inspections_next_ready)) }
    }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).imePadding())
    }
    if (adding) AddPreparedRoomDialog({ type, name -> onAdd(type, name)?.let { adding = false; onEdit(it) } }, { adding = false })
    val room = draft.rooms.find { it.id == removeId }
    if (room != null) WalkoryConfirmation(stringResource(R.string.inspections_remove_room_title, room.name), stringResource(R.string.inspections_remove_room_body),
        stringResource(R.string.inspections_remove_room), { onRemove(room.id); removeId = null }, { removeId = null })
}

@Composable
private fun AddPreparedRoomDialog(onAdd: (RoomType, String) -> Unit, onDismiss: () -> Unit) {
    var type by rememberSaveable { mutableStateOf(RoomType.CUSTOM) }
    var name by rememberSaveable { mutableStateOf("") }
    var validation by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    AlertDialog(modifier = Modifier.dismissKeyboardOnOutsideTap(), onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.inspections_add_room)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(WalkorySpacing.medium)) {
                OutlinedButton({ menu = true }, Modifier.testTag("room_type")) {
                    Text(stringResource(R.string.inspections_ready_rooms))
                }
                WalkoryRequiredField(name, { name = it }, stringResource(R.string.inspections_room_name), validation, Modifier.testTag("new_room_name"))
            }
        },
        confirmButton = { TextButton({ validation = true; if (name.isNotBlank()) onAdd(type, name) }, Modifier.testTag("room_add_confirm")) { Text(stringResource(R.string.inspections_add_room)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.common_cancel)) } },)
    if (menu) RoomTypePicker({ selected, selectedName ->
        type = selected
        name = selectedName
        menu = false
    }, { menu = false })
}

@Composable
internal fun InspectionReadyForm(state: InspectionCreationState, onEdit: (String) -> Unit, onCreate: () -> Unit) {
    val draft = state.draft ?: return
    val saving = state.saveStatus == SaveStatus.SAVING
    InspectionForm("inspection_ready") {
        Text(draft.name, style = MaterialTheme.typography.headlineSmall)
        Text(draft.address)
        Text(stringResource(draft.role!!.label()))
        Text(formattedDate(LocalDate.parse(draft.date)))
        if (draft.inspector.isNotEmpty()) Text(draft.inspector)
        Text(stringResource(R.string.inspections_ready_body))
        draft.rooms.forEach { room ->
            WalkoryRecordRow(room.name, pluralStringResource(R.plurals.inspections_checks, room.checks.size, room.checks.size), false,
                { if (!saving) onEdit(room.id) })
        }
        WalkorySaveStatus(state.saveStatus, onCreate)
        Button(onCreate, enabled = !saving, modifier = Modifier.testTag("inspection_create")) { Text(stringResource(R.string.inspections_create)) }
    }
}

internal fun RoomType.roomLabel(): Int = when (this) {
    RoomType.KITCHEN -> R.string.inspections_room_kitchen
    RoomType.BEDROOM -> R.string.inspections_room_bedroom
    RoomType.LIVING_ROOM -> R.string.inspections_room_living
    RoomType.COMBINED_BATHROOM -> R.string.inspections_room_combined
    RoomType.BATHROOM -> R.string.inspections_room_bathroom
    RoomType.TOILET -> R.string.inspections_room_toilet
    RoomType.ENTRANCE_HALL -> R.string.inspections_room_entrance
    RoomType.BALCONY -> R.string.inspections_room_balcony
    RoomType.WALK_IN_WARDROBE -> R.string.inspections_room_wardrobe
    RoomType.STORAGE_ROOM -> R.string.inspections_room_storage
    RoomType.STUDY -> R.string.inspections_room_study
    RoomType.LAUNDRY_UTILITY_ROOM -> R.string.inspections_room_laundry
    RoomType.DINING_ROOM -> R.string.inspections_room_dining
    RoomType.CUSTOM -> R.string.inspections_room_custom
}
