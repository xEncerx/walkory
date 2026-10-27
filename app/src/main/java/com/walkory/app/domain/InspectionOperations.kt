package com.walkory.app.domain

import java.time.Clock
import java.time.LocalDate

/** Pure snapshot transformations; repositories apply them inside a serialized atomic update. */
class InspectionOperations(private val clock: Clock = Clock.systemDefaultZone()) {
    fun create(
        name: String,
        address: String,
        role: InspectionRole,
        date: LocalDate = LocalDate.now(clock),
        inspector: String? = null,
        propertyId: PropertyId? = null,
    ): DomainResult<Inspection> {
        val details = details(name, address, date, role, inspector)
        return details.map {
            val now = clock.instant()
            Inspection(InspectionId.new(), it, propertyId, InspectionState.IN_PROGRESS, emptyList(), now, now)
        }
    }

    fun editDetails(
        inspection: Inspection,
        name: String,
        address: String,
        date: LocalDate,
        role: InspectionRole,
        inspector: String?,
    ): DomainResult<Inspection> = details(name, address, date, role, inspector).map {
        inspection.copy(details = it, modifiedAt = clock.instant())
    }

    fun addRoom(inspection: Inspection, room: InspectionRoom): DomainResult<Inspection> {
        val updated = inspection.copy(rooms = inspection.rooms + room, modifiedAt = clock.instant())
        return validateOwnership(updated).map { updated }
    }

    fun editCheck(
        inspection: Inspection,
        checkId: CheckId,
        title: String,
        guidance: String?,
        comment: String?,
    ): DomainResult<Inspection> {
        if (title.isBlank()) return DomainResult.Failure(DomainError.BlankField(RequiredField.CHECK_TITLE))
        return changeCheck(inspection, checkId) { DomainResult.Success(it.copy(title = title, guidance = guidance, comment = comment)) }
    }

    fun setCheckState(inspection: Inspection, checkId: CheckId, state: CheckState): DomainResult<Inspection> =
        changeCheck(inspection, checkId) { check ->
            if (check.issues.isNotEmpty() && state != CheckState.HAS_ISSUE) {
                DomainResult.Failure(DomainError.IssuesPreventStateChange(check.issues.size))
            } else DomainResult.Success(check.copy(state = state))
        }

    fun addIssue(
        inspection: Inspection,
        checkId: CheckId,
        description: String?,
        photos: List<Photograph> = emptyList(),
    ): DomainResult<Inspection> {
        if (description.isNullOrBlank() && photos.isEmpty()) return DomainResult.Failure(DomainError.EmptyIssue)
        return changeCheck(inspection, checkId) { check ->
            DomainResult.Success(check.copy(
                state = CheckState.HAS_ISSUE,
                issues = check.issues + Issue(IssueId.new(), description, photos.toList()),
            ))
        }.validateAttachments()
    }

    /** Removing the last issue never infers OK. Dismissing the requested choice leaves UNCHECKED. */
    fun deleteIssue(inspection: Inspection, checkId: CheckId, issueId: IssueId): DomainResult<IssueDeletion> {
        var needsState = false
        return changeCheck(inspection, checkId) { check ->
            val index = check.issues.indexOfFirst { it.id == issueId }
            if (index < 0) return@changeCheck DomainResult.Failure(DomainError.NotFound(RecordKind.ISSUE))
            val remaining = check.issues.toMutableList().apply { removeAt(index) }
            needsState = remaining.isEmpty()
            DomainResult.Success(check.copy(
                issues = remaining,
                state = if (needsState) CheckState.UNCHECKED else CheckState.HAS_ISSUE,
            ))
        }.map { IssueDeletion(it, if (needsState) checkId else null) }
    }

    fun attachPhotograph(
        inspection: Inspection,
        owner: PhotographOwner,
        photograph: Photograph,
    ): DomainResult<Inspection> {
        val checkId = when (owner) {
            is PhotographOwner.Condition -> owner.checkId
            is PhotographOwner.IssueEvidence -> owner.checkId
        }
        return changeCheck(inspection, checkId) { check ->
            when (owner) {
                is PhotographOwner.Condition -> DomainResult.Success(check.copy(conditionPhotos = check.conditionPhotos + photograph))
                is PhotographOwner.IssueEvidence -> {
                    val index = check.issues.indexOfFirst { it.id == owner.issueId }
                    if (index < 0) return@changeCheck DomainResult.Failure(DomainError.NotFound(RecordKind.ISSUE))
                    val issues = check.issues.toMutableList()
                    issues[index] = issues[index].copy(photos = issues[index].photos + photograph)
                    DomainResult.Success(check.copy(issues = issues))
                }
            }
        }.validateAttachments()
    }

    fun complete(inspection: Inspection, confirmUnchecked: Boolean = false): DomainResult<Inspection> {
        if (inspection.state == InspectionState.COMPLETED) return DomainResult.Success(inspection)
        val unchecked = inspection.progress().uncheckedChecks
        if (unchecked > 0 && !confirmUnchecked) {
            return DomainResult.Failure(DomainError.UncheckedConfirmationRequired(unchecked))
        }
        return DomainResult.Success(inspection.copy(state = InspectionState.COMPLETED, modifiedAt = clock.instant()))
    }

    /** Card deletion changes only the link; authored metadata, results and files remain owned here. */
    fun detachProperty(inspection: Inspection, removedPropertyId: PropertyId): Inspection =
        if (inspection.propertyId != removedPropertyId) inspection
        else inspection.copy(propertyId = null, modifiedAt = clock.instant())

    private fun details(
        name: String, address: String, date: LocalDate, role: InspectionRole, inspector: String?,
    ): DomainResult<InspectionDetails> = when {
        name.isBlank() -> DomainResult.Failure(DomainError.BlankField(RequiredField.INSPECTION_NAME))
        address.isBlank() -> DomainResult.Failure(DomainError.BlankField(RequiredField.ADDRESS))
        else -> DomainResult.Success(InspectionDetails(name, PropertySnapshot(address), date, role, inspector))
    }

    private inline fun changeCheck(
        inspection: Inspection,
        checkId: CheckId,
        change: (InspectionCheck) -> DomainResult<InspectionCheck>,
    ): DomainResult<Inspection> {
        for ((roomIndex, room) in inspection.rooms.withIndex()) {
            val checkIndex = room.checks.indexOfFirst { it.id == checkId }
            if (checkIndex < 0) continue
            return change(room.checks[checkIndex]).map { updated ->
                val checks = room.checks.toMutableList().apply { set(checkIndex, updated) }
                val rooms = inspection.rooms.toMutableList().apply { set(roomIndex, room.copy(checks = checks)) }
                inspection.copy(rooms = rooms, modifiedAt = clock.instant())
            }
        }
        return DomainResult.Failure(DomainError.NotFound(RecordKind.CHECK))
    }

    private fun DomainResult<Inspection>.validateAttachments(): DomainResult<Inspection> = when (this) {
        is DomainResult.Failure -> this
        is DomainResult.Success -> validateOwnership(value).map { value }
    }
}

data class IssueDeletion(val inspection: Inspection, val requestStateFor: CheckId?)

/** Repositories also enforce these identities globally, including exclusive ownership of local files. */
fun validateOwnership(inspection: Inspection): DomainResult<Unit> {
    val rooms = HashSet<RoomId>()
    val checks = HashSet<CheckId>()
    val issues = HashSet<IssueId>()
    val photos = HashSet<PhotographId>()
    val files = HashSet<LocalFileId>()
    fun accept(photo: Photograph): Boolean = photos.add(photo.id) && files.add(photo.fileId)
    for (room in inspection.rooms) {
        if (!rooms.add(room.id)) return DomainResult.Failure(DomainError.AlreadyExists)
        for (check in room.checks) {
            if (!checks.add(check.id)) return DomainResult.Failure(DomainError.AlreadyExists)
            for (photo in check.conditionPhotos) {
                if (!accept(photo)) return DomainResult.Failure(DomainError.AttachmentAlreadyOwned)
            }
            for (issue in check.issues) {
                if (!issues.add(issue.id)) return DomainResult.Failure(DomainError.AlreadyExists)
                for (photo in issue.photos) {
                    if (!accept(photo)) return DomainResult.Failure(DomainError.AttachmentAlreadyOwned)
                }
            }
        }
    }
    return DomainResult.Success(Unit)
}
