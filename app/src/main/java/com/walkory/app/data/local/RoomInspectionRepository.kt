package com.walkory.app.data.local

import androidx.room.withTransaction
import com.walkory.app.domain.*

internal class RoomInspectionRepository(
    private val database: WalkoryDatabase,
    private val attachments: AttachmentStorageCoordinator,
) : InspectionRepository {
    private val dao = database.records()

    override fun observeAll() = dao.observeInspections().durableSnapshots { rows -> rows.map { it.toDomain() } }
    override fun observe(id: InspectionId) = dao.observeInspection(id.value).durableSnapshots { it?.toDomain() }

    override suspend fun create(inspection: Inspection): DomainResult<Inspection> = storageResult {
        attachments.coordinated {
            val validation = validateOwnership(inspection)
            if (validation is DomainResult.Failure) return@coordinated validation
            database.withTransaction {
                if (inspection.propertyId != null && !dao.propertyExists(inspection.propertyId.value)) {
                    return@withTransaction DomainResult.Failure(DomainError.NotFound(RecordKind.PROPERTY))
                }
                val next = InspectionRows(inspection)
                if (filesAlreadyExist(next.photos.values.map { it.fileId })) {
                    return@withTransaction DomainResult.Failure(DomainError.AttachmentAlreadyOwned)
                }
                dao.insertInspection(inspection.toEntity())
                writeRows(null, next)
                DomainResult.Success(inspection)
            }
        }
    }

    override suspend fun update(
        id: InspectionId,
        transform: (Inspection) -> DomainResult<Inspection>,
    ): DomainResult<Inspection> = storageResult {
        attachments.coordinated {
            var removedPhotos = false
            val result = database.withTransaction {
                val previous = dao.inspection(id.value)?.toDomain()
                    ?: return@withTransaction DomainResult.Failure(DomainError.NotFound(RecordKind.INSPECTION))
                val transformed = transform(previous)
                if (transformed is DomainResult.Failure) return@withTransaction transformed
                val next = (transformed as DomainResult.Success).value
                val ownership = validateOwnership(next)
                if (ownership is DomainResult.Failure) return@withTransaction ownership
                if (next.id != previous.id || next.createdAt != previous.createdAt ||
                    (previous.state == InspectionState.COMPLETED && next.state != InspectionState.COMPLETED)) {
                    return@withTransaction DomainResult.Failure(DomainError.InvalidMutation)
                }
                if (next.propertyId != null && !dao.propertyExists(next.propertyId.value)) {
                    return@withTransaction DomainResult.Failure(DomainError.NotFound(RecordKind.PROPERTY))
                }
                val before = InspectionRows(previous)
                val after = InspectionRows(next)
                if (!sameOwnership(before, after)) return@withTransaction DomainResult.Failure(DomainError.InvalidMutation)
                val newFiles = after.photos.values.mapNotNull { if (it.id !in before.photos) it.fileId else null }
                if (filesAlreadyExist(newFiles)) return@withTransaction DomainResult.Failure(DomainError.AttachmentAlreadyOwned)
                removedPhotos = before.photos.keys.any { it !in after.photos }
                val nextRecord = next.toEntity()
                if (nextRecord != previous.toEntity()) dao.updateInspection(nextRecord)
                writeRows(before, after)
                DomainResult.Success(next)
            }
            if (result is DomainResult.Success && removedPhotos) {
                val cleanup = attachments.cleanupPending()
                if (cleanup is DomainResult.Failure) return@coordinated cleanup
            }
            result
        }
    }

    override suspend fun delete(id: InspectionId): DomainResult<Unit> = storageResult {
        attachments.coordinated {
            val deleted = database.withTransaction { dao.deleteInspection(id.value) }
            if (deleted == 0) DomainResult.Failure(DomainError.NotFound(RecordKind.INSPECTION))
            else attachments.cleanupPending()
        }
    }

    override suspend fun retryAttachmentCleanup(): DomainResult<Unit> = storageResult {
        attachments.coordinated { attachments.cleanupPending() }
    }

    private suspend fun filesAlreadyExist(ids: List<String>): Boolean {
        for (batch in ids.chunked(900)) if (dao.existingFiles(batch).isNotEmpty()) return true
        return false
    }

    private fun sameOwnership(before: InspectionRows, after: InspectionRows): Boolean {
        for ((id, check) in after.checks) if (before.checks[id]?.let { it.roomId != check.roomId } == true) return false
        for ((id, issue) in after.issues) if (before.issues[id]?.let { it.checkId != issue.checkId } == true) return false
        val oldFiles = before.photos.values.associate { it.fileId to it.id }
        for ((id, photo) in after.photos) {
            val previous = before.photos[id]
            if (previous != null && (previous.checkId != photo.checkId || previous.issueId != photo.issueId || previous.fileId != photo.fileId)) return false
            if (oldFiles[photo.fileId]?.let { it != id } == true) return false
        }
        return true
    }

    private suspend fun writeRows(before: InspectionRows?, after: InspectionRows) {
        val rooms = RowChanges(before?.rooms.orEmpty(), after.rooms)
        val checks = RowChanges(before?.checks.orEmpty(), after.checks)
        val issues = RowChanges(before?.issues.orEmpty(), after.issues)
        val photos = RowChanges(before?.photos.orEmpty(), after.photos)
        // Remove issues before changing their parent's state; the SQL trigger protects live evidence.
        for (batch in photos.removed.chunked(900)) dao.deletePhotos(batch)
        for (batch in issues.removed.chunked(900)) dao.deleteIssues(batch)
        for (batch in checks.removed.chunked(900)) dao.deleteChecks(batch)
        for (batch in rooms.removed.chunked(900)) dao.deleteRooms(batch)
        if (rooms.inserted.isNotEmpty()) dao.insertRooms(rooms.inserted)
        if (rooms.updated.isNotEmpty()) dao.updateRooms(rooms.updated)
        if (checks.inserted.isNotEmpty()) dao.insertChecks(checks.inserted)
        if (checks.updated.isNotEmpty()) dao.updateChecks(checks.updated)
        if (issues.inserted.isNotEmpty()) dao.insertIssues(issues.inserted)
        if (issues.updated.isNotEmpty()) dao.updateIssues(issues.updated)
        if (photos.inserted.isNotEmpty()) {
            dao.insertFiles(photos.inserted.map { AttachmentFileEntity(it.fileId) })
            dao.insertPhotos(photos.inserted)
        }
        if (photos.updated.isNotEmpty()) dao.updatePhotos(photos.updated)
    }
}

private class RowChanges<T>(before: Map<String, T>, after: Map<String, T>) {
    val removed = before.keys.filter { it !in after }
    val inserted = ArrayList<T>()
    val updated = ArrayList<T>()
    init {
        for ((id, value) in after) {
            if (id !in before) inserted.add(value)
            else if (before[id] != value) updated.add(value)
        }
    }
}
