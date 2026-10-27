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
import com.walkory.app.app.CollectionState
import com.walkory.app.domain.*
import com.walkory.app.ui_kit.*

@Composable
internal fun InspectionBrowser(records: CollectionState<Inspection>, selectedId: String?, expanded: Boolean, onSelect: (String) -> Unit, onRetry: () -> Unit) {
    WalkoryListDetail(expanded, selectedId != null, list = {
        LazyColumn(Modifier.fillMaxSize().testTag("inspection_list"), contentPadding = PaddingValues(
            start = WalkorySpacing.medium, top = WalkorySpacing.medium, end = WalkorySpacing.medium, bottom = WalkoryLayout.floatingActionClearance),
            verticalArrangement = Arrangement.spacedBy(WalkorySpacing.medium)) {
            when (records) {
                CollectionState.Loading -> item { WalkoryLoading() }
                CollectionState.Error -> item { WalkoryError(onRetry) }
                is CollectionState.Content -> {
                    if (records.items.isEmpty()) item { WalkoryMessage(stringResource(R.string.inspections_empty_title), stringResource(R.string.inspections_empty_body)) }
                    items(records.items, key = { it.id.value }) { inspection ->
                        WalkoryRecordRow(inspection.details.name, inspection.details.property.address, inspection.id.value == selectedId,
                            { onSelect(inspection.id.value) }, Modifier.testTag("inspection_${inspection.id.value}"))
                    }
                }
            }
        }
    }, detail = {
        when (records) {
            CollectionState.Loading -> WalkoryLoading()
            CollectionState.Error -> WalkoryError(onRetry)
            is CollectionState.Content -> {
                val inspection = records.items.find { it.id.value == selectedId }
                if (selectedId == null) Column(Modifier.padding(WalkorySpacing.large)) { WalkoryMessage(stringResource(R.string.common_select_title), stringResource(R.string.common_select_body)) }
                else if (inspection == null) WalkoryMessage(stringResource(R.string.common_missing_title), stringResource(R.string.common_missing_body))
                else InspectionOverview(inspection)
            }
        }
    })
}

@Composable
private fun InspectionOverview(inspection: Inspection) {
    var roomId by rememberSaveable(inspection.id.value) { mutableStateOf<String?>(null) }
    val progress = inspection.progress()
    LazyColumn(Modifier.fillMaxSize().testTag("inspection_overview"), contentPadding = PaddingValues(WalkorySpacing.medium),
        verticalArrangement = Arrangement.spacedBy(WalkorySpacing.medium)) {
        item {
            Text(inspection.details.property.address, style = MaterialTheme.typography.titleLarge)
            Text(stringResource(inspection.details.role.label()))
            Text(formattedDate(inspection.details.date))
            inspection.details.inspector?.takeIf { it.isNotEmpty() }?.let { Text(stringResource(R.string.inspections_inspector_value, it)) }
            Text(stringResource(if (inspection.state == InspectionState.COMPLETED) R.string.inspections_completed else R.string.inspections_in_progress))
            Text(stringResource(R.string.inspections_progress, progress.checkedChecks, progress.totalChecks))
            Text(pluralStringResource(R.plurals.inspections_unchecked, progress.uncheckedChecks, progress.uncheckedChecks))
            Text(stringResource(R.string.inspections_issue_counts, progress.issueCount, progress.problematicChecks))
        }
        items(inspection.rooms, key = { it.id.value }) { room ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(WalkorySpacing.medium), verticalArrangement = Arrangement.spacedBy(WalkorySpacing.small)) {
                    WalkoryRecordRow(room.name, pluralStringResource(R.plurals.inspections_checks, room.checks.size, room.checks.size), roomId == room.id.value,
                        { roomId = if (roomId == room.id.value) null else room.id.value }, Modifier.testTag("overview_room_${room.id.value}"))
                    if (roomId == room.id.value) {
                        if (room.checks.isEmpty()) Text(stringResource(R.string.inspections_empty_checks))
                        room.checks.forEach { check ->
                            Text(check.title, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(when (check.state) {
                                CheckState.UNCHECKED -> R.string.inspections_check_unchecked
                                CheckState.OK -> R.string.inspections_check_ok
                                CheckState.HAS_ISSUE -> R.string.inspections_check_issue
                                CheckState.NOT_APPLICABLE -> R.string.inspections_check_na
                            }))
                            check.guidance?.let { Text(it) }
                        }
                    }
                }
            }
        }
    }
}
