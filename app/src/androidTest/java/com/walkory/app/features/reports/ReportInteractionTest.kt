package com.walkory.app.features.reports

import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
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
        val model = ReportViewModel(fixture.reports, fixture.reportSettings,
            com.walkory.app.platform.pdf.AndroidReportDelivery(fixture.context, fixture.reportFiles), androidx.lifecycle.SavedStateHandle()).also { store.put("reports", it) }
        compose.setContent { WalkoryTheme { ReportScreen(current.value, saved.value, model) } }
        compose.onNodeWithText(fixture.context.getString(com.walkory.app.R.string.reports_wait_saved)).assertIsDisplayed()
        compose.runOnIdle { saved.value = true }
        compose.waitUntil(15_000) { model.state.value[original.id]?.report != null }
        compose.onNodeWithTag("report_preview").assertIsDisplayed()
        val first = model.state.value[original.id]!!.report!!
        runBlocking { assertEquals(original, fixture.inspections.read(original.id)) }
        val completed = runBlocking { fixture.inspections.update(original.id) { InspectionOperations().complete(it, true) }.accepted() }
        compose.runOnIdle { current.value = completed }
        compose.waitUntil(15_000) { model.state.value[original.id]?.report?.fileId != first.fileId }
        val second = model.state.value[original.id]!!.report!!
        assertNotEquals(first.fileId, second.fileId)
        assertTrue(fixture.reportFiles.report(first.fileId).exists())
        assertTrue(fixture.reportFiles.report(second.fileId).exists())
        runBlocking { assertEquals(completed, fixture.inspections.read(original.id)) }
    }
    @Test fun pagingRestoresSelectionAndPreviewProviderFailuresRemainRecoverable() {
        val original = InspectionOperations().create("Rental review", "Sample Street 5", InspectionRole.TENANT).accepted().copy(
            rooms = listOf(InspectionRoom(RoomId.new(), RoomType.KITCHEN, "Kitchen", List(12) {
                InspectionCheck(CheckId.new(), "Check ${it + 1}", comment = "Observed condition. ".repeat(80))
            })))
        val report = runBlocking {
            fixture.inspections.create(original).accepted()
            fixture.reports.generate(ReportRequest(original.id, "en", PdfPhotoQuality.BALANCED)).accepted()
        }
        assertTrue(report.pageCount > 2)
        val handle = androidx.lifecycle.SavedStateHandle(mapOf("artifacts" to arrayListOf(original.id.value, report.fileId.value, report.pageCount.toString())))
        val model = ReportViewModel(fixture.reports, fixture.reportSettings,
            com.walkory.app.platform.pdf.AndroidReportDelivery(fixture.context, fixture.reportFiles), handle).also { store.put("reports", it) }
        val restoration = StateRestorationTester(compose)
        val shortWindow = mutableStateOf(false)
        restoration.setContent {
            WalkoryTheme {
                Box(Modifier.requiredSize(400.dp, if (shortWindow.value) 300.dp else 700.dp)) {
                    ReportScreen(original, true, model)
                }
            }
        }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("report_preview").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("report_pages").performScrollToIndex(1)
        compose.onNodeWithTag("report_page_1").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("report_preview").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("report_page_1").assertIsDisplayed()
        compose.runOnIdle { shortWindow.value = true }
        compose.onNodeWithTag("report_page_1").assertIsDisplayed()
        val file = fixture.reportFiles.report(report.fileId)
        val retained = java.io.File(fixture.directory, "retained.pdf")
        assertTrue(file.renameTo(retained))
        compose.onNodeWithTag("report_pages").performScrollToIndex(report.pageCount - 1)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("report_preview_retry_${report.pageCount - 1}").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(retained.renameTo(file))
        compose.onNodeWithTag("report_preview_retry_${report.pageCount - 1}").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("report_preview_retry_${report.pageCount - 1}").fetchSemanticsNodes().isEmpty() }
        val bytes = file.readBytes()
        compose.runOnIdle {
            model.chooseDestination(original.id, "Report.pdf")
            model.destinationResult("content://unavailable.documents/new.pdf")
        }
        compose.waitUntil(10_000) { model.state.value[original.id]?.delivery == DeliveryStatus.FAILED }
        compose.onNodeWithTag("report_save").performScrollTo().assertIsEnabled()
        assertArrayEquals(bytes, file.readBytes())
        compose.runOnIdle { model.chooseDestination(original.id, "Report.pdf"); model.destinationResult(null) }
        assertEquals(DeliveryStatus.CANCELLED, model.state.value[original.id]!!.delivery)
        runBlocking { assertEquals(original, fixture.inspections.read(original.id)) }
    }
}
