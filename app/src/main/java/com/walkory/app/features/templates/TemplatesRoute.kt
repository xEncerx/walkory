package com.walkory.app.features.templates

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.walkory.app.domain.*
import com.walkory.app.ui_kit.*
import kotlinx.serialization.Serializable

@Serializable private data object TemplateCatalogRoute : NavKey
@Serializable private data class TemplateDetailRoute(val id: String) : NavKey
@Serializable private data class TemplateEditorRoute(val id: String) : NavKey

/** Create this owner alongside the shell, not inside a destination's transient registry. */
@Composable
internal fun rememberTemplatesViewModel(repository: TemplateRepository): TemplatesViewModel {
    val factory = remember(repository) { viewModelFactory { initializer { TemplatesViewModel(repository, createSavedStateHandle()) } } }
    return viewModel(factory = factory)
}

@Stable
internal class TemplatesNavigationState(
    val backStack: NavBackStack<NavKey>,
    val filter: MutableState<TemplateFilter>,
    val roles: MutableState<List<InspectionRole>>,
    val deleteId: MutableState<String?>,
    val returningToCatalog: MutableState<Boolean>,
    val query: MutableState<String>,
) {
    fun returnToCatalog(model: TemplatesViewModel) {
        if (backStack.last() is TemplateEditorRoute) {
            if (model.state.value.saveStatus == SaveStatus.SAVING) return
            returningToCatalog.value = true
            model.requestClose()
        } else showCatalog()
    }

    fun showCatalog() {
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        deleteId.value = null
        returningToCatalog.value = false
    }
}

/** Navigation and drafts share the shell's process-restorable owner. */
@Composable
internal fun rememberTemplatesNavigation(): TemplatesNavigationState {
    val stack = rememberNavBackStack(TemplateCatalogRoute)
    val filter = rememberSaveable { mutableStateOf(TemplateFilter.ALL) }
    val roles = rememberSaveable { mutableStateOf(InspectionRole.entries.toList()) }
    val deletion = rememberSaveable { mutableStateOf<String?>(null) }
    val returning = rememberSaveable { mutableStateOf(false) }
    val query = rememberSaveable { mutableStateOf("") }
    return remember(stack, filter, roles, deletion, returning, query) { TemplatesNavigationState(stack, filter, roles, deletion, returning, query) }
}

@Composable
internal fun TemplatesWorkspace(model: TemplatesViewModel, expanded: Boolean, navigation: TemplatesNavigationState = rememberTemplatesNavigation()) {
    val state by model.state.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    LaunchedEffect(locale) { model.setLanguage(if (locale.language == "ru") AppLanguage.RUSSIAN else AppLanguage.ENGLISH) }
    val backStack = navigation.backStack
    var filter by navigation.filter
    var roles by navigation.roles
    var deleteId by navigation.deleteId
    val current = backStack.last()
    val back: () -> Unit = {
        if (backStack.last() is TemplateEditorRoute) model.requestClose()
        else if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }
    BackHandler(backStack.size > 1, back)
    val select: (String) -> Unit = { id ->
        val destination = TemplateDetailRoute(id)
        if (backStack.last() != destination) {
            if (backStack.last() is TemplateDetailRoute) backStack.removeAt(backStack.lastIndex)
            backStack.add(destination)
        }
    }
    LaunchedEffect(state.exitEditor) {
        if (state.exitEditor) {
            val editor = backStack.last() as? TemplateEditorRoute
            if (editor != null) {
                backStack.removeAt(backStack.lastIndex)
                if (navigation.returningToCatalog.value) navigation.showCatalog()
                else if (state.saveStatus == SaveStatus.SAVED) select(editor.id)
            }
            model.acknowledgeExit()
        }
    }
    LaunchedEffect(state.deletedId) {
        state.deletedId?.let { id ->
            if ((backStack.last() as? TemplateDetailRoute)?.id == id) backStack.removeAt(backStack.lastIndex)
            deleteId = null
            model.acknowledgeDeletion()
        }
    }
    val resources = androidx.compose.ui.platform.LocalResources.current
    val createCopy: (ChecklistTemplate) -> Unit = { template ->
        backStack.add(TemplateEditorRoute(model.beginCopy(template, resources.getString(R.string.templates_copy_name, template.name))))
    }
    val new: () -> Unit = { backStack.add(TemplateEditorRoute(model.beginNew())) }
    val edit: (ChecklistTemplate) -> Unit = { template -> model.beginEdit(template)?.let { backStack.add(TemplateEditorRoute(it)) } }
    val reducedMotion = LocalReducedMotion.current
    val title = when (current) {
        is TemplateEditorRoute -> stringResource(if (state.draft?.isNew == true) R.string.templates_new else R.string.templates_edit)
        is TemplateDetailRoute -> state.templates.find { it.id.value == current.id }?.name ?: stringResource(R.string.templates_title)
        else -> stringResource(R.string.templates_title)
    }
    val toggleRole: (InspectionRole) -> Unit = { role ->
        roles = if (role in roles) roles - role else roles + role
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { WalkoryTopAppBar(title, if (backStack.size > 1) back else null) },
        floatingActionButton = {
            if (current == TemplateCatalogRoute || (expanded && current is TemplateDetailRoute)) {
                FloatingActionButton(new, Modifier.testTag("templates_new")) {
                    WalkoryIcon(Icons.Rounded.Add, description = stringResource(R.string.templates_new))
                }
            }
        },
    ) { padding ->
        NavDisplay(
            backStack = backStack,
            onBack = back,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
            transitionSpec = {
                if (reducedMotion) EnterTransition.None togetherWith ExitTransition.None
                else androidx.compose.animation.fadeIn() togetherWith androidx.compose.animation.fadeOut()
            },
            popTransitionSpec = {
                if (reducedMotion) EnterTransition.None togetherWith ExitTransition.None
                else androidx.compose.animation.fadeIn() togetherWith androidx.compose.animation.fadeOut()
            },
            predictivePopTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
            entryProvider = entryProvider<NavKey> {
                entry<TemplateCatalogRoute> {
                    TemplatesBrowser(state, null, expanded, filter, roles, { filter = it }, toggleRole, select, edit, createCopy,
                        { deleteId = it.id.value }, model::retry, navigation.query.value, { navigation.query.value = it })
                }
                entry<TemplateDetailRoute> { route ->
                    TemplatesBrowser(state, route.id, expanded, filter, roles, { filter = it }, toggleRole, select, edit, createCopy,
                        { deleteId = it.id.value }, model::retry, navigation.query.value, { navigation.query.value = it })
                }
                entry<TemplateEditorRoute> { route ->
                    if (state.draft?.id == route.id) TemplateEditor(
                        state, model::rename, model::addItem, model::editItem, model::removeItem, model::moveItem,
                        model::save, model::requestClose,
                    )
                    else TemplateMissing()
                }
            },
        )
    }
    if (state.confirmDiscard && current is TemplateEditorRoute) WalkoryConfirmation(
        stringResource(R.string.templates_discard_title), stringResource(R.string.templates_discard_body),
        stringResource(R.string.templates_discard), model::closeEditor, {
            navigation.returningToCatalog.value = false
            model.cancelDiscard()
        },
    )
    val deleting = state.templates.find { it.id.value == deleteId }
    if (deleting != null && deleting.origin == TemplateOrigin.Custom) AlertDialog(
        onDismissRequest = { if (!state.deleting) deleteId = null },
        title = { Text(stringResource(R.string.templates_delete_title, deleting.name)) },
        text = { Text(stringResource(if (state.deleteFailed) R.string.templates_delete_error else R.string.templates_delete_body)) },
        confirmButton = { TextButton({ model.delete(deleting) }, enabled = !state.deleting) { Text(stringResource(R.string.templates_delete)) } },
        dismissButton = { TextButton({ deleteId = null }, enabled = !state.deleting) { Text(stringResource(R.string.common_cancel)) } },
    )
}
