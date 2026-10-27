package com.walkory.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IssueOperationsTest {
    private val operations = DomainFixtures.operations

    @Test fun addingIssueMarksCheckAndRejectsEveryIncompatibleStateWithoutLosingEvidence() {
        val initial = DomainFixtures.withRoom()
        val id = initial.firstCheck().id
        val photo = DomainFixtures.photo()
        val updated = operations.addIssue(initial, id, "Loose handle", listOf(photo)).value()
        assertEquals(CheckState.HAS_ISSUE, updated.firstCheck().state)
        assertEquals("Loose handle", updated.firstCheck().issues.single().description)
        assertEquals(listOf(photo), updated.firstCheck().issues.single().photos)
        for (state in listOf(CheckState.UNCHECKED, CheckState.OK, CheckState.NOT_APPLICABLE)) {
            assertEquals(DomainResult.Failure(DomainError.IssuesPreventStateChange(1)), operations.setCheckState(updated, id, state))
        }
        assertEquals(updated.firstCheck(), operations.setCheckState(updated, id, CheckState.HAS_ISSUE).value().firstCheck())
        assertEquals(CheckState.UNCHECKED, initial.firstCheck().state)
        assertTrue(initial.firstCheck().issues.isEmpty())
    }

    @Test fun emptyIssueIsRejectedButTextOnlyAndPhotoOnlyIssuesAreAllowed() {
        val inspection = DomainFixtures.withRoom()
        val id = inspection.firstCheck().id
        assertEquals(DomainResult.Failure(DomainError.EmptyIssue), operations.addIssue(inspection, id, null))
        assertEquals(DomainResult.Failure(DomainError.EmptyIssue), operations.addIssue(inspection, id, " \t"))
        val text = operations.addIssue(inspection, id, "Scratch").value()
        val photo = operations.addIssue(inspection, id, null, listOf(DomainFixtures.photo())).value()
        assertEquals("Scratch", text.firstCheck().issues.single().description)
        assertEquals(null, photo.firstCheck().issues.single().description)
        assertEquals(CheckState.HAS_ISSUE, photo.firstCheck().state)
    }

    @Test fun explicitProblematicStateDoesNotRequireAnIssue() {
        val inspection = DomainFixtures.withRoom()
        val updated = operations.setCheckState(inspection, inspection.firstCheck().id, CheckState.HAS_ISSUE).value()
        assertEquals(1, updated.progress().problematicChecks)
        assertEquals(0, updated.progress().issueCount)
    }

    @Test fun lastIssueDeletionRequestsStateAndDismissalLeavesUnchecked() {
        val initial = DomainFixtures.withRoom()
        val id = initial.firstCheck().id
        val one = operations.addIssue(initial, id, "First observation").value()
        val two = operations.addIssue(one, id, "Second observation").value()
        assertNotEquals(two.firstCheck().issues[0].id, two.firstCheck().issues[1].id)
        val first = operations.deleteIssue(two, id, two.firstCheck().issues.first().id).value()
        assertEquals(null, first.requestStateFor)
        assertEquals(CheckState.HAS_ISSUE, first.inspection.firstCheck().state)
        assertEquals("Second observation", first.inspection.firstCheck().issues.single().description)
        val last = operations.deleteIssue(first.inspection, id, first.inspection.firstCheck().issues.single().id).value()
        assertEquals(id, last.requestStateFor)
        assertEquals(CheckState.UNCHECKED, last.inspection.firstCheck().state)
        assertTrue(last.inspection.firstCheck().issues.isEmpty())
        assertEquals(CheckState.NOT_APPLICABLE, operations.setCheckState(last.inspection, id, CheckState.NOT_APPLICABLE).value().firstCheck().state)
    }

    @Test fun deletingIssuePreservesConditionPhotosCommentsAndOtherChecks() {
        val initial = DomainFixtures.withRoom()
        val id = initial.firstCheck().id
        val condition = DomainFixtures.photo("Overview")
        val issued = operations.addIssue(initial, id, "Mark", listOf(DomainFixtures.photo("Detail"))).value()
        val attached = operations.attachPhotograph(issued, PhotographOwner.Condition(id), condition).value()
        val commented = operations.editCheck(attached, id, "Floor", "Guidance", "Condition note").value()
        val deleted = operations.deleteIssue(commented, id, commented.firstCheck().issues.single().id).value().inspection
        assertEquals(listOf(condition), deleted.firstCheck().conditionPhotos)
        assertEquals("Condition note", deleted.firstCheck().comment)
        assertEquals(initial.rooms.first().checks[1], deleted.rooms.first().checks[1])
    }

    @Test fun missingChecksAndIssuesReturnTypedErrors() {
        val inspection = DomainFixtures.withRoom()
        val missing = CheckId.new()
        val checkError = DomainResult.Failure(DomainError.NotFound(RecordKind.CHECK))
        assertEquals(checkError, operations.setCheckState(inspection, missing, CheckState.OK))
        assertEquals(checkError, operations.addIssue(inspection, missing, "Observation"))
        assertEquals(checkError, operations.deleteIssue(inspection, missing, IssueId.new()))
        assertEquals(DomainResult.Failure(DomainError.NotFound(RecordKind.ISSUE)), operations.deleteIssue(inspection, inspection.firstCheck().id, IssueId.new()))
    }
}
