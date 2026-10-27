package com.walkory.app.domain

import kotlinx.coroutines.flow.Flow

/**
 * Flows expose confirmed durable snapshots or explicit storage errors, never optimistic saved state.
 * Implementations translate storage failures, propagate coroutine cancellation, and preserve old data
 * when a write fails. Collection order inside each aggregate is retained.
 */
interface InspectionRepository {
    fun observeAll(): Flow<DomainResult<List<Inspection>>>
    fun observe(id: InspectionId): Flow<DomainResult<Inspection?>>

    /** Reject duplicate descendant identities/files across inspections; do not replace existing data. */
    suspend fun create(inspection: Inspection): DomainResult<Inspection>

    /**
     * Serialize read-transform-write for the latest durable aggregate in one transaction. A rejected
     * transform writes nothing. IDs, creation time and exclusive ownership cannot change. Use domain
     * operations in transform; successful return means committed storage, not a queued autosave.
     * The transform is synchronous and must not perform external effects.
     */
    suspend fun update(
        id: InspectionId,
        transform: (Inspection) -> DomainResult<Inspection>,
    ): DomainResult<Inspection>

    /**
     * Delete only this aggregate and its private attachments. Preserve properties, templates, other
     * inspections and exported PDFs. Coordinate recoverable file cleanup, not a fictitious file/DB
     * transaction. Report storage failures without silently discarding retryable file ownership.
     */
    suspend fun delete(id: InspectionId): DomainResult<Unit>
}

interface TemplateRepository {
    /** Built-in content is resolved for the requested language; custom text is never translated. */
    fun observeAll(language: AppLanguage): Flow<DomainResult<List<ChecklistTemplate>>>
    /** Reject built-in changes with ReadOnlyTemplate. Copies receive a new custom identity. */
    suspend fun saveCustom(template: ChecklistTemplate): DomainResult<ChecklistTemplate>
    /** No copied room depends on this record; deleting a built-in returns ReadOnlyTemplate. */
    suspend fun deleteCustom(id: TemplateId): DomainResult<Unit>
}

interface PropertyRepository {
    fun observeAll(): Flow<DomainResult<List<PropertyCard>>>
    /** Card edits never rewrite existing inspection snapshots. */
    suspend fun save(property: PropertyCard): DomainResult<PropertyCard>
    /** Atomically remove the card and detach all linked inspections, retaining all snapshots/media. */
    suspend fun deleteAndDetach(id: PropertyId): DomainResult<Unit>
}

interface SettingsRepository {
    fun observe(): Flow<DomainResult<Preferences>>
    suspend fun update(transform: (Preferences) -> Preferences): DomainResult<Preferences>
    /** Resets preferences only; inspection, template, property and media records are untouched. */
    suspend fun reset(): DomainResult<Preferences>
}

/** One consistent, independently readable full-report input; authored text is not localized. */
data class ReportRequest(
    val inspection: Inspection,
    val localeTag: String,
    val photoQuality: PdfPhotoQuality,
)

data class GeneratedReport(val fileId: LocalFileId)

interface ReportEngine {
    /**
     * Generate the complete report without mutating/completing the inspection. The implementation
     * stages a consistent copy of owned photos coordinated with attachment deletion; subsequent
     * edits affect only later requests. Output is private, not a public URI or claimed export.
     */
    suspend fun generate(request: ReportRequest): DomainResult<GeneratedReport>
}
