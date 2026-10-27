package com.walkory.app.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.walkory.app.R
import com.walkory.app.domain.InspectionRole
import com.walkory.app.domain.InspectionState
import com.walkory.app.ui_kit.WalkoryError
import com.walkory.app.ui_kit.WalkoryListDetail
import com.walkory.app.ui_kit.WalkoryLoading
import com.walkory.app.ui_kit.WalkoryMessage
import com.walkory.app.ui_kit.WalkorySpacing
import com.walkory.app.ui_kit.WalkoryRecordRow
import com.walkory.app.ui_kit.WalkoryActionRow
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal fun MainArea.titleResource(): Int = when (this) {
    MainArea.INSPECTIONS -> R.string.inspections_title
    MainArea.OBJECTS -> R.string.objects_title
    MainArea.TEMPLATES -> R.string.templates_title
    MainArea.SETTINGS -> R.string.settings_title
}

private data class RecordSummary(val id: String, val title: String, val details: List<String>)

@Composable
internal fun AreaScreen(
    area: MainArea,
    state: ShellState,
    selectedId: String?,
    information: Information?,
    expanded: Boolean,
    onSelect: (String) -> Unit,
    onInformation: (Information) -> Unit,
    onRetry: () -> Unit,
) {
    if (area == MainArea.SETTINGS) {
        WalkoryListDetail(expanded, information != null, list = {
            InformationLinks(onInformation)
        }, detail = {
            if (information != null) InformationScreen(information, onInformation) else SelectionPrompt()
        })
        return
    }
    val locale = LocalConfiguration.current.locales[0]
    val records: CollectionState<RecordSummary> = when (area) {
        MainArea.INSPECTIONS -> state.inspections.present { inspection ->
            val role = stringResource(if (inspection.details.role == InspectionRole.TENANT)
                R.string.inspections_tenant else R.string.inspections_landlord)
            val status = stringResource(if (inspection.state == InspectionState.COMPLETED)
                R.string.inspections_completed else R.string.inspections_in_progress)
            RecordSummary(inspection.id.value, inspection.details.name, listOf(
                inspection.details.property.address,
                inspection.details.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)),
                role, status,
                pluralStringResource(R.plurals.inspections_rooms, inspection.rooms.size, inspection.rooms.size),
            ))
        }
        MainArea.OBJECTS -> state.objects.present { RecordSummary(it.id.value, it.name, listOf(it.address)) }
        MainArea.TEMPLATES, MainArea.SETTINGS -> error("Area has its own feature surface")
    }
    WalkoryListDetail(expanded, selectedId != null || information != null, list = {
        RecordList(area, records, selectedId, onSelect, onInformation, onRetry)
    }, detail = {
        if (information != null) InformationScreen(information, onInformation)
        else when (records) {
            CollectionState.Loading -> WalkoryLoading()
            CollectionState.Error -> WalkoryError(onRetry)
            is CollectionState.Content -> {
                val selected = records.items.find { it.id == selectedId }
                if (selectedId == null) SelectionPrompt()
                else if (selected == null) WalkoryMessage(stringResource(R.string.common_missing_title), stringResource(R.string.common_missing_body))
                else LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("record_detail"),
                    contentPadding = PaddingValues(WalkorySpacing.large),
                    verticalArrangement = Arrangement.spacedBy(WalkorySpacing.medium),
                ) {
                    item { Text(selected.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() }) }
                    items(selected.details) { Text(it, style = MaterialTheme.typography.bodyLarge) }
                }
            }
        }
    })
}

@Composable
private inline fun <T, R> CollectionState<T>.present(transform: @Composable (T) -> R): CollectionState<R> = when (this) {
    CollectionState.Loading -> CollectionState.Loading
    CollectionState.Error -> CollectionState.Error
    is CollectionState.Content -> CollectionState.Content(items.map { transform(it) })
}

@Composable
private fun RecordList(
    area: MainArea,
    state: CollectionState<RecordSummary>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onInformation: (Information) -> Unit,
    onRetry: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().testTag("record_list"),
        contentPadding = PaddingValues(WalkorySpacing.medium),
        verticalArrangement = Arrangement.spacedBy(WalkorySpacing.medium),
    ) {
        when (state) {
            CollectionState.Loading -> item { WalkoryLoading() }
            CollectionState.Error -> item { WalkoryError(onRetry) }
            is CollectionState.Content -> if (state.items.isEmpty()) item {
                val title = when (area) {
                    MainArea.INSPECTIONS -> R.string.inspections_empty_title
                    MainArea.OBJECTS -> R.string.objects_empty_title
                    else -> R.string.templates_empty_title
                }
                val body = when (area) {
                    MainArea.INSPECTIONS -> R.string.inspections_empty_body
                    MainArea.OBJECTS -> R.string.objects_empty_body
                    else -> R.string.templates_empty_body
                }
                WalkoryMessage(stringResource(title), stringResource(body))
            } else items(state.items, key = { it.id }) { record ->
                WalkoryRecordRow(
                    title = record.title,
                    supporting = record.details.first(),
                    selected = record.id == selectedId,
                    onClick = { onSelect(record.id) },
                    modifier = Modifier.fillMaxWidth().testTag("record_${record.id}"),
                )
            }
        }
        item { TextButton({ onInformation(Information.HELP) }) { Text(stringResource(R.string.common_help)) } }
    }
}

@Composable
private fun SelectionPrompt() {
    Column(Modifier.padding(WalkorySpacing.large)) {
        WalkoryMessage(stringResource(R.string.common_select_title), stringResource(R.string.common_select_body))
    }
}

@Composable
private fun InformationLinks(onInformation: (Information) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(WalkorySpacing.medium)) {
        items(Information.entries) { page ->
            WalkoryActionRow(stringResource(page.titleResource()), { onInformation(page) })
        }
    }
}

internal fun Information.titleResource(): Int = when (this) {
    Information.HELP -> R.string.common_help
    Information.ABOUT -> R.string.settings_about
    Information.LOCAL_DATA -> R.string.common_local_data
}

@Composable
internal fun InformationScreen(page: Information, onInformation: (Information) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().testTag("info_${page.name}"),
        contentPadding = PaddingValues(WalkorySpacing.large),
        verticalArrangement = Arrangement.spacedBy(WalkorySpacing.medium),
    ) {
        item { Text(stringResource(page.titleResource()), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() }) }
        when (page) {
            Information.ABOUT -> {
                item { WalkoryMessage(stringResource(R.string.common_app_name), stringResource(R.string.settings_about_body)) }
                item { TextButton({ onInformation(Information.HELP) }) { Text(stringResource(R.string.common_help)) } }
                item { TextButton({ onInformation(Information.LOCAL_DATA) }) { Text(stringResource(R.string.common_local_data)) } }
            }
            Information.LOCAL_DATA -> item { Text(stringResource(R.string.common_local_data_body)) }
            Information.HELP -> {
                item { Text(stringResource(R.string.common_help_intro), style = MaterialTheme.typography.titleLarge) }
                item { Text(stringResource(R.string.common_help_role)) }
                item { Text(stringResource(R.string.common_help_rooms)) }
                item { Text(stringResource(R.string.common_help_results)) }
            }
        }
    }
}
