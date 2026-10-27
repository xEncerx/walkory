package com.walkory.app.ui_kit

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.walkory.app.R
import org.junit.Rule
import org.junit.Test

class FoundationInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun whitespaceValidationAndDraftSurviveRestorationAndModalBack() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            var draft by rememberSaveable { mutableStateOf("  ") }
            var validate by rememberSaveable { mutableStateOf(false) }
            var confirm by rememberSaveable { mutableStateOf(false) }
            WalkoryTheme {
                Column {
                    WalkoryRequiredField(draft, { draft = it }, "Title", validate, Modifier.testTag("draft"))
                    Button({ validate = true }) { Text("Validate") }
                    Button({ confirm = true }) { Text("Discard") }
                    if (confirm) WalkoryConfirmation("Discard draft?", "Only this draft will be removed. Saved records are retained.", "Discard draft",
                        onConfirm = { draft = ""; confirm = false }, onDismiss = { confirm = false })
                }
            }
        }
        compose.onNodeWithText("Validate").performClick()
        val error = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.common_required_field)
        compose.onNodeWithText(error).assertIsDisplayed()
        compose.onNodeWithTag("draft").performTextReplacement("User-authored draft")
        compose.onNodeWithText(error).assertDoesNotExist()
        Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Discard").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Discard draft?").assertIsDisplayed()
        Espresso.closeSoftKeyboard()
        Espresso.pressBack()
        compose.onNodeWithText("Discard draft?").assertDoesNotExist()
        compose.onNodeWithTag("draft").assertTextContains("User-authored draft")
        compose.onNodeWithText("Discard").performClick()
        compose.onNodeWithText("Discard draft").performClick()
        compose.onNodeWithTag("draft").assertTextContains("")
        compose.onNodeWithText(error).assertIsDisplayed()
    }
}
