package com.walkory.app.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import com.walkory.app.domain.PropertyCard
import com.walkory.app.domain.PropertyId
import com.walkory.app.ui_kit.WalkoryTheme
import org.junit.Rule
import org.junit.Test

class ShellNavigationTest {
    @get:Rule val compose = createComposeRule()

    private val state = ShellState(
        inspections = CollectionState.Content(emptyList()),
        objects = CollectionState.Content(listOf(
            PropertyCard(PropertyId("sample-home"), "User-authored name", "Пользовательский адрес"),
            PropertyCard(PropertyId("other-home"), "Other home", "Other address"),
        )),
        templates = CollectionState.Content(emptyList()),
    )

    @Test fun selectionSurvivesTabSwitchRestorationAndResizeWithoutDuplicatingBackStack() {
        var expanded by mutableStateOf(false)
        val restoration = StateRestorationTester(compose)
        restoration.setContent { WalkoryTheme { WalkoryShell(state, {}, expanded) } }
        compose.onNodeWithTag("nav_OBJECTS").performClick()
        compose.onNodeWithTag("record_sample-home").performClick()
        compose.onNodeWithTag("record_detail").assertIsDisplayed()
        compose.onNodeWithText("Пользовательский адрес").assertIsDisplayed()
        compose.onNodeWithTag("nav_TEMPLATES").performClick()
        compose.onNodeWithTag("nav_OBJECTS").performClick()
        compose.onNodeWithTag("record_detail").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("nav_OBJECTS").assertIsSelected()
        compose.onNodeWithTag("record_detail").assertIsDisplayed()
        compose.runOnIdle { expanded = true }
        compose.onNodeWithTag("two_panes").assertIsDisplayed()
        compose.onNodeWithTag("record_sample-home").assertIsSelected()
        compose.onNodeWithTag("record_other-home").performClick()
        compose.runOnIdle { expanded = false }
        compose.onNodeWithText("Other address").assertIsDisplayed()
        Espresso.pressBack()
        compose.onNodeWithTag("record_list").assertIsDisplayed()
        compose.onNodeWithTag("record_detail").assertDoesNotExist()
        Espresso.pressBack()
        compose.onNodeWithTag("nav_INSPECTIONS").assertIsSelected()
    }

    @Test fun nestedInformationReturnsToCallingContextAndRetainsInactiveTabStack() {
        compose.setContent { WalkoryTheme { WalkoryShell(state, {}, false) } }
        compose.onNodeWithTag("nav_SETTINGS").performClick()
        // Use localized labels from the same Android resource boundary as the actual screen.
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithText(context.getString(com.walkory.app.R.string.settings_about)).performClick()
        compose.onNodeWithTag("info_ABOUT").assertIsDisplayed()
        compose.onNodeWithText(context.getString(com.walkory.app.R.string.common_help)).performClick()
        compose.onNodeWithTag("info_HELP").assertIsDisplayed()
        compose.onNodeWithTag("nav_INSPECTIONS").performClick()
        compose.onNodeWithTag("nav_SETTINGS").performClick()
        compose.onNodeWithTag("info_HELP").assertIsDisplayed()
        Espresso.pressBack()
        compose.onNodeWithTag("info_ABOUT").assertIsDisplayed()
        compose.onNodeWithTag("navigate_back").performClick()
        compose.onNodeWithTag("info_ABOUT").assertDoesNotExist()
        compose.onNodeWithTag("nav_SETTINGS").assertIsSelected()
    }
}
