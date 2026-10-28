package com.walkory.app.features.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.walkory.app.domain.*
import com.walkory.app.ui_kit.SaveStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { models.clear(); Dispatchers.resetMain() }
    private fun model(repository: SettingsFake, handle: SavedStateHandle = SavedStateHandle()) =
        SettingsViewModel(repository, handle).also { models.put("settings-${handle.hashCode()}", it) }

    @Test fun stagedRestoredChoiceDoesNotApplyUntilConfirmedOrOverwriteOtherPreferences() = runTest(dispatcher) {
        val repository = SettingsFake()
        val saved = SavedStateHandle()
        val model = model(repository, saved)
        runCurrent()
        model.open(SettingsSection.ACCENT)
        model.edit(model.state.value.edit!!.copy(choice = Accent.CORAL.name, dynamic = true))
        assertEquals(Preferences(), repository.value)
        repository.update { it.copy(defaultInspector = "Authored inspector", textScale = AppTextScale.LARGE) }
        val restored = model(repository, SavedStateHandle(mapOf("settings_edit" to saved.get<String>("settings_edit"))))
        runCurrent()
        assertEquals(Accent.CORAL.name, restored.state.value.edit!!.choice)
        restored.apply(); runCurrent()
        assertEquals(Preferences(accent = Accent.CORAL, dynamicColors = true, textScale = AppTextScale.LARGE,
            defaultInspector = "Authored inspector"), repository.value)
        restored.open(SettingsSection.THEME)
        restored.edit(restored.state.value.edit!!.copy(choice = AppTheme.DARK.name))
        restored.dismiss()
        assertEquals(AppTheme.SYSTEM, repository.value.theme)
    }

    @Test fun failedApplyAndResetRetainInputAndOnlySuccessfulLanguageChoiceRequestsRecreation() = runTest(dispatcher) {
        val repository = SettingsFake()
        val model = model(repository)
        runCurrent()
        model.open(SettingsSection.LANGUAGE)
        model.edit(model.state.value.edit!!.copy(choice = AppLanguage.RUSSIAN.name))
        repository.fail = true
        model.apply(); runCurrent()
        assertEquals(SaveStatus.ERROR, model.state.value.saveStatus)
        assertEquals(AppLanguage.RUSSIAN.name, model.state.value.edit!!.choice)
        assertNull(model.state.value.localeChange)
        assertEquals(AppLanguage.SYSTEM, repository.value.language)
        repository.fail = false
        model.apply(); runCurrent()
        assertEquals(AppLanguage.RUSSIAN, model.state.value.localeChange)
        model.localeApplied()
        model.requestReset(); model.cancelReset(); model.reset(); runCurrent()
        assertEquals(AppLanguage.RUSSIAN, repository.value.language)
        model.requestReset()
        repository.fail = true
        model.reset(); runCurrent()
        assertTrue(model.state.value.confirmReset)
        assertEquals(SaveStatus.ERROR, model.state.value.saveStatus)
        assertEquals(AppLanguage.RUSSIAN, repository.value.language)
        repository.fail = false
        model.reset(); runCurrent()
        assertEquals(Preferences(), repository.value)
        assertFalse(model.state.value.confirmReset)
        assertEquals(AppLanguage.SYSTEM, model.state.value.localeChange)
    }
}

private class SettingsFake : SettingsRepository {
    var value = Preferences()
    var fail = false
    private val rows = MutableStateFlow<DomainResult<Preferences>>(DomainResult.Success(value))
    override fun observe() = rows
    override suspend fun update(transform: (Preferences) -> Preferences): DomainResult<Preferences> {
        if (fail) return DomainResult.Failure(DomainError.StorageUnavailable)
        value = transform(value)
        return DomainResult.Success(value).also { rows.value = it }
    }
    override suspend fun reset() = update { Preferences() }
}
