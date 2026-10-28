package com.walkory.app.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.walkory.app.R
import com.walkory.app.ui_kit.WalkoryMessage
import com.walkory.app.ui_kit.WalkorySpacing

internal fun MainArea.titleResource(): Int = when (this) {
    MainArea.INSPECTIONS -> R.string.inspections_title
    MainArea.OBJECTS -> R.string.objects_title
    MainArea.TEMPLATES -> R.string.templates_title
    MainArea.SETTINGS -> R.string.settings_title
}


@Composable
internal fun SettingsScreen(information: Information?, model: com.walkory.app.features.settings.SettingsViewModel, onInformation: (Information) -> Unit) {
    if (information != null) InformationScreen(information, onInformation)
    else com.walkory.app.features.settings.SettingsWorkspace(model, onInformation)
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
