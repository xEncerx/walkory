package com.walkory.app.features.settings

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.walkory.app.R
import com.walkory.app.app.Information
import com.walkory.app.app.titleResource
import com.walkory.app.domain.*
import com.walkory.app.ui_kit.*

@Composable
internal fun rememberSettingsModel(repository: SettingsRepository): SettingsViewModel {
    val factory = remember(repository) { viewModelFactory { initializer { SettingsViewModel(repository, createSavedStateHandle()) } } }
    return viewModel(factory = factory)
}

@Composable
internal fun SettingsWorkspace(model: SettingsViewModel, onInformation: (Information) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val p = state.preferences
    if (p == null) {
        if (state.loadError) WalkoryError(model::retry) else WalkoryLoading()
        return
    }
    BackHandler(state.edit != null || state.confirmReset) { if (state.edit != null) model.dismiss() else model.cancelReset() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = WalkoryLayout.contentWidth).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(WalkorySpacing.medium).testTag("settings_list"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.loadError) WalkoryError(model::retry)
            SettingsSection.entries.forEach { section ->
                TextButton({ model.open(section) }, Modifier.fillMaxWidth().testTag("settings_${section.name}")) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(stringResource(section.title()), style = MaterialTheme.typography.titleMedium)
                        Text(when (section) {
                            SettingsSection.LANGUAGE -> stringResource(p.language.label())
                            SettingsSection.THEME -> stringResource(p.theme.label())
                            SettingsSection.ACCENT -> stringResource(p.accent.label())
                            SettingsSection.ACCESSIBILITY -> stringResource(R.string.settings_accessibility_summary, p.textScale.percent)
                            SettingsSection.PDF -> stringResource(p.pdfPhotoQuality.label())
                            SettingsSection.INSPECTOR -> p.defaultInspector ?: stringResource(R.string.settings_not_set)
                        }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Information.entries.forEach { page ->
                TextButton({ onInformation(page) }, Modifier.fillMaxWidth().testTag("settings_info_${page.name}")) {
                    Text(stringResource(page.titleResource()))
                }
            }
            OutlinedButton(model::requestReset, Modifier.fillMaxWidth().testTag("settings_reset")) { Text(stringResource(R.string.settings_reset)) }
            WalkorySaveStatus(state.saveStatus, if (state.confirmReset) model::reset else model::apply)
        }
    }
    state.edit?.let { draft -> SettingsEditor(draft, state.saveStatus, model::edit, model::apply, model::dismiss) }
    if (state.confirmReset) {
        AlertDialog(
            onDismissRequest = model::cancelReset,
            title = { Text(stringResource(R.string.settings_reset_title)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.settings_reset_body))
                WalkorySaveStatus(state.saveStatus, model::reset)
            } },
            confirmButton = { TextButton(model::reset, enabled = state.saveStatus != SaveStatus.SAVING,
                modifier = Modifier.testTag("settings_reset_confirm")) { Text(stringResource(R.string.settings_reset)) } },
            dismissButton = { TextButton(model::cancelReset, enabled = state.saveStatus != SaveStatus.SAVING) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
private fun SettingsEditor(draft: SettingsEdit, status: SaveStatus, onEdit: (SettingsEdit) -> Unit, onApply: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(decorFitsSystemWindows = false)) {
        Surface(modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing), shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.widthIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(WalkorySpacing.large)
                .testTag("settings_editor"), verticalArrangement = Arrangement.spacedBy(WalkorySpacing.medium)) {
                Text(stringResource(draft.section.title()), style = MaterialTheme.typography.headlineSmall)
                val enabled = status != SaveStatus.SAVING
                when (draft.section) {
                    SettingsSection.LANGUAGE -> {
                        Choices(AppLanguage.entries, draft.choice, { stringResource(it.label()) }, enabled) { onEdit(draft.copy(choice = it)) }
                        Text(stringResource(R.string.settings_language_note))
                    }
                    SettingsSection.THEME -> Choices(AppTheme.entries, draft.choice, { stringResource(it.label()) }, enabled) { onEdit(draft.copy(choice = it)) }
                    SettingsSection.ACCENT -> {
                        Choices(Accent.entries, draft.choice, { stringResource(it.label()) }, enabled) { onEdit(draft.copy(choice = it)) }
                        Toggle(stringResource(R.string.settings_dynamic_colors), draft.dynamic, enabled, "settings_dynamic") { onEdit(draft.copy(dynamic = it)) }
                        Text(stringResource(if (Build.VERSION.SDK_INT >= 31) R.string.settings_dynamic_note else R.string.settings_dynamic_fallback))
                        val preview = MaterialTheme.colorScheme.withAccent(Accent.valueOf(draft.choice), MaterialTheme.colorScheme.surface.luminance() < 0.5f)
                        Surface(color = preview.primaryContainer, contentColor = preview.onPrimaryContainer,
                            shape = MaterialTheme.shapes.medium) {
                            Text(stringResource(R.string.settings_accent_preview), Modifier.fillMaxWidth().padding(WalkorySpacing.medium))
                        }
                    }
                    SettingsSection.ACCESSIBILITY -> {
                        Choices(AppTextScale.entries, draft.choice, { stringResource(R.string.settings_percent, it.percent) }, enabled) { onEdit(draft.copy(choice = it)) }
                        Text(stringResource(R.string.settings_scale_note))
                        Toggle(stringResource(R.string.settings_contrast), draft.contrast, enabled, "settings_contrast") { onEdit(draft.copy(contrast = it)) }
                        Toggle(stringResource(R.string.settings_motion), draft.motion, enabled, "settings_motion") { onEdit(draft.copy(motion = it)) }
                    }
                    SettingsSection.PDF -> {
                        Choices(PdfPhotoQuality.entries, draft.choice, { stringResource(it.label()) }, enabled) { onEdit(draft.copy(choice = it)) }
                        Text(stringResource(R.string.settings_pdf_note))
                    }
                    SettingsSection.INSPECTOR -> {
                        OutlinedTextField(draft.choice, { onEdit(draft.copy(choice = it)) }, label = { Text(stringResource(R.string.settings_inspector)) },
                            enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("settings_inspector_input"))
                        Text(stringResource(R.string.settings_inspector_note))
                    }
                }
                WalkorySaveStatus(status, onApply)
                // A column keeps both actions readable at combined app/system large font sizes.
                Button(onApply, Modifier.fillMaxWidth().testTag("settings_apply"), enabled = enabled) { Text(stringResource(R.string.settings_apply)) }
                TextButton(onDismiss, Modifier.fillMaxWidth().testTag("settings_cancel"), enabled = enabled) { Text(stringResource(R.string.common_cancel)) }
            }
        }
    }
}

@Composable
private fun <T : Enum<T>> Choices(values: List<T>, selected: String, label: @Composable (T) -> String, enabled: Boolean, choose: (String) -> Unit) {
    Column(Modifier.selectableGroup()) {
        values.forEach { value ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(value.name == selected, enabled = enabled,
                role = Role.RadioButton, onClick = { choose(value.name) }).testTag("settings_choice_${value.name}")
                .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(value.name == selected, onClick = null, enabled = enabled)
                Text(label(value), Modifier.weight(1f).padding(start = 12.dp))
            }
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, enabled: Boolean, tag: String, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = change)
        .testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked, onCheckedChange = null, enabled = enabled)
    }
}

internal fun SettingsSection.title(): Int = when (this) {
    SettingsSection.LANGUAGE -> R.string.settings_language
    SettingsSection.THEME -> R.string.settings_theme
    SettingsSection.ACCENT -> R.string.settings_accent
    SettingsSection.ACCESSIBILITY -> R.string.settings_accessibility
    SettingsSection.PDF -> R.string.settings_pdf
    SettingsSection.INSPECTOR -> R.string.settings_inspector
}
internal fun AppLanguage.label(): Int = when (this) { AppLanguage.SYSTEM -> R.string.settings_system; AppLanguage.RUSSIAN -> R.string.settings_russian; AppLanguage.ENGLISH -> R.string.settings_english }
internal fun AppTheme.label(): Int = when (this) { AppTheme.SYSTEM -> R.string.settings_system; AppTheme.LIGHT -> R.string.settings_light; AppTheme.DARK -> R.string.settings_dark }
internal fun Accent.label(): Int = when (this) {
    Accent.TEAL -> R.string.settings_teal; Accent.BLUE -> R.string.settings_blue; Accent.GREEN -> R.string.settings_green
    Accent.PURPLE -> R.string.settings_purple; Accent.CORAL -> R.string.settings_coral; Accent.AMBER -> R.string.settings_amber; Accent.MONOCHROME -> R.string.settings_monochrome
}
internal fun PdfPhotoQuality.label(): Int = when (this) { PdfPhotoQuality.COMPACT -> R.string.settings_compact; PdfPhotoQuality.BALANCED -> R.string.settings_balanced; PdfPhotoQuality.HIGH -> R.string.settings_high }
