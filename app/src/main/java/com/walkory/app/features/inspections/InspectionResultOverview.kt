package com.walkory.app.features.inspections

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.walkory.app.R
import com.walkory.app.domain.*
import com.walkory.app.ui_kit.*

@Composable
internal fun ResultOverviewScreen(record: InspectionResultState, model: InspectionWorkflowViewModel, onRoom: (RoomId) -> Unit,
    onSummary: (Boolean) -> Unit, templates: InspectionCreationState, onRetryTemplates: () -> Unit,
    onReport: () -> Unit, actions: @Composable () -> Unit) {
    val inspection = record.inspection
    val progress = inspection.progress()
    var completing by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().testTag("inspection_overview"), contentPadding = PaddingValues(WalkorySpacing.medium),
        verticalArrangement = Arrangement.spacedBy(WalkorySpacing.medium)) {
        item("details") {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(WalkorySpacing.large), verticalArrangement = Arrangement.spacedBy(WalkorySpacing.small)) {
                    Text(inspection.details.property.address, style = MaterialTheme.typography.titleLarge)
                    Text(formattedDate(inspection.details.date), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(inspection.details.role.label()), style = MaterialTheme.typography.labelLarge)
                    inspection.details.inspector?.takeIf { it.isNotEmpty() }?.let { Text(stringResource(R.string.inspections_inspector_value, it)) }
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(stringResource(if (inspection.state == InspectionState.COMPLETED) R.string.inspections_completed else R.string.inspections_in_progress),
                            Modifier.padding(horizontal = WalkorySpacing.medium, vertical = WalkorySpacing.small),
                            style = MaterialTheme.typography.labelLarge)
                    }
                    actions()
                }
            }
        }
        item("progress") {
            ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(WalkorySpacing.large), verticalArrangement = Arrangement.spacedBy(WalkorySpacing.medium)) {
                    Text(stringResource(R.string.inspections_progress_heading), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.inspections_progress, progress.checkedChecks, progress.totalChecks),
                        style = MaterialTheme.typography.headlineSmall)
                    LinearProgressIndicator(progress = { progress.fraction.toFloat() }, modifier = Modifier.fillMaxWidth())
                }
                ListItem(onClick = { onSummary(false) }, modifier = Modifier.testTag("result_unchecked"),
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)) {
                    Text(pluralStringResource(R.plurals.inspections_unchecked, progress.uncheckedChecks, progress.uncheckedChecks))
                }
                ListItem(onClick = { onSummary(true) }, modifier = Modifier.testTag("result_issues"),
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    supportingContent = { Text(stringResource(R.string.inspections_issue_counts, progress.issueCount, progress.problematicChecks)) }) {
                    Text(stringResource(R.string.inspections_issues))
                }
            }
        }
        item("rooms_heading") { Text(stringResource(R.string.inspections_rooms_title), style = MaterialTheme.typography.titleLarge) }
        items(inspection.rooms, key = { it.id.value }) { room ->
            val roomProgress = inspection.copy(rooms = listOf(room)).progress()
            WalkoryRecordRow(room.name, stringResource(R.string.inspections_room_progress, roomProgress.checkedChecks, roomProgress.totalChecks, roomProgress.issueCount), false,
                { onRoom(room.id) }, Modifier.testTag("overview_room_${room.id.value}"))
        }
        item("add_room") {
            OutlinedButton({ adding = true }, Modifier.fillMaxWidth().testTag("result_add_room")) { Text(stringResource(R.string.inspections_add_room)) }
        }
        item("finish") {
            HorizontalDivider(Modifier.padding(vertical = WalkorySpacing.medium))
            Column(verticalArrangement = Arrangement.spacedBy(WalkorySpacing.small)) {
                Button(onReport, Modifier.fillMaxWidth().testTag("result_report")) { Text(stringResource(R.string.reports_title)) }
                if (inspection.state == InspectionState.IN_PROGRESS) OutlinedButton({ completing = true },
                    Modifier.fillMaxWidth().testTag("result_complete")) { Text(stringResource(R.string.inspections_complete)) }
                else Text(stringResource(R.string.inspections_completed_editable), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    if (completing) WalkoryConfirmation(stringResource(R.string.inspections_complete_title),
        if (progress.uncheckedChecks > 0) pluralStringResource(R.plurals.inspections_complete_unchecked, progress.uncheckedChecks, progress.uncheckedChecks)
        else stringResource(R.string.inspections_complete_body), stringResource(R.string.inspections_complete),
        { model.complete(inspection.id, true); completing = false }, { completing = false })
    if (adding) AddResultRoomDialog(inspection, templates, onRetryTemplates,
        { type, name, template -> if (model.addRoom(inspection.id, type, name, template)) adding = false }, { adding = false })
}

@Composable
internal fun ResultSummaryScreen(inspection: Inspection, issues: Boolean, onCheck: (CheckId, IssueId?) -> Unit) {
    var selectedRoom by rememberSaveable { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    val rooms = inspection.rooms.filter { selectedRoom == null || it.id.value == selectedRoom }
    val filtered = inspection.copy(rooms = rooms).progress()
    LazyColumn(Modifier.fillMaxSize().testTag("result_summary"), contentPadding = PaddingValues(WalkorySpacing.medium),
        verticalArrangement = Arrangement.spacedBy(WalkorySpacing.medium)) {
        item {
            Box {
                OutlinedButton({ menu = true }, Modifier.testTag("summary_room_filter")) {
                    Text(inspection.rooms.find { it.id.value == selectedRoom }?.name ?: stringResource(R.string.inspections_all_rooms))
                }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text(stringResource(R.string.inspections_all_rooms)) }, { selectedRoom = null; menu = false })
                    inspection.rooms.forEach { room -> DropdownMenuItem({ Text(room.name) }, { selectedRoom = room.id.value; menu = false }, modifier = Modifier.testTag("summary_filter_${room.id.value}")) }
                }
            }
            if (issues) Text(stringResource(R.string.inspections_issue_counts, filtered.issueCount, filtered.problematicChecks))
            else Text(pluralStringResource(R.plurals.inspections_unchecked, filtered.uncheckedChecks, filtered.uncheckedChecks))
            if (if (issues) filtered.problematicChecks == 0 else filtered.uncheckedChecks == 0) Text(stringResource(R.string.inspections_summary_empty))
        }
        rooms.forEach { room ->
            val checks = room.checks.filter { if (issues) it.state == CheckState.HAS_ISSUE else it.state == CheckState.UNCHECKED }
            if (checks.isNotEmpty()) item(key = "room-${room.id.value}") { Text(room.name, style = MaterialTheme.typography.titleLarge) }
            checks.forEach { check ->
                item(key = check.id.value) {
                    WalkoryRecordRow(check.title, stringResource(check.state.resultLabel()), false, { onCheck(check.id, null) }, Modifier.testTag("summary_check_${check.id.value}"))
                    if (issues && check.issues.isEmpty()) Text(stringResource(R.string.inspections_no_issue_records))
                }
                if (issues) items(check.issues, key = { it.id.value }) { issue ->
                    WalkoryRecordRow(issue.description ?: stringResource(R.string.inspections_issue_without_text), check.title, false,
                        { onCheck(check.id, issue.id) }, Modifier.testTag("summary_issue_${issue.id.value}"))
                }
            }
        }
    }
}
