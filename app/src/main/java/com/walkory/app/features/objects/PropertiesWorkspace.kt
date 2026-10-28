package com.walkory.app.features.objects

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.*
import androidx.navigation3.ui.NavDisplay
import com.walkory.app.R
import com.walkory.app.app.CollectionState
import com.walkory.app.domain.*
import com.walkory.app.features.inspections.InspectionDiscovery
import com.walkory.app.features.inspections.InspectionHistoryRow
import com.walkory.app.ui_kit.*
import kotlinx.serialization.Serializable

@Serializable private data object PropertyListRoute : NavKey
@Serializable private data class PropertyDetailRoute(val id: String) : NavKey
@Serializable private data object PropertyEditRoute : NavKey

@Stable
internal class PropertiesNavigation(val stack: NavBackStack<NavKey>) {
    fun root() { while (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun selectedId(): String? = (stack.last() as? PropertyDetailRoute)?.id
    fun select(id: String) {
        if (stack.last() is PropertyDetailRoute) stack.removeAt(stack.lastIndex)
        stack.add(PropertyDetailRoute(id))
    }
    fun reselect(model: PropertiesViewModel) {
        if (model.state.value.draft == null || model.requestDiscard(toRoot = true)) root()
    }
}

@Composable internal fun rememberPropertiesNavigation(): PropertiesNavigation {
    val stack = rememberNavBackStack(PropertyListRoute)
    return remember(stack) { PropertiesNavigation(stack) }
}

@Composable
internal fun PropertiesWorkspace(model: PropertiesViewModel, properties: CollectionState<PropertyCard>, inspections: CollectionState<Inspection>,
    expanded: Boolean, navigation: PropertiesNavigation, onRetry: () -> Unit, onInspection: (String) -> Unit, onCreate: (PropertyCard) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val stack = navigation.stack
    val current = stack.last()
    val back: () -> Unit = {
        if (!state.deleting && state.status != SaveStatus.SAVING && (current != PropertyEditRoute || model.requestDiscard())) {
            if (stack.size > 1) stack.removeAt(stack.lastIndex)
        }
    }
    BackHandler(stack.size > 1, back)
    LaunchedEffect(state.savedId) { state.savedId?.let { navigation.root(); navigation.select(it); model.acknowledgeSaved() } }
    LaunchedEffect(state.deletedId) { state.deletedId?.let { navigation.root(); model.acknowledgeDeleted() } }
    val title = when (current) {
        PropertyEditRoute -> stringResource(if (state.draft?.isNew != false) R.string.objects_new else R.string.objects_edit)
        is PropertyDetailRoute -> (properties as? CollectionState.Content)?.items?.find { it.id.value == current.id }?.name
            ?: stringResource(R.string.objects_title)
        else -> stringResource(R.string.objects_title)
    }
    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = { WalkoryTopAppBar(title, if (stack.size > 1) back else null) },
        floatingActionButton = {
            if (current != PropertyEditRoute) ExtendedFloatingActionButton(
                { model.begin(); stack.add(PropertyEditRoute) }, Modifier.testTag("property_new")) { Text(stringResource(R.string.objects_new)) }
        }) { padding ->
        val reducedMotion = LocalReducedMotion.current
        NavDisplay(backStack = stack, onBack = back, modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
            transitionSpec = { if (reducedMotion) EnterTransition.None togetherWith ExitTransition.None else androidx.compose.animation.fadeIn() togetherWith androidx.compose.animation.fadeOut() },
            popTransitionSpec = { if (reducedMotion) EnterTransition.None togetherWith ExitTransition.None else androidx.compose.animation.fadeIn() togetherWith androidx.compose.animation.fadeOut() },
            predictivePopTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
            entryProvider = entryProvider<NavKey> {
                entry<PropertyListRoute> { PropertiesBrowser(properties, inspections, null, expanded, navigation::select, onRetry, state,
                    onInspection, onCreate, { model.begin(it); stack.add(PropertyEditRoute) }, model::delete) }
                entry<PropertyDetailRoute> { route -> PropertiesBrowser(properties, inspections, route.id, expanded, navigation::select, onRetry, state,
                    onInspection, onCreate, { model.begin(it); stack.add(PropertyEditRoute) }, model::delete) }
                entry<PropertyEditRoute> { PropertyEditor(state, model) }
            })
    }
    PropertyDiscardConfirmation(state, model) {
        if (state.discardToRoot) navigation.root()
        else if (stack.last() == PropertyEditRoute) stack.removeAt(stack.lastIndex)
    }
}

@Composable
internal fun PropertiesBrowser(properties: CollectionState<PropertyCard>, inspections: CollectionState<Inspection>, selectedId: String?, expanded: Boolean,
    onSelect: (String) -> Unit, onRetry: () -> Unit, editor: PropertiesState, onInspection: (String) -> Unit, onCreate: (PropertyCard) -> Unit,
    onEdit: (PropertyCard) -> Unit, onDelete: (PropertyId) -> Unit) {
    val currentDetail by rememberUpdatedState<@Composable () -> Unit> {
        when (properties) {
            CollectionState.Loading -> WalkoryLoading()
            CollectionState.Error -> WalkoryError(onRetry)
            is CollectionState.Content -> {
                val property = properties.items.find { it.id.value == selectedId }
                if (selectedId == null) Column(Modifier.padding(WalkorySpacing.large)) {
                    WalkoryMessage(stringResource(R.string.common_select_title), stringResource(R.string.common_select_body))
                } else if (property == null) WalkoryMessage(stringResource(R.string.common_missing_title), stringResource(R.string.common_missing_body))
                else key(property.id) { PropertyDetail(property, inspections, editor, onRetry, onInspection, onCreate, onEdit, onDelete) }
            }
        }
    }
    val retainedDetail = remember { movableContentOf { currentDetail() } }
    WalkoryListDetail(expanded, selectedId != null, list = {
        LazyColumn(Modifier.fillMaxSize().testTag("property_list"), contentPadding = PaddingValues(
            start = WalkorySpacing.medium, top = WalkorySpacing.medium, end = WalkorySpacing.medium, bottom = WalkoryLayout.floatingActionClearance),
            verticalArrangement = Arrangement.spacedBy(WalkorySpacing.medium)) {
            when (properties) {
                CollectionState.Loading -> item { WalkoryLoading() }
                CollectionState.Error -> item { WalkoryError(onRetry) }
                is CollectionState.Content -> {
                    if (properties.items.isEmpty()) item { WalkoryMessage(stringResource(R.string.objects_empty_title), stringResource(R.string.objects_empty_body)) }
                    items(properties.items, key = { it.id.value }) { property ->
                        val count = (inspections as? CollectionState.Content)?.items?.count { it.propertyId == property.id }
                        WalkoryRecordRow(property.name, if (count == null) property.address else stringResource(R.string.objects_summary, property.address,
                            pluralStringResource(R.plurals.objects_inspections, count, count)), property.id.value == selectedId,
                            { onSelect(property.id.value) }, Modifier.testTag("property_${property.id.value}"))
                    }
                }
            }
        }
    }, detail = {
        retainedDetail()
    })
}

@Composable
private fun PropertyDetail(property: PropertyCard, inspections: CollectionState<Inspection>, editor: PropertiesState, onRetry: () -> Unit,
    onInspection: (String) -> Unit, onCreate: (PropertyCard) -> Unit, onEdit: (PropertyCard) -> Unit, onDelete: (PropertyId) -> Unit) {
    var deleting by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().testTag("property_detail"), contentPadding = PaddingValues(
        start = WalkorySpacing.medium, top = WalkorySpacing.medium, end = WalkorySpacing.medium, bottom = WalkoryLayout.floatingActionClearance),
        verticalArrangement = Arrangement.spacedBy(WalkorySpacing.medium)) {
        item {
            Text(property.address, style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.objects_snapshot_notice))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(WalkorySpacing.small)) {
                OutlinedButton({ onEdit(property) }, enabled = !editor.deleting, modifier = Modifier.testTag("property_edit")) { Text(stringResource(R.string.objects_edit)) }
                TextButton({ deleting = true }, enabled = !editor.deleting, modifier = Modifier.testTag("property_delete")) { Text(stringResource(R.string.objects_delete)) }
            }
            if (editor.deleting) WalkoryLoading()
            if (editor.deleteFailedId == property.id) WalkoryError { onDelete(property.id) }
            Button({ onCreate(property) }, enabled = !editor.deleting, modifier = Modifier.testTag("property_inspect")) { Text(stringResource(R.string.objects_inspect)) }
            Text(stringResource(R.string.objects_history), style = MaterialTheme.typography.titleMedium)
        }
        when (inspections) {
            CollectionState.Loading -> item { WalkoryLoading() }
            CollectionState.Error -> item { WalkoryError(onRetry) }
            is CollectionState.Content -> {
                val linked = InspectionDiscovery().select(inspections.items.filter { it.propertyId == property.id })
                if (linked.isEmpty()) item { WalkoryMessage(stringResource(R.string.objects_history_empty), stringResource(R.string.objects_history_empty_body)) }
                items(linked, key = { it.id.value }) { inspection -> InspectionHistoryRow(inspection, false) { onInspection(inspection.id.value) } }
            }
        }
    }
    if (deleting) WalkoryConfirmation(stringResource(R.string.objects_delete_title, property.name), stringResource(R.string.objects_delete_body),
        stringResource(R.string.objects_delete), { deleting = false; onDelete(property.id) }, { deleting = false })
}
