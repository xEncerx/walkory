package com.walkory.app.features.inspections

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.walkory.app.R
import com.walkory.app.app.CollectionState
import com.walkory.app.domain.*
import com.walkory.app.ui_kit.*

@Composable
internal fun InspectionBrowser(records: CollectionState<Inspection>, selectedId: String?, expanded: Boolean, onSelect: (String) -> Unit,
    onRetry: () -> Unit, detail: @Composable (Inspection) -> Unit) {
    val currentDetail by rememberUpdatedState(detail)
    // Keep contextual navigation and modal input when the detail moves between pane layouts.
    val retainedDetail = remember { movableContentOf<Inspection> { currentDetail(it) } }
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
                else retainedDetail(inspection)
            }
        }
    })
}

