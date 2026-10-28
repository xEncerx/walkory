package com.walkory.app.features.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.walkory.app.domain.*
import com.walkory.app.ui_kit.SaveStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal enum class SettingsSection { LANGUAGE, THEME, ACCENT, ACCESSIBILITY, PDF, INSPECTOR }

@Serializable
internal data class SettingsEdit(
    val section: SettingsSection,
    val choice: String,
    val dynamic: Boolean = false,
    val contrast: Boolean = false,
    val motion: Boolean = false,
)

internal data class SettingsState(
    val preferences: Preferences? = null,
    val loadError: Boolean = false,
    val edit: SettingsEdit? = null,
    val confirmReset: Boolean = false,
    val saveStatus: SaveStatus = SaveStatus.IDLE,
    val localeChange: AppLanguage? = null,
)

internal class SettingsViewModel(private val repository: SettingsRepository, private val saved: SavedStateHandle) : ViewModel() {
    private val mutable = MutableStateFlow(SettingsState(
        edit = saved.get<String>("settings_edit")?.let { Json.decodeFromString<SettingsEdit>(it) },
        confirmReset = saved["settings_reset"] ?: false,
    ))
    val state = mutable.asStateFlow()
    private var observation: kotlinx.coroutines.Job? = null
    init { retry() }

    fun retry() {
        observation?.cancel()
        mutable.update { it.copy(loadError = false) }
        observation = viewModelScope.launch {
            repository.observe().collect { result -> mutable.update { old -> when (result) {
                is DomainResult.Success -> old.copy(preferences = result.value, loadError = false)
                is DomainResult.Failure -> old.copy(loadError = true)
            } } }
        }
    }

    fun open(section: SettingsSection) {
        if (state.value.saveStatus == SaveStatus.SAVING) return
        val p = state.value.preferences ?: return
        edit(SettingsEdit(section, when (section) {
            SettingsSection.LANGUAGE -> p.language.name
            SettingsSection.THEME -> p.theme.name
            SettingsSection.ACCENT -> p.accent.name
            SettingsSection.ACCESSIBILITY -> p.textScale.name
            SettingsSection.PDF -> p.pdfPhotoQuality.name
            SettingsSection.INSPECTOR -> p.defaultInspector.orEmpty()
        }, p.dynamicColors, p.higherContrast, p.reducedMotion))
    }

    fun edit(value: SettingsEdit) {
        if (state.value.saveStatus == SaveStatus.SAVING) return
        saved["settings_edit"] = Json.encodeToString(value)
        mutable.update { it.copy(edit = value, saveStatus = SaveStatus.IDLE) }
    }
    fun dismiss() {
        if (state.value.saveStatus == SaveStatus.SAVING) return
        saved["settings_edit"] = null
        mutable.update { it.copy(edit = null, saveStatus = SaveStatus.IDLE) }
    }
    fun requestReset() { if (state.value.saveStatus != SaveStatus.SAVING) resetConfirmation(true) }
    fun cancelReset() { if (state.value.saveStatus != SaveStatus.SAVING) resetConfirmation(false) }
    private fun resetConfirmation(value: Boolean) {
        saved["settings_reset"] = value
        mutable.update { it.copy(confirmReset = value, saveStatus = SaveStatus.IDLE) }
    }

    fun apply() {
        val draft = state.value.edit ?: return
        save(false) { p -> when (draft.section) {
            SettingsSection.LANGUAGE -> p.copy(language = AppLanguage.valueOf(draft.choice))
            SettingsSection.THEME -> p.copy(theme = AppTheme.valueOf(draft.choice))
            SettingsSection.ACCENT -> p.copy(accent = Accent.valueOf(draft.choice), dynamicColors = draft.dynamic)
            SettingsSection.ACCESSIBILITY -> p.copy(textScale = AppTextScale.valueOf(draft.choice), higherContrast = draft.contrast, reducedMotion = draft.motion)
            SettingsSection.PDF -> p.copy(pdfPhotoQuality = PdfPhotoQuality.valueOf(draft.choice))
            SettingsSection.INSPECTOR -> p.copy(defaultInspector = draft.choice.ifBlank { null })
        } }
    }
    fun reset() { if (state.value.confirmReset) save(true) { it } }
    private fun save(reset: Boolean, transform: (Preferences) -> Preferences) {
        if (state.value.saveStatus == SaveStatus.SAVING) return
        val changeLocale = reset || state.value.edit?.section == SettingsSection.LANGUAGE
        mutable.update { it.copy(saveStatus = SaveStatus.SAVING) }
        viewModelScope.launch {
            when (val result = if (reset) repository.reset() else repository.update(transform)) {
                is DomainResult.Failure -> mutable.update { it.copy(saveStatus = SaveStatus.ERROR) }
                is DomainResult.Success -> {
                    saved["settings_edit"] = null
                    saved["settings_reset"] = false
                    mutable.update { it.copy(preferences = result.value, edit = null, confirmReset = false,
                        saveStatus = SaveStatus.SAVED, localeChange = if (changeLocale) result.value.language else null) }
                }
            }
        }
    }
    // AppCompat/platform locale storage also accepts changes made in Android app settings.
    fun synchronizeLocale(language: AppLanguage) {
        if (state.value.preferences?.language == language || state.value.saveStatus == SaveStatus.SAVING) return
        viewModelScope.launch {
            when (val result = repository.update { it.copy(language = language) }) {
                is DomainResult.Success -> mutable.update { it.copy(preferences = result.value) }
                is DomainResult.Failure -> mutable.update { it.copy(loadError = true) }
            }
        }
    }
    fun localeApplied() { mutable.update { it.copy(localeChange = null) } }
}
