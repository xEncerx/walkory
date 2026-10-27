package com.walkory.app.features.inspections

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.*
import androidx.navigation3.ui.NavDisplay
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import com.walkory.app.R
import com.walkory.app.domain.*
import com.walkory.app.ui_kit.*
import kotlinx.serialization.Serializable

@Serializable private data object ResultOverview : NavKey
@Serializable private data class ResultRoom(val id: String, val editing: Boolean = false) : NavKey
@Serializable private data class ResultCheck(val id: String, val issue: String? = null) : NavKey
@Serializable private data class ResultSummary(val issues: Boolean) : NavKey

@Composable
internal fun rememberInspectionWorkflowModel(inspections: InspectionRepository, templates: TemplateRepository): InspectionWorkflowViewModel {
    val factory = remember(inspections, templates) { viewModelFactory { initializer { InspectionWorkflowViewModel(inspections, templates) } } }
    return viewModel(factory = factory)
}

@Composable
internal fun InspectionWorkflow(model: InspectionWorkflowViewModel, id: InspectionId, onBack: () -> Unit,
    templates: InspectionCreationState, onRetryTemplates: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val record = state.records[id]
    when {
        record == null && state.loading -> { WalkoryLoading(); return }
        record == null && state.readError -> { WalkoryError(model::retryRead); return }
        record == null -> { WalkoryMessage(stringResource(R.string.common_missing_title), stringResource(R.string.common_missing_body)); return }
    }
    val inspection = record.inspection
    val stack = rememberNavBackStack(ResultOverview)
    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) else onBack() }
    BackHandler(onBack = back)
    val current = stack.last()
    val title = when (current) {
        is ResultRoom -> if (current.editing) stringResource(R.string.inspections_edit_list) else inspection.rooms.find { it.id.value == current.id }?.name.orEmpty()
        is ResultCheck -> inspection.rooms.asSequence().flatMap { it.checks }.find { it.id.value == current.id }?.title.orEmpty()
        is ResultSummary -> stringResource(if (current.issues) R.string.inspections_issues else R.string.inspections_check_unchecked)
        else -> inspection.details.name
    }
    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = { WalkoryTopAppBar(title, back) }) { padding ->
        Column(Modifier.padding(padding).consumeWindowInsets(padding)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = WalkorySpacing.medium)) {
                WalkorySaveStatus(record.saveStatus, { model.retry(id) })
                if (state.readError) WalkoryError(model::retryRead)
                record.error?.let { error ->
                    Row {
                        Text(stringResource(workflowErrorLabel(error)), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                        TextButton({ model.dismissError(id) }) { Text(stringResource(R.string.inspections_dismiss)) }
                    }
                }
            }
            val reducedMotion = LocalReducedMotion.current
            NavDisplay(backStack = stack, onBack = back, modifier = Modifier.weight(1f),
                entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                transitionSpec = { if (reducedMotion) EnterTransition.None togetherWith ExitTransition.None else androidx.compose.animation.fadeIn() togetherWith androidx.compose.animation.fadeOut() },
                popTransitionSpec = { if (reducedMotion) EnterTransition.None togetherWith ExitTransition.None else androidx.compose.animation.fadeIn() togetherWith androidx.compose.animation.fadeOut() },
                predictivePopTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                entryProvider = entryProvider<NavKey> {
                    entry<ResultOverview> {
                        ResultOverviewScreen(record, model, { stack.add(ResultRoom(it.value)) }, { stack.add(ResultSummary(it)) }, templates, onRetryTemplates)
                    }
                    entry<ResultRoom> { route ->
                        val room = inspection.rooms.find { it.id.value == route.id }
                        if (room != null) ResultRoomScreen(record, room, route.editing, model,
                            { stack.add(ResultCheck(it.value)) }, { stack.add(ResultRoom(room.id.value, true)) }, back, {
                                while (stack.size > 1 && (stack.last() as? ResultRoom)?.id == route.id) stack.removeAt(stack.lastIndex)
                            })
                        else ResultMissing(back)
                    }
                    entry<ResultCheck> { route ->
                        val room = inspection.rooms.find { room -> room.checks.any { it.id.value == route.id } }
                        val check = room?.checks?.find { it.id.value == route.id }
                        if (check != null) ResultCheckScreen(id, room, check, route.issue, model, back)
                        else ResultMissing(back)
                    }
                    entry<ResultSummary> { route ->
                        ResultSummaryScreen(inspection, route.issues) { check, issue -> stack.add(ResultCheck(check.value, issue?.value)) }
                    }
                })
        }
    }
}

@Composable private fun ResultMissing(onBack: () -> Unit) {
    Column(Modifier.padding(WalkorySpacing.medium)) {
        WalkoryMessage(stringResource(R.string.common_missing_title), stringResource(R.string.common_missing_body))
        TextButton(onBack) { Text(stringResource(R.string.inspections_back_list)) }
    }
}

internal fun CheckState.resultLabel(): Int = when (this) {
    CheckState.UNCHECKED -> R.string.inspections_check_unchecked
    CheckState.OK -> R.string.inspections_check_ok
    CheckState.HAS_ISSUE -> R.string.inspections_check_issue
    CheckState.NOT_APPLICABLE -> R.string.inspections_check_na
}

private fun workflowErrorLabel(error: DomainError): Int = when (error) {
    is DomainError.IssuesPreventStateChange -> R.string.inspections_issues_prevent_state
    is DomainError.UncheckedConfirmationRequired -> R.string.inspections_completion_changed
    DomainError.EmptyIssue -> R.string.inspections_empty_issue
    DomainError.EmptyTemplate -> R.string.inspections_empty_template
    DomainError.AttachmentCleanupPending -> R.string.inspections_cleanup_pending
    DomainError.InsufficientStorage -> R.string.inspections_storage_full
    DomainError.StorageUnavailable -> R.string.common_save_error
    is DomainError.BlankField -> R.string.common_required_field
    else -> R.string.inspections_update_failed
}
