package com.walkory.app.ui_kit

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.window.Dialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppTextScaleTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectedScaleEnlargesNewDialogsJustLikeScreenText() {
        var scale by mutableFloatStateOf(1f)
        var showDialog by mutableStateOf(false)
        compose.setContent {
            WalkoryTheme(textScale = scale) {
                Text("Readable text", Modifier.testTag("screen_text"), style = MaterialTheme.typography.bodyLarge)
                if (showDialog) Dialog(onDismissRequest = { showDialog = false }) {
                    Text("Readable text", Modifier.testTag("dialog_text"), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        val screenBefore = layout("screen_text")
        compose.runOnIdle { showDialog = true }
        val dialogBefore = layout("dialog_text")
        // Preference editors close on Apply; subsequent dialogs must use the confirmed scale.
        compose.runOnIdle { showDialog = false }
        compose.runOnIdle { scale = 1.3f }
        val screenAfter = layout("screen_text")
        compose.runOnIdle { showDialog = true }
        val dialogAfter = layout("dialog_text")
        assertTrue("Screen ${screenBefore.size} -> ${screenAfter.size}, font ${screenBefore.layoutInput.style.fontSize} -> ${screenAfter.layoutInput.style.fontSize}",
            screenAfter.size.height > screenBefore.size.height * 1.2f)
        assertTrue("Dialog ${dialogBefore.size} -> ${dialogAfter.size}, font ${dialogBefore.layoutInput.style.fontSize} -> ${dialogAfter.layoutInput.style.fontSize}",
            dialogAfter.size.height > dialogBefore.size.height * 1.2f)
        assertEquals(screenAfter.size, dialogAfter.size)
    }
    private fun layout(tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }
}
