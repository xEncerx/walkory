package com.walkory.app.features.reports

import androidx.lifecycle.ViewModelStore
import com.walkory.app.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { store.clear(); Dispatchers.resetMain() }

    @Test fun duplicateTapCancellationRetryAndOwnerDestructionHaveHonestStates() = runTest(dispatcher) {
        val settings = ReportSettings()
        val engine = DelayedEngine()
        val model = ReportViewModel(engine, settings, TestDelivery(), androidx.lifecycle.SavedStateHandle()).also { store.put("report", it) }
        val id = InspectionId.new()
        model.generate(id, "ru"); model.generate(id, "en"); runCurrent()
        assertEquals(1, engine.requests.size)
        assertTrue(model.state.value[id]!!.running)
        assertEquals(ReportProgress.Rendering(1, 4), model.state.value[id]!!.progress)
        model.cancel(id); runCurrent()
        assertFalse(model.state.value[id]!!.running)
        assertTrue(model.state.value[id]!!.cancelled)
        assertNull(model.state.value[id]!!.report)
        assertNull(model.state.value[id]!!.error)
        settings.update { it.copy(pdfPhotoQuality = PdfPhotoQuality.HIGH) }
        model.generate(id, "en"); runCurrent()
        assertEquals(2, engine.requests.size)
        assertEquals(PdfPhotoQuality.HIGH, engine.requests.last().photoQuality)
        engine.finish.complete(DomainResult.Failure(DomainError.InvalidImage)); runCurrent()
        assertEquals(DomainError.InvalidImage, model.state.value[id]!!.error)
        assertFalse(model.state.value[id]!!.running)
        engine.finish = CompletableDeferred()
        model.generate(id, "en"); runCurrent()
        val report = GeneratedReport(LocalFileId.new(), 3)
        engine.finish.complete(DomainResult.Success(report)); runCurrent()
        assertEquals(report, model.state.value[id]!!.report)
        assertNull(model.state.value[id]!!.error)
        engine.finish = CompletableDeferred()
        model.generate(id, "ru"); runCurrent()
        store.clear(); runCurrent()
        assertTrue(model.state.value[id]!!.cancelled)
        assertFalse(model.state.value[id]!!.running)
        assertEquals(report, model.state.value[id]!!.report)
    }

    @Test fun unreadablePreferencesPreventGenerationAndRemainRetryable() = runTest(dispatcher) {
        val settings = ReportSettings()
        settings.rows.value = DomainResult.Failure(DomainError.StorageUnavailable)
        val engine = DelayedEngine()
        val model = ReportViewModel(engine, settings, TestDelivery(), androidx.lifecycle.SavedStateHandle()).also { store.put("report", it) }
        val id = InspectionId.new()
        model.generate(id, "en"); runCurrent()
        assertTrue(engine.requests.isEmpty())
        assertEquals(DomainError.StorageUnavailable, model.state.value[id]!!.error)
        assertFalse(model.state.value[id]!!.running)
        settings.rows.value = DomainResult.Success(Preferences())
        model.generate(id, "en"); runCurrent()
        assertTrue(model.state.value[id]!!.running)
        assertNull(model.state.value[id]!!.error)
    }
    @Test fun documentResultAfterRestorationSavesFrozenArtifactAndRetainsItsInspectionOwner() = runTest(dispatcher) {
        val first = InspectionId.new()
        val second = InspectionId.new()
        val oldReport = GeneratedReport(LocalFileId.new(), 2)
        val otherReport = GeneratedReport(LocalFileId.new(), 5)
        val handle = androidx.lifecycle.SavedStateHandle(mapOf("artifacts" to arrayListOf(
            first.value, oldReport.fileId.value, "2", second.value, otherReport.fileId.value, "5")))
        val delivery = TestDelivery()
        val model = ReportViewModel(DelayedEngine(), ReportSettings(), delivery, handle)
        model.chooseDestination(first, "Inspection.pdf")
        model.destinationLaunched()
        model.chooseDestination(second, "Other.pdf")
        assertEquals(first, model.saveRequest.value!!.inspection)
        val restored = ReportViewModel(DelayedEngine(), ReportSettings(), delivery, handle).also { store.put("restored", it) }
        assertTrue(restored.saveRequest.value!!.launched)
        restored.destinationResult("content://destination/new")
        runCurrent()
        assertEquals(oldReport.fileId, delivery.saved)
        assertEquals(DeliveryStatus.SAVING, restored.state.value[first]!!.delivery)
        assertEquals(DeliveryStatus.IDLE, restored.state.value[second]!!.delivery)
        restored.cancelSave(second); runCurrent()
        assertEquals(DeliveryStatus.SAVING, restored.state.value[first]!!.delivery)
        restored.cancelSave(first); runCurrent()
        assertEquals(DeliveryStatus.CANCELLED, restored.state.value[first]!!.delivery)
        assertEquals(oldReport, restored.state.value[first]!!.report)
        restored.chooseDestination(second, "Other.pdf")
        restored.destinationResult(null)
        assertEquals(DeliveryStatus.CANCELLED, restored.state.value[second]!!.delivery)
        assertEquals(otherReport, restored.state.value[second]!!.report)
    }

    @Test fun failedSavingRetriesWithoutRegenerationAndOnlyClosedCopyClaimsSuccess() = runTest(dispatcher) {
        val id = InspectionId.new()
        val report = GeneratedReport(LocalFileId.new(), 3)
        val delivery = TestDelivery()
        val handle = androidx.lifecycle.SavedStateHandle(mapOf("artifacts" to arrayListOf(id.value, report.fileId.value, "3")))
        val engine = DelayedEngine()
        val model = ReportViewModel(engine, ReportSettings(), delivery, handle).also { store.put("delivery", it) }
        model.chooseDestination(id, "Report.pdf"); model.destinationResult("content://provider/first"); runCurrent()
        assertEquals(DeliveryStatus.SAVING, model.state.value[id]!!.delivery)
        delivery.finish.completeExceptionally(java.io.IOException("Provider failed")); runCurrent()
        assertEquals(DeliveryStatus.FAILED, model.state.value[id]!!.delivery)
        assertEquals(report, model.state.value[id]!!.report)
        delivery.finish = CompletableDeferred()
        model.chooseDestination(id, "Report.pdf"); model.destinationResult("content://provider/second"); runCurrent()
        model.generate(id, "en"); runCurrent()
        assertTrue(engine.requests.isEmpty())
        delivery.finish.complete(Unit); runCurrent()
        assertEquals(DeliveryStatus.SAVED, model.state.value[id]!!.delivery)
        assertEquals(report, model.state.value[id]!!.report)
    }
}

private class DelayedEngine : ReportEngine {
    val requests = mutableListOf<ReportRequest>()
    var finish = CompletableDeferred<DomainResult<GeneratedReport>>()
    override suspend fun generate(request: ReportRequest, onProgress: suspend (ReportProgress) -> Unit): DomainResult<GeneratedReport> {
        requests += request
        onProgress(ReportProgress.Rendering(1, 4))
        return finish.await()
    }
}

private class ReportSettings : SettingsRepository {
    val rows = MutableStateFlow<DomainResult<Preferences>>(DomainResult.Success(Preferences()))
    override fun observe() = rows
    override suspend fun update(transform: (Preferences) -> Preferences): DomainResult<Preferences> {
        val value = (rows.value as DomainResult.Success).value
        return DomainResult.Success(transform(value)).also { rows.value = it }
    }
    override suspend fun reset() = update { Preferences() }
}

private class TestDelivery : com.walkory.app.platform.pdf.ReportDelivery {
    var finish = CompletableDeferred<Unit>()
    var saved: LocalFileId? = null
    override fun file(id: LocalFileId) = java.io.File(id.value)
    override fun share(id: LocalFileId): android.content.Intent = error("Not used in JVM tests")
    override suspend fun save(id: LocalFileId, destination: String, progress: (Long, Long) -> Unit) {
        saved = id
        progress(8, 16)
        finish.await()
    }
}
