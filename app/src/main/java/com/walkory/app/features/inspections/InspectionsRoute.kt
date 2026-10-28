package com.walkory.app.features.inspections

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.*
import androidx.navigation3.ui.NavDisplay
import com.walkory.app.R
import com.walkory.app.app.CollectionState
import com.walkory.app.domain.*
import com.walkory.app.ui_kit.*
import kotlinx.serialization.Serializable

@Serializable private data object InspectionListRoute : NavKey
@Serializable private data object InspectionRoleRoute : NavKey
@Serializable private data object InspectionDetailsRoute : NavKey
@Serializable private data object InspectionRoomsRoute : NavKey
@Serializable private data object InspectionReadyRoute : NavKey
@Serializable private data class PreparedRoomRoute(val id: String) : NavKey
@Serializable private data class RoomMacroRoute(val id: String) : NavKey
@Serializable private data class InspectionOverviewRoute(val id: String) : NavKey

@Composable
internal fun rememberInspectionCreationModel(inspections: InspectionRepository, templates: TemplateRepository): InspectionCreationViewModel {
    val factory = remember(inspections, templates) { viewModelFactory {
        initializer { InspectionCreationViewModel(inspections, templates, createSavedStateHandle()) }
    } }
    return viewModel(factory = factory)
}

@Stable
internal class InspectionsNavigation(val stack: NavBackStack<NavKey>) {
    fun root() { while (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun reselect(model: InspectionCreationViewModel) {
        if (model.state.value.draft != null) model.requestDiscard() else root()
    }
}

@Composable
internal fun rememberInspectionsNavigation(): InspectionsNavigation {
    val stack = rememberNavBackStack(InspectionListRoute)
    return remember(stack) { InspectionsNavigation(stack) }
}

@Composable
internal fun InspectionsWorkspace(
    model: InspectionCreationViewModel, records: CollectionState<Inspection>, expanded: Boolean,
    navigation: InspectionsNavigation, onRetry: () -> Unit,
    workflow: InspectionWorkflowViewModel,
    photographs: com.walkory.app.features.media.PhotographViewModel,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    LaunchedEffect(locale) { model.setLanguage(if (locale.language == "ru") AppLanguage.RUSSIAN else AppLanguage.ENGLISH) }
    val stack = navigation.stack
    val current = stack.last()
    val saving = state.saveStatus == SaveStatus.SAVING
    val back: () -> Unit = {
        if (!saving) {
            if (stack.last() == InspectionRoleRoute) model.requestDiscard()
            else if (stack.size > 1) stack.removeAt(stack.lastIndex)
        }
    }
    BackHandler(stack.size > 1, back)
    val select: (String) -> Unit = { id ->
        if (stack.last() is InspectionOverviewRoute) stack.removeAt(stack.lastIndex)
        stack.add(InspectionOverviewRoute(id))
    }
    LaunchedEffect(state.createdId) {
        state.createdId?.let { id -> navigation.root(); select(id); model.acknowledgeCreated() }
    }
    val title = when (current) {
        InspectionRoleRoute -> stringResource(R.string.inspections_role_title)
        InspectionDetailsRoute -> stringResource(R.string.inspections_details_title)
        InspectionRoomsRoute -> stringResource(R.string.inspections_rooms_title)
        InspectionReadyRoute -> stringResource(R.string.inspections_ready_title)
        is PreparedRoomRoute -> state.draft?.rooms?.find { it.id == current.id }?.name?.ifBlank { null }
            ?: stringResource(R.string.inspections_prepare_room)
        is RoomMacroRoute -> stringResource(R.string.inspections_macro_title)
        is InspectionOverviewRoute -> (records as? CollectionState.Content)?.items?.find { it.id.value == current.id }?.details?.name
            ?: stringResource(R.string.inspections_title)
        else -> stringResource(R.string.inspections_title)
    }
    val reducedMotion = LocalReducedMotion.current
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { if (current !is InspectionOverviewRoute) WalkoryTopAppBar(title, if (stack.size > 1) back else null) },
        floatingActionButton = {
            if (current == InspectionListRoute || (expanded && current is InspectionOverviewRoute)) {
                FloatingActionButton({ model.begin(); stack.add(InspectionRoleRoute) }, Modifier.testTag("inspection_new")) {
                    WalkoryIcon(Icons.Rounded.Add, description = stringResource(R.string.inspections_new))
                }
            }
        },
    ) { padding ->
        NavDisplay(
            backStack = stack, onBack = back, modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
            transitionSpec = { if (reducedMotion) EnterTransition.None togetherWith ExitTransition.None else androidx.compose.animation.fadeIn() togetherWith androidx.compose.animation.fadeOut() },
            popTransitionSpec = { if (reducedMotion) EnterTransition.None togetherWith ExitTransition.None else androidx.compose.animation.fadeIn() togetherWith androidx.compose.animation.fadeOut() },
            predictivePopTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
            entryProvider = entryProvider<NavKey> {
                entry<InspectionListRoute> { InspectionBrowser(records, null, expanded, select, onRetry) {} }
                entry<InspectionOverviewRoute> { route ->
                    key(route.id) {
                        InspectionBrowser(records, route.id, expanded, select, onRetry) { inspection ->
                            InspectionWorkflow(workflow, inspection.id, back, state, model::retryTemplates, photographs)
                        }
                    }
                }
                entry<InspectionRoleRoute> { InspectionRoleForm(state, model::role) { stack.add(InspectionDetailsRoute) } }
                entry<InspectionDetailsRoute> { InspectionDetailsForm(state, model::details, model::date) {
                    if (model.validateDetails()) stack.add(InspectionRoomsRoute)
                } }
                entry<InspectionRoomsRoute> { InspectionRoomsForm(state, model::addRoom, { stack.add(PreparedRoomRoute(it)) }, model::removeRoom) {
                    if (model.validateRooms()) stack.add(InspectionReadyRoute)
                } }
                entry<PreparedRoomRoute> { route -> PreparedRoomForm(state, route.id, model::renameRoom,
                    { stack.add(RoomMacroRoute(route.id)) }, model::addCheck, model::editCheck, model::removeCheck, model::moveCheck, back) }
                entry<RoomMacroRoute> { route -> RoomMacroForm(state, route.id, model::retryTemplates) { template ->
                    model.useTemplate(route.id, template); back()
                } }
                entry<InspectionReadyRoute> { InspectionReadyForm(state, { stack.add(PreparedRoomRoute(it)) }, model::create) }
            },
        )
    }
    if (state.confirmDiscard) WalkoryConfirmation(
        stringResource(R.string.inspections_discard_title), stringResource(R.string.inspections_discard_body),
        stringResource(R.string.inspections_discard), { model.discard(); navigation.root() }, model::cancelDiscard,
    )
}
