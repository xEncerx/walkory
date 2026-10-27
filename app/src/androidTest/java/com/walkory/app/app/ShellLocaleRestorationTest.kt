package com.walkory.app.app

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ApplicationProvider
import com.walkory.app.domain.DomainResult
import com.walkory.app.domain.PropertyCard
import com.walkory.app.domain.PropertyId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ShellLocaleRestorationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun localeChangeAndActivityRecreationKeepSelectionAndAuthoredContent() {
        val application = ApplicationProvider.getApplicationContext<WalkoryApplication>()
        val properties = application.container.properties
        val property = PropertyCard(PropertyId.new(), "Собственное название", "User-authored address")
        val previousLocales = AppCompatDelegate.getApplicationLocales()
        assertTrue(runBlocking { properties.save(property) } is DomainResult.Success)
        try {
            compose.onNodeWithTag("nav_OBJECTS").performClick()
            compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("record_${property.id.value}")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("record_${property.id.value}").performClick()
            compose.activityRule.scenario.onActivity { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ru")) }
            compose.waitForIdle()
            compose.activityRule.scenario.onActivity { assertEquals("ru", it.resources.configuration.locales[0].language) }
            compose.onNodeWithTag("nav_OBJECTS").assertIsSelected()
            compose.onNodeWithTag("record_detail").assertIsDisplayed()
            compose.onNodeWithText(property.name).assertIsDisplayed()
            compose.onNodeWithText(property.address).assertIsDisplayed()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("nav_OBJECTS").assertIsSelected()
            compose.onNodeWithTag("record_detail").assertIsDisplayed()
            compose.onNodeWithText(property.address).assertIsDisplayed()
        } finally {
            assertTrue(runBlocking { properties.deleteAndDetach(property.id) } is DomainResult.Success)
            compose.activityRule.scenario.onActivity { AppCompatDelegate.setApplicationLocales(previousLocales) }
        }
    }
}
