package com.walkory.app.features.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
)

internal class ReportViewModel(private val engine: ReportEngine, private val settings: SettingsRepository) : ViewModel() {
    private val mutableState = MutableStateFlow<Map<InspectionId, ReportState>>(emptyMap())
    val state = mutableState.asStateFlow()
    private val jobs = mutableMapOf<InspectionId, Job>()

    private fun update(id: InspectionId, change: (ReportState) -> ReportState) {
        mutableState.update { it + (id to change(it[id] ?: ReportState())) }
    }

    fun generate(id: InspectionId, localeTag: String) {
        if (state.value[id]?.running == true) return
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
                    is DomainResult.Success -> update(id) { it.copy(running = false, report = result.value) }
                    is DomainResult.Failure -> update(id) { it.copy(running = false, error = result.error) }
                }
            } catch (cancelled: CancellationException) {
                update(id) { it.copy(running = false, cancelled = true) }
                throw cancelled
            }
        }
    }

    fun cancel(id: InspectionId) { jobs[id]?.cancel() }
}
