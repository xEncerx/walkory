package com.walkory.app.domain

import kotlinx.coroutines.flow.Flow

/**
 * Flows expose confirmed durable snapshots or explicit storage errors, never optimistic saved state.
 * Implementations translate storage failures, propagate coroutine cancellation, and preserve old data
 * when a database write fails. AttachmentCleanupPending is a post-commit cleanup outcome instead.
 * Collection order inside each aggregate is retained.
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
     * Removing evidence can return AttachmentCleanupPending after the updated snapshot has committed.
     */
    suspend fun update(
        id: InspectionId,
        transform: (Inspection) -> DomainResult<Inspection>,
    ): DomainResult<Inspection>

    /**
     * Delete only this aggregate and its private attachments. Preserve properties, templates, other
     * inspections and exported PDFs. Coordinate recoverable file cleanup, not a fictitious file/DB
     * transaction. AttachmentCleanupPending means deletion committed with durable cleanup remaining.
     */
    suspend fun delete(id: InspectionId): DomainResult<Unit>

    /** Retry only the private attachment files durably marked for deletion, including after restart. */
    suspend fun retryAttachmentCleanup(): DomainResult<Unit>
}

interface TemplateRepository {
    /** Built-in content is resolved for the requested language; custom text is never translated. */
    fun observeAll(language: AppLanguage): Flow<DomainResult<List<ChecklistTemplate>>>
    /** Reject built-in changes with ReadOnlyTemplate. Copies receive a new custom identity. */
    suspend fun saveCustom(template: ChecklistTemplate): DomainResult<ChecklistTemplate>
    /** Delete a stored custom template; an ID absent from this store returns NotFound. */
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
