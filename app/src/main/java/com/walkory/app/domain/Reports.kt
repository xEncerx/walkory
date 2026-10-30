package com.walkory.app.domain

import java.util.Collections

/** Resolve the latest durable aggregate at capture time, not a stale UI copy. */
data class ReportRequest(val inspectionId: InspectionId, val localeTag: String, val photoQuality: PdfPhotoQuality)

data class GeneratedReport(val fileId: LocalFileId, val pageCount: Int)

sealed interface ReportProgress {
    data object Staging : ReportProgress
    data class Rendering(val completedChecks: Int, val totalChecks: Int) : ReportProgress
    data object Finalizing : ReportProgress
}

/** All lists are defensively frozen; media identities resolve only within this snapshot's staging. */
class ReportSnapshot(inspection: Inspection, val stagingId: LocalFileId) {
    val inspection: Inspection = inspection.copy(rooms = frozenMap(inspection.rooms) { room ->
        room.copy(checks = frozenMap(room.checks) { check ->
            check.copy(conditionPhotos = frozen(check.conditionPhotos), issues = frozenMap(check.issues) { issue ->
                issue.copy(photos = frozen(issue.photos))
            })
        })
    })
}

private fun <T> frozen(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))
private inline fun <T, R> frozenMap(values: List<T>, transform: (T) -> R): List<R> =
    Collections.unmodifiableList(values.map(transform))

interface ReportSnapshots {
    /**
     * Stage through the shared attachment gate, release the gate before rendering, and always
     * remove this job's copies before returning, including cancellation at dispatcher boundaries.
     */
    suspend fun <T> withSnapshot(id: InspectionId, render: suspend (ReportSnapshot) -> T): DomainResult<T>
}

interface ReportEngine {
    /** Off-main, cancellable full report. Reuses identical inputs; never mutates or completes the inspection. */
    suspend fun generate(
        request: ReportRequest,
        onProgress: suspend (ReportProgress) -> Unit = {},
    ): DomainResult<GeneratedReport>
}
