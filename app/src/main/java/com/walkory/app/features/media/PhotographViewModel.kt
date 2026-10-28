package com.walkory.app.features.media

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.walkory.app.domain.*
import com.walkory.app.platform.images.PhotographMedia
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class PhotographState(val busy: Boolean = false, val error: DomainError? = null)

/** Application UI-host lifetime: leaving a record does not cancel an accepted import. */
internal class PhotographViewModel(
    val media: PhotographMedia,
    private val inspections: InspectionRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PhotographState())
    val state = mutableState.asStateFlow()
    val sourceMenu = saved.getStateFlow("sourceMenu", false)
    val preparingCamera = saved.getStateFlow("preparingCamera", false)
    val awaitingResult = saved.getStateFlow("awaitingResult", false)
    val cameraUnavailable = saved.getStateFlow("cameraUnavailable", false)

    fun requestSource(id: InspectionId, owner: PhotographOwner, description: String?) {
        if (state.value.busy || awaitingResult.value || preparingCamera.value) return
        saved["inspection"] = id.value
        saved["check"] = when (owner) {
            is PhotographOwner.Condition -> owner.checkId.value
            is PhotographOwner.IssueEvidence -> owner.checkId.value
        }
        saved["issue"] = (owner as? PhotographOwner.IssueEvidence)?.issueId?.value
        saved["description"] = description
        saved["sourceMenu"] = true
    }

    fun dismissSource() { saved["sourceMenu"] = false }
    fun dismissCameraError() { saved["cameraUnavailable"] = false }
    fun chooseCamera() { dismissSource(); saved["preparingCamera"] = true }
    fun beginPicker() { dismissSource(); dismissCameraError(); saved["awaitingResult"] = true }
    fun capturePrepared(uri: Uri) {
        saved["captureUri"] = uri.toString()
        saved["preparingCamera"] = false
        saved["awaitingResult"] = true
    }
    fun launchFailed(camera: Boolean, error: DomainError = DomainError.StorageUnavailable) {
        saved["preparingCamera"] = false
        saved["awaitingResult"] = false
        if (camera) saved["cameraUnavailable"] = true else fail(error)
    }

    fun picked(sources: List<Uri>) {
        saved["awaitingResult"] = false
        importSelected(sources, camera = false)
    }
    fun captured(success: Boolean) {
        saved["awaitingResult"] = false
        val uri = saved.get<String>("captureUri")
        saved["captureUri"] = null
        if (success && uri != null) importSelected(listOf(uri.toUri()), camera = true)
        else viewModelScope.launch { if (!media.discardCapture()) fail(DomainError.AttachmentCleanupPending) }
    }
    private fun importSelected(sources: List<Uri>, camera: Boolean) {
        if (sources.isEmpty()) return
        val inspection = saved.get<String>("inspection") ?: return fail()
        val check = saved.get<String>("check") ?: return fail()
        val issue = saved.get<String>("issue")
        val owner = if (issue == null) PhotographOwner.Condition(CheckId(check))
            else PhotographOwner.IssueEvidence(CheckId(check), IssueId(issue))
        import(InspectionId(inspection), owner, sources, saved["description"], camera)
    }

    init { viewModelScope.launch {
        val result = inspections.retryAttachmentCleanup()
        if (result is DomainResult.Failure) fail(result.error)
    } }

    fun dismissError() { mutableState.update { it.copy(error = null) } }
    fun fail(error: DomainError = DomainError.StorageUnavailable) { mutableState.update { it.copy(error = error) } }

    private fun import(id: InspectionId, owner: PhotographOwner, sources: List<Uri>, description: String?, camera: Boolean) {
        if (state.value.busy || sources.isEmpty()) return
        mutableState.value = PhotographState(busy = true)
        viewModelScope.launch {
            try {
                val result = media.import(id, owner, sources, description)
                mutableState.update { it.copy(error = (result as? DomainResult.Failure)?.error) }
            } finally {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    if (camera && !media.discardCapture()) fail(DomainError.AttachmentCleanupPending)
                }
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    fun retryCleanup() {
        viewModelScope.launch {
            when (val result = inspections.retryAttachmentCleanup()) {
                is DomainResult.Success -> if (awaitingResult.value || preparingCamera.value || media.discardCapture()) dismissError() else fail(DomainError.AttachmentCleanupPending)
                is DomainResult.Failure -> fail(result.error)
            }
        }
    }
}
