package com.walkory.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AttachmentOwnershipTest {
    private val operations = DomainFixtures.operations

    @Test fun conditionAndIssueTargetsKeepIndependentCollectionsAndCaptions() {
        val initial = DomainFixtures.withRoom()
        val checkId = initial.firstCheck().id
        val condition = DomainFixtures.photo("Overview")
        val issuePhoto = DomainFixtures.photo("Detail")
        val issued = operations.addIssue(initial, checkId, "Visible mark").value()
        val issueId = issued.firstCheck().issues.single().id
        val overview = operations.attachPhotograph(issued, PhotographOwner.Condition(checkId), condition).value()
        val detail = operations.attachPhotograph(overview, PhotographOwner.IssueEvidence(checkId, issueId), issuePhoto).value()
        assertEquals(listOf(condition), detail.firstCheck().conditionPhotos)
        assertEquals(listOf(issuePhoto), detail.firstCheck().issues.single().photos)
        assertEquals(emptyList<Photograph>(), initial.firstCheck().conditionPhotos)
        assertEquals(DomainResult.Success(Unit), validateOwnership(detail))
    }

    @Test fun samePhotoOrFileCannotBeSharedBetweenChecksOrIssues() {
        val initial = DomainFixtures.withRoom()
        val id = initial.firstCheck().id
        val other = initial.rooms.first().checks[1].id
        val photo = DomainFixtures.photo()
        val owned = operations.attachPhotograph(initial, PhotographOwner.Condition(id), photo).value()
        val failure = DomainResult.Failure(DomainError.AttachmentAlreadyOwned)
        assertEquals(failure, operations.attachPhotograph(owned, PhotographOwner.Condition(other), photo))
        assertEquals(failure, operations.addIssue(owned, other, "Detail", listOf(photo.copy(id = PhotographId.new()))))
        assertEquals(failure, operations.addIssue(owned, id, "Detail", listOf(photo.copy(fileId = LocalFileId.new()))))
        assertEquals(listOf(photo), owned.firstCheck().conditionPhotos)
        assertEquals(CheckState.UNCHECKED, owned.rooms.first().checks[1].state)
    }

    @Test fun duplicateFilesInsideOneNewIssueAndDuplicatedDescendantIdsAreRejected() {
        val initial = DomainFixtures.withRoom()
        val photo = DomainFixtures.photo()
        assertEquals(DomainResult.Failure(DomainError.AttachmentAlreadyOwned), operations.addIssue(initial, initial.firstCheck().id, null, listOf(photo, photo.copy(id = PhotographId.new()))))
        val duplicated = initial.rooms.first().copy(id = RoomId.new())
        assertEquals(DomainResult.Failure(DomainError.AlreadyExists), operations.addRoom(initial, duplicated))
    }

    @Test fun unknownAttachmentOwnersAreRejectedWithoutChangingExistingEvidence() {
        val initial = DomainFixtures.withRoom()
        assertEquals(DomainResult.Failure(DomainError.NotFound(RecordKind.CHECK)), operations.attachPhotograph(initial, PhotographOwner.Condition(CheckId.new()), DomainFixtures.photo()))
        assertEquals(DomainResult.Failure(DomainError.NotFound(RecordKind.ISSUE)), operations.attachPhotograph(initial, PhotographOwner.IssueEvidence(initial.firstCheck().id, IssueId.new()), DomainFixtures.photo()))
        assertEquals(InspectionProgress(2, 0, 2, 0, 0), initial.progress())
    }
}
