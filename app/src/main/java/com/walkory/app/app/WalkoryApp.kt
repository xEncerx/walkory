package com.walkory.app.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.walkory.app.R
import com.walkory.app.domain.AppLanguage
import com.walkory.app.ui_kit.LocalReducedMotion
import com.walkory.app.ui_kit.WalkoryIcon
import com.walkory.app.ui_kit.WalkorySpacing
import com.walkory.app.ui_kit.WalkoryTheme
import com.walkory.app.ui_kit.useTwoPanes

@Composable
internal fun WalkoryApp() {
    val application = LocalContext.current.applicationContext as WalkoryApplication
    val model: ShellViewModel = viewModel(factory = ShellViewModel.Factory(application.container))
    val state by model.state.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    LaunchedEffect(locale) {
        model.setLanguage(if (locale.language == "ru") AppLanguage.RUSSIAN else AppLanguage.ENGLISH)
    }
    WalkoryTheme { WalkoryShell(state, model::retry) }
}

@Composable
internal fun WalkoryShell(state: ShellState, onRetry: () -> Unit, expanded: Boolean = useTwoPanes()) {
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
    val openRecord: (String) -> Unit = { id ->
        val destination = RecordRoute(selectedArea, id)
        if (backStack.last() != destination) {
            // Adjacent selections replace the detail, rather than growing a tablet-only history.
            if (backStack.last() is RecordRoute) backStack.removeAt(backStack.lastIndex)
            backStack.add(destination)
        }
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
                            onClick = { selectedArea = area },
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
                    Row(
                        Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                            .padding(WalkorySpacing.small),
                        horizontalArrangement = Arrangement.spacedBy(WalkorySpacing.small),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        if (canGoBack) IconButton(onBack, Modifier.testTag("navigate_back")) {
                            WalkoryIcon(Icons.AutoMirrored.Rounded.ArrowBack, description = stringResource(R.string.common_back))
                        }
                        Text(stringResource(title), style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.weight(1f).padding(WalkorySpacing.small).semantics { heading() })
                    }
                },
                bottomBar = {
                    if (!expanded) NavigationBar {
                        MainArea.entries.forEach { area ->
                            NavigationBarItem(
                                selected = area == selectedArea,
                                onClick = { selectedArea = area },
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
                                        AreaScreen(route.area, state, null, null, expanded, openRecord, openInformation, onRetry)
                                    }
                                    entry<RecordRoute> { route ->
                                        AreaScreen(route.area, state, route.id, null, expanded, openRecord, openInformation, onRetry)
                                    }
                                    entry<InformationRoute> { route ->
                                        AreaScreen(selectedArea, state, null, route.page, expanded, openRecord, openInformation, onRetry)
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
