package com.walkory.app.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.walkory.app.features.inspections.InspectionsWorkspace
import com.walkory.app.features.inspections.rememberInspectionCreationModel
import com.walkory.app.features.inspections.rememberInspectionsNavigation
import com.walkory.app.features.inspections.rememberInspectionWorkflowModel
import com.walkory.app.features.templates.TemplatesWorkspace
import com.walkory.app.features.templates.rememberTemplatesNavigation
import com.walkory.app.features.templates.rememberTemplatesViewModel
import com.walkory.app.ui_kit.LocalReducedMotion
import com.walkory.app.ui_kit.WalkoryIcon
import com.walkory.app.ui_kit.WalkoryTheme
import com.walkory.app.ui_kit.WalkoryTopAppBar
import com.walkory.app.ui_kit.useTwoPanes
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.walkory.app.features.media.PhotographViewModel
import androidx.lifecycle.createSavedStateHandle
import com.walkory.app.features.media.PhotographHost

import com.walkory.app.features.objects.*
import com.walkory.app.features.inspections.rememberInspectionHistoryModel
@Composable
internal fun WalkoryApp() {
    val application = LocalContext.current.applicationContext as WalkoryApplication
    val model: ShellViewModel = viewModel(factory = ShellViewModel.Factory(application.container))
    val state by model.state.collectAsStateWithLifecycle()
    val templates = rememberTemplatesViewModel(application.container.templates)
    val templateNavigation = rememberTemplatesNavigation()
    val creation = rememberInspectionCreationModel(application.container.inspections, application.container.templates)
    val inspectionNavigation = rememberInspectionsNavigation()
    val workflow = rememberInspectionWorkflowModel(application.container.inspections, application.container.templates)
    val history = rememberInspectionHistoryModel(application.container.inspections)
    val properties = rememberPropertiesModel(application.container.properties, "property_workspace")
    val propertyPicker = rememberPropertiesModel(application.container.properties, "inspection_property_picker")
    val propertyNavigation = rememberPropertiesNavigation()
    val photoFactory = remember(application.container) { viewModelFactory {
        initializer { PhotographViewModel(application.container.photographs, application.container.inspections, createSavedStateHandle()) }
    } }
    val photographs: PhotographViewModel = viewModel(factory = photoFactory)
    WalkoryTheme {
        PhotographHost(photographs)
        WalkoryShell(
            onTemplatesReselect = { templateNavigation.returnToCatalog(templates) },
            onInspectionsReselect = { inspectionNavigation.reselect(creation) },
            onObjectsReselect = { propertyNavigation.reselect(properties) },
            objectsContent = { expanded, showInspections ->
                PropertiesWorkspace(properties, state.objects, state.inspections, expanded, propertyNavigation, model::retry,
                    { inspectionNavigation.select(it, propertyNavigation.selectedId()); showInspections() },
                    { inspectionNavigation.begin(creation, it); showInspections() })
            },
            inspectionsContent = { expanded, showProperties -> InspectionsWorkspace(creation, state.inspections, expanded, inspectionNavigation, model::retry,
                workflow, photographs, history, state.objects, propertyPicker) { id ->
                    propertyNavigation.root()
                    if ((state.objects as? CollectionState.Content)?.items?.any { it.id.value == id } == true) propertyNavigation.select(id)
                    showProperties()
                } },
            templatesContent = { expanded -> TemplatesWorkspace(templates, expanded, templateNavigation) },
        )
    }
}

internal typealias AreaContent = @Composable (Boolean) -> Unit

@Composable
internal fun WalkoryShell(
    expanded: Boolean = useTwoPanes(),
    onTemplatesReselect: () -> Unit = {},
    onInspectionsReselect: () -> Unit = {},
    onObjectsReselect: () -> Unit = {},
    objectsContent: @Composable (Boolean, () -> Unit) -> Unit,
    inspectionsContent: @Composable (Boolean, () -> Unit) -> Unit,
    templatesContent: AreaContent,
) {
    var selectedArea by rememberSaveable { mutableStateOf(MainArea.INSPECTIONS) }
    val inspections = rememberNavBackStack(AreaRoute(MainArea.INSPECTIONS))
    val objects = rememberNavBackStack(AreaRoute(MainArea.OBJECTS))
    val templates = rememberNavBackStack(AreaRoute(MainArea.TEMPLATES))
    val settings = rememberNavBackStack(AreaRoute(MainArea.SETTINGS))
    val backStack = when (selectedArea) {
        MainArea.INSPECTIONS -> inspections
        MainArea.OBJECTS -> objects
        MainArea.TEMPLATES -> templates
        MainArea.SETTINGS -> settings
    }
    val selectArea: (MainArea) -> Unit = { area ->
        if (selectedArea == area) {
            if (area == MainArea.TEMPLATES) onTemplatesReselect()
            else if (area == MainArea.INSPECTIONS) onInspectionsReselect()
            else if (area == MainArea.OBJECTS) onObjectsReselect()
            else while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        } else selectedArea = area
    }
    val canGoBack = backStack.size > 1
    val onBack: () -> Unit = {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        else selectedArea = MainArea.INSPECTIONS
    }
    BackHandler(canGoBack || selectedArea != MainArea.INSPECTIONS, onBack)
    val openInformation: (Information) -> Unit = { page ->
        val destination = InformationRoute(page)
        if (backStack.last() != destination) backStack.add(destination)
    }
    val current = backStack.last()
    val title = when (current) {
        is InformationRoute -> current.page.titleResource()
        else -> selectedArea.titleResource()
    }
    val retainedAreas = rememberSaveableStateHolder()
    Surface(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            if (expanded) {
                NavigationRail {
                    MainArea.entries.forEach { area ->
                        NavigationRailItem(
                            selected = area == selectedArea,
                            onClick = { selectArea(area) },
                            icon = { AreaIcon(area) },
                            label = { Text(stringResource(area.titleResource())) },
                            modifier = Modifier.testTag("nav_${area.name}"),
                        )
                    }
                }
            }
            Scaffold(
                modifier = Modifier.weight(1f),
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    if (selectedArea == MainArea.SETTINGS) {
                        WalkoryTopAppBar(stringResource(title), if (canGoBack) onBack else null)
                    }
                },
                bottomBar = {
                    if (!expanded) NavigationBar {
                        MainArea.entries.forEach { area ->
                            NavigationBarItem(
                                selected = area == selectedArea,
                                onClick = { selectArea(area) },
                                icon = { AreaIcon(area) },
                                label = { Text(stringResource(area.titleResource())) },
                                modifier = Modifier.testTag("nav_${area.name}"),
                            )
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
                    retainedAreas.SaveableStateProvider(selectedArea) {
                        key(selectedArea) {
                            val reducedMotion = LocalReducedMotion.current
                            NavDisplay(
                                backStack = backStack,
                                onBack = onBack,
                                entryDecorators = listOf(
                                    rememberSaveableStateHolderNavEntryDecorator(),
                                    rememberViewModelStoreNavEntryDecorator(),
                                ),
                                transitionSpec = {
                                    if (reducedMotion) EnterTransition.None togetherWith ExitTransition.None
                                    else androidx.compose.animation.fadeIn() togetherWith androidx.compose.animation.fadeOut()
                                },
                                popTransitionSpec = {
                                    if (reducedMotion) EnterTransition.None togetherWith ExitTransition.None
                                    else androidx.compose.animation.fadeIn() togetherWith androidx.compose.animation.fadeOut()
                                },
                                predictivePopTransitionSpec = {
                                    if (reducedMotion) EnterTransition.None togetherWith ExitTransition.None
                                    else androidx.compose.animation.fadeIn() togetherWith androidx.compose.animation.fadeOut()
                                },
                                entryProvider = entryProvider<NavKey> {
                                    entry<AreaRoute> { route ->
                                        if (route.area == MainArea.TEMPLATES) templatesContent(expanded)
                                        else if (route.area == MainArea.INSPECTIONS) inspectionsContent(expanded) { selectedArea = MainArea.OBJECTS }
                                        else if (route.area == MainArea.OBJECTS) objectsContent(expanded) { selectedArea = MainArea.INSPECTIONS }
                                        else SettingsScreen(null, expanded, openInformation)
                                    }
                                    entry<InformationRoute> { route ->
                                        SettingsScreen(route.page, expanded, openInformation)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AreaIcon(area: MainArea) {
    WalkoryIcon(when (area) {
        MainArea.INSPECTIONS -> Icons.AutoMirrored.Rounded.FactCheck
        MainArea.OBJECTS -> Icons.Rounded.Apartment
        MainArea.TEMPLATES -> Icons.Rounded.Checklist
        MainArea.SETTINGS -> Icons.Rounded.Settings
    })
}
