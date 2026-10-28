package com.walkory.app.features.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ApplicationProvider
import com.walkory.app.app.MainActivity
import com.walkory.app.app.WalkoryApplication
import com.walkory.app.data.local.accepted
import com.walkory.app.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SettingsInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val application get() = ApplicationProvider.getApplicationContext<WalkoryApplication>()
    private val repository get() = application.container.settings
    private fun current() = runBlocking { repository.observe().first().accepted() }
    private fun click(tag: String) = compose.onNodeWithTag(tag).performClick()
    private fun scrollClick(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()
    private fun open(section: SettingsSection) { scrollClick("settings_${section.name}") }
    private fun choose(choice: String) { scrollClick("settings_choice_$choice") }
    private fun apply() { scrollClick("settings_apply"); compose.waitForIdle() }

    @Test fun choicesApplyOnlyOnConfirmationAndLocaleThemeResetPreserveDraftAndSelectedRecord() {
        val previous = current()
        val previousLocales = AppCompatDelegate.getApplicationLocales()
        val card = PropertyCard(PropertyId.new(), "Авторское название", "Authored address")
        runBlocking { application.container.properties.save(card).accepted() }
        try {
            compose.onNodeWithTag("nav_OBJECTS").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("property_${card.id.value}").fetchSemanticsNodes().isNotEmpty() }
            click("property_${card.id.value}")
            compose.onNodeWithTag("nav_SETTINGS").performClick()
            open(SettingsSection.INSPECTOR)
            compose.onNodeWithTag("settings_inspector_input").performTextReplacement("Новый проверяющий")
            apply()
            compose.waitUntil(10_000) { current().defaultInspector == "Новый проверяющий" }
            compose.onNodeWithTag("nav_INSPECTIONS").performClick()
            click("inspection_new")
            click("inspection_role_TENANT")
            click("inspection_role_next")
            compose.onNodeWithTag("inspection_inspector").assertTextContains("Новый проверяющий")
            compose.onNodeWithTag("inspection_name").performTextReplacement("Собственный черновик")
            compose.onNodeWithTag("inspection_address").performTextReplacement("Draft address")
            compose.onNodeWithTag("inspection_inspector").performTextReplacement("Authored inspector")
            compose.onNodeWithTag("nav_SETTINGS").performClick()
            open(SettingsSection.THEME)
            val nextTheme = if (previous.theme == AppTheme.DARK) AppTheme.LIGHT else AppTheme.DARK
            choose(nextTheme.name)
            assertEquals(previous.theme, current().theme)
            scrollClick("settings_cancel")
            assertEquals(previous.theme, current().theme)
            open(SettingsSection.THEME); choose(nextTheme.name); apply()
            compose.waitUntil(10_000) { current().theme == nextTheme }
            open(SettingsSection.LANGUAGE); choose(AppLanguage.RUSSIAN.name); apply()
            compose.waitUntil(10_000) { compose.activity.resources.configuration.locales[0].language == "ru" }
            compose.onNodeWithTag("nav_SETTINGS").assertIsSelected()
            compose.onNodeWithTag("settings_THEME").assertTextContains("Тема")
            compose.onNodeWithTag("nav_INSPECTIONS").performClick()
            compose.onNodeWithTag("inspection_name").assertTextContains("Собственный черновик")
            compose.onNodeWithTag("inspection_inspector").assertTextContains("Authored inspector")
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("inspection_address").assertTextContains("Draft address")
            compose.onNodeWithTag("nav_OBJECTS").performClick()
            compose.onNodeWithTag("property_detail").assertIsDisplayed()
            compose.onNodeWithText(card.name).assertIsDisplayed()
            compose.onNodeWithText(card.address).assertIsDisplayed()
            compose.onNodeWithTag("nav_SETTINGS").performClick()
            open(SettingsSection.LANGUAGE); choose(AppLanguage.ENGLISH.name); apply()
            compose.waitUntil(10_000) { compose.activity.resources.configuration.locales[0].language == "en" }
            compose.onNodeWithTag("settings_THEME").assertTextContains("Theme")
            scrollClick("settings_reset")
            compose.onNodeWithText(compose.activity.getString(com.walkory.app.R.string.common_cancel)).performClick()
            assertEquals(nextTheme, current().theme)
            scrollClick("settings_reset"); click("settings_reset_confirm")
            compose.waitUntil(10_000) { current() == Preferences() }
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("nav_OBJECTS").performClick()
            compose.onNodeWithText(card.address).assertIsDisplayed()
            compose.onNodeWithTag("nav_INSPECTIONS").performClick()
            compose.onNodeWithTag("inspection_name").assertTextContains("Собственный черновик")
            compose.onNodeWithTag("inspection_inspector").assertTextContains("Authored inspector")
        } finally {
            runBlocking {
                application.container.properties.deleteAndDetach(card.id).accepted()
                repository.update { previous }.accepted()
            }
            compose.activityRule.scenario.onActivity { AppCompatDelegate.setApplicationLocales(previousLocales) }
        }
    }
}
