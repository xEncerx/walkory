package com.walkory.app.domain

import org.junit.Assert.*
import org.junit.Test

class InspectionEditingTest {
    private val operations = DomainFixtures.operations

    @Test fun editingIssuePreservesIdentityAndPhotographsAndRejectsEmptyTextOnlyEvidence() {
        val initial = DomainFixtures.withRoom()
        val check = initial.firstCheck().id
        val photo = DomainFixtures.photo("Detail")
        val issued = operations.addIssue(initial, check, "Original", listOf(photo)).value()
        val issue = issued.firstCheck().issues.single()
        val edited = operations.editIssue(issued, check, issue.id, null).value()
        assertEquals(issue.copy(description = null), edited.firstCheck().issues.single())
        assertEquals(CheckState.HAS_ISSUE, edited.firstCheck().state)
        val textOnly = operations.addIssue(edited, check, "Retain").value()
        val textId = textOnly.firstCheck().issues.last().id
        assertEquals(DomainResult.Failure(DomainError.EmptyIssue), operations.editIssue(textOnly, check, textId, " \n"))
        assertEquals(DomainResult.Failure(DomainError.NotFound(RecordKind.ISSUE)), operations.editIssue(textOnly, check, IssueId.new(), "Missing"))
        assertEquals(listOf(issue.copy(description = null), Issue(textId, "Retain")), textOnly.firstCheck().issues)
    }

    @Test fun listEditsAndRemovalPreserveUnselectedResultsAndCompletion() {
        val room = DomainFixtures.room()
        val other = DomainFixtures.room("Other bedroom")
        var inspection = operations.addRoom(DomainFixtures.withRoom(room), other).value()
        val check = room.checks.first().id
        inspection = operations.addIssue(inspection, check, "Observation", listOf(DomainFixtures.photo())).value()
        inspection = operations.editCheck(inspection, check, "Authored", "Guidance", "General").value()
        inspection = operations.complete(inspection, true).value()
        val evidence = inspection.firstCheck()
        val moved = operations.moveCheck(inspection, room.id, check, room.checks.last().id).value()
        assertEquals(evidence, moved.rooms.first().checks.last())
        assertEquals(other, moved.rooms.last())
        assertEquals(DomainResult.Failure(DomainError.BlankField(RequiredField.ROOM_NAME)), operations.renameRoom(moved, room.id, " \t"))
        val renamed = operations.renameRoom(moved, room.id, "Authored bedroom").value()
        assertEquals(moved.rooms.first().checks, renamed.rooms.first().checks)
        val added = InspectionCheck(CheckId.new(), "New point")
        val extended = operations.addCheck(renamed, room.id, added).value()
        val removed = operations.removeCheck(extended, check).value()
        assertEquals(listOf(room.checks.last(), added), removed.rooms.first().checks)
        assertEquals(other, removed.rooms.last())
        val final = operations.removeRoom(removed, room.id).value()
        assertEquals(listOf(other), final.rooms)
        assertEquals(InspectionState.COMPLETED, final.state)
        assertEquals(DomainResult.Failure(DomainError.NotFound(RecordKind.CHECK)), operations.removeCheck(final, check))
        assertEquals(DomainResult.Failure(DomainError.AlreadyExists), operations.addCheck(inspection, room.id, other.checks.first()))
    }
}
