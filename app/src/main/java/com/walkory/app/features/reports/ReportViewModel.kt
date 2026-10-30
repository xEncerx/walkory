package com.walkory.app.features.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import com.walkory.app.platform.pdf.ReportDelivery
import com.walkory.app.platform.pdf.DeliveryError
import com.walkory.app.platform.pdf.deliveryError
import com.walkory.app.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

internal data class ReportState(
    val running: Boolean = false,
    val progress: ReportProgress = ReportProgress.Staging,
    val report: GeneratedReport? = null,
    val error: DomainError? = null,
    val cancelled: Boolean = false,
    val delivery: DeliveryStatus = DeliveryStatus.IDLE,
    val deliveryError: DeliveryError? = null,
    val copied: Long = 0,
    val total: Long = 0,
)

internal enum class DeliveryStatus { IDLE, CHOOSING, SAVING, SAVED, CANCELLED, FAILED }
internal data class SaveRequest(val inspection: InspectionId, val report: GeneratedReport, val name: String, val launched: Boolean)

internal class ReportViewModel(
    private val engine: ReportEngine,
    private val settings: SettingsRepository,
    val delivery: ReportDelivery,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow<Map<InspectionId, ReportState>>(
        savedState.get<ArrayList<String>>("artifacts").orEmpty().chunked(3).associate { row ->
            InspectionId(row[0]) to ReportState(report = GeneratedReport(LocalFileId(row[1]), row[2].toInt()))
        })
    val state = mutableState.asStateFlow()
    private val jobs = mutableMapOf<InspectionId, Job>()
    private var saveJob: Job? = null
    private var savingId: InspectionId? = null
    private val mutableRequest = MutableStateFlow(savedState.get<ArrayList<String>>("destination")?.let {
        SaveRequest(InspectionId(it[0]), GeneratedReport(LocalFileId(it[1]), it[2].toInt()), it[3], it[4].toBoolean())
    })
    val saveRequest = mutableRequest.asStateFlow()

    init {
        mutableRequest.value?.let { request -> update(request.inspection) { it.copy(delivery = DeliveryStatus.CHOOSING) } }
    }

    private fun request(value: SaveRequest?) {
        savedState["destination"] = value?.let { arrayListOf(it.inspection.value, it.report.fileId.value, it.report.pageCount.toString(), it.name, it.launched.toString()) }
        mutableRequest.value = value
    }

    fun chooseDestination(id: InspectionId, name: String) {
        if (saveRequest.value != null || saveJob?.isActive == true || state.value[id]?.running == true) return
        val report = state.value[id]?.report ?: return
        request(SaveRequest(id, report, name, false))
        update(id) { it.copy(delivery = DeliveryStatus.CHOOSING, deliveryError = null) }
    }

    fun destinationLaunched() { request(saveRequest.value?.copy(launched = true)) }

    fun destinationResult(destination: String?) {
        val pending = saveRequest.value ?: return
        request(null)
        if (destination == null) {
            update(pending.inspection) { it.copy(delivery = DeliveryStatus.CANCELLED) }
            return
        }
        savingId = pending.inspection
        update(pending.inspection) { it.copy(delivery = DeliveryStatus.SAVING, copied = 0, total = 0, deliveryError = null) }
        saveJob = viewModelScope.launch {
            try {
                delivery.save(pending.report.fileId, destination) { copied, total ->
                    update(pending.inspection) { it.copy(copied = copied, total = total) }
                }
                update(pending.inspection) { it.copy(delivery = DeliveryStatus.SAVED) }
            } catch (cancelled: CancellationException) {
                update(pending.inspection) { it.copy(delivery = DeliveryStatus.CANCELLED) }
                throw cancelled
            } catch (failure: java.io.IOException) {
                deliveryFailed(pending.inspection, deliveryError(failure))
            } catch (failure: RuntimeException) {
                deliveryFailed(pending.inspection, deliveryError(failure))
            } finally { savingId = null }
        }
    }

    fun destinationFailed() {
        val pending = saveRequest.value ?: return
        request(null)
        deliveryFailed(pending.inspection, DeliveryError.STORAGE)
    }

    fun deliveryFailed(id: InspectionId, error: DeliveryError) {
        update(id) { it.copy(delivery = DeliveryStatus.FAILED, deliveryError = error) }
    }

    fun dismissDeliveryError(id: InspectionId) {
        update(id) { it.copy(delivery = DeliveryStatus.IDLE, deliveryError = null) }
    }

    fun cancelSave(id: InspectionId) { if (savingId == id) saveJob?.cancel() }

    private fun update(id: InspectionId, change: (ReportState) -> ReportState) {
        mutableState.update { it + (id to change(it[id] ?: ReportState())) }
    }

    fun generate(id: InspectionId, localeTag: String) {
        val current = state.value[id]
        if (current?.running == true || current?.delivery == DeliveryStatus.CHOOSING || current?.delivery == DeliveryStatus.SAVING) return
        update(id) { it.copy(running = true, progress = ReportProgress.Staging, error = null, cancelled = false) }
        jobs[id] = viewModelScope.launch {
            try {
                val preferences = settings.observe().first()
                val result = when (preferences) {
                    is DomainResult.Failure -> preferences
                    is DomainResult.Success -> engine.generate(ReportRequest(id, localeTag, preferences.value.pdfPhotoQuality)) { progress ->
                        update(id) { it.copy(progress = progress) }
                    }
                }
                when (result) {
                    is DomainResult.Success -> {
                        update(id) { it.copy(running = false, report = result.value, delivery = DeliveryStatus.IDLE, deliveryError = null) }
                        savedState["artifacts"] = ArrayList(state.value.flatMap { (owner, value) ->
                            value.report?.let { listOf(owner.value, it.fileId.value, it.pageCount.toString()) }.orEmpty()
                        })
                    }
                    is DomainResult.Failure -> update(id) { it.copy(running = false, error = result.error) }
                }
            } catch (cancelled: CancellationException) {
                update(id) { it.copy(running = false, cancelled = true) }
                throw cancelled
            }
        }
    }

    fun cancel(id: InspectionId) { jobs[id]?.cancel(); cancelSave(id) }
}
