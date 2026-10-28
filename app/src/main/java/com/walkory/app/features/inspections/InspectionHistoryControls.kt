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
internal fun InspectionHistoryControls(discovery: InspectionDiscovery, onChange: (InspectionDiscovery) -> Unit) {
    var filtering by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(discovery.query, { onChange(discovery.copy(query = it)) },
        label = { Text(stringResource(R.string.inspections_search)) }, modifier = Modifier.fillMaxWidth().testTag("history_search"))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(WalkorySpacing.small)) {
        OutlinedButton({ filtering = true }, Modifier.testTag("history_filters")) { Text(stringResource(R.string.inspections_filters)) }
        if (discovery != InspectionDiscovery()) TextButton({ onChange(InspectionDiscovery()) }, Modifier.testTag("history_reset")) {
            Text(stringResource(R.string.inspections_reset))
        }
    }
    if (filtering) HistoryFiltersDialog(discovery, { onChange(it); filtering = false }, { filtering = false })
}

@Composable
private fun HistoryFiltersDialog(current: InspectionDiscovery, onApply: (InspectionDiscovery) -> Unit, onDismiss: () -> Unit) {
    var role by rememberSaveable { mutableStateOf(current.role) }
    var status by rememberSaveable { mutableStateOf(current.status) }
    var sort by rememberSaveable { mutableStateOf(current.sort) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.inspections_filters)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(WalkorySpacing.medium)) {
                HistoryChoice(stringResource(R.string.inspections_filter_role), role?.let { stringResource(it.label()) } ?: stringResource(R.string.inspections_any),
                    "history_role", listOf(null) + InspectionRole.entries, role,
                    { it?.let { value -> stringResource(value.label()) } ?: stringResource(R.string.inspections_any) }, { role = it })
                HistoryChoice(stringResource(R.string.inspections_filter_status), status?.let { stringResource(it.historyLabel()) } ?: stringResource(R.string.inspections_any),
                    "history_status", listOf(null) + InspectionState.entries, status,
                    { it?.let { value -> stringResource(value.historyLabel()) } ?: stringResource(R.string.inspections_any) }, { status = it })
                HistoryChoice(stringResource(R.string.inspections_filter_sort), stringResource(sort.label()), "history_sort", InspectionSort.entries, sort,
                    { stringResource(it.label()) }, { sort = it })
                TextButton({ role = null; status = null; sort = InspectionSort.NEWEST }, Modifier.testTag("history_filter_reset")) {
                    Text(stringResource(R.string.inspections_reset))
                }
            }
        },
        confirmButton = { TextButton({ onApply(current.copy(role = role, status = status, sort = sort)) }, Modifier.testTag("history_apply")) {
            Text(stringResource(R.string.inspections_apply))
        } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun <T> HistoryChoice(title: String, value: String, tag: String, options: List<T>, selected: T,
    label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton({ expanded = true }, Modifier.fillMaxWidth().testTag(tag)) { Text(value) }
            DropdownMenu(expanded, { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem({ Text(label(option)) }, { onSelect(option); expanded = false },
                        modifier = Modifier.testTag("${tag}_${option ?: "ANY"}"),
                        leadingIcon = if (option == selected) { { Text("✓") } } else null)
                }
            }
        }
    }
}

internal fun InspectionState.historyLabel() = if (this == InspectionState.COMPLETED) R.string.inspections_completed else R.string.inspections_in_progress
private fun InspectionSort.label() = when (this) {
    InspectionSort.NEWEST -> R.string.inspections_sort_newest
    InspectionSort.OLDEST -> R.string.inspections_sort_oldest
    InspectionSort.NAME -> R.string.inspections_sort_name
}

@Composable
internal fun InspectionHistoryRow(inspection: Inspection, selected: Boolean, onSelect: () -> Unit) {
    WalkoryRecordRow(inspection.details.name,
        stringResource(R.string.inspections_history_summary, inspection.details.property.address, formattedDate(inspection.details.date),
            stringResource(inspection.details.role.label()), stringResource(inspection.state.historyLabel()),
            stringResource(if (inspection.propertyId == null) R.string.objects_standalone else R.string.objects_linked)),
        selected, onSelect, Modifier.testTag("inspection_${inspection.id.value}"))
}
