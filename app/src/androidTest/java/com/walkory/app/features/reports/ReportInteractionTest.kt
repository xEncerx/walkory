package com.walkory.app.features.reports

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModelStore
import com.walkory.app.data.local.*
import com.walkory.app.domain.*
import com.walkory.app.ui_kit.WalkoryTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReportInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val fixture = PersistenceFixture()
    private val store = ViewModelStore()
    @After fun close() { compose.runOnIdle { store.clear() }; fixture.close() }

    @Test fun generationRequiresSavedInputAndIsAvailableBeforeAndAfterCompletion() {
        val original = InspectionOperations().create("Rental review", "Sample Street 3", InspectionRole.TENANT).accepted()
        runBlocking { fixture.inspections.create(original).accepted() }
        val current = mutableStateOf(original)
        val saved = mutableStateOf(false)
        val model = ReportViewModel(fixture.reports, fixture.reportSettings).also { store.put("reports", it) }
        compose.setContent { WalkoryTheme { ReportScreen(current.value, saved.value, model) } }
        compose.onNodeWithTag("report_generate").assertIsNotEnabled()
        compose.runOnIdle { saved.value = true }
        compose.onNodeWithTag("report_generate").performScrollTo().performClick()
        compose.waitUntil(15_000) { model.state.value[original.id]?.report != null }
        compose.onNodeWithTag("report_ready").assertIsDisplayed()
        val first = model.state.value[original.id]!!.report!!
        runBlocking { assertEquals(original, fixture.inspections.read(original.id)) }
        val completed = runBlocking { fixture.inspections.update(original.id) { InspectionOperations().complete(it, true) }.accepted() }
        compose.runOnIdle { current.value = completed }
        compose.onNodeWithTag("report_generate").performScrollTo().performClick()
        compose.waitUntil(15_000) { model.state.value[original.id]?.report?.fileId != first.fileId }
        val second = model.state.value[original.id]!!.report!!
        assertNotEquals(first.fileId, second.fileId)
        assertTrue(fixture.reportFiles.report(first.fileId).exists())
        assertTrue(fixture.reportFiles.report(second.fileId).exists())
        runBlocking { assertEquals(completed, fixture.inspections.read(original.id)) }
    }
}
