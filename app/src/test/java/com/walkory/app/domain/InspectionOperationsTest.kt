package com.walkory.app.domain

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

class InspectionOperationsTest {
    private val operations = DomainFixtures.operations

    @Test fun creationUsesLocalCalendarDateAndRejectsMissingRequiredText() {
        val inspection = DomainFixtures.inspection()
        assertEquals(LocalDate.of(2026, 1, 16), inspection.details.date)
        assertEquals(InspectionRole.TENANT, inspection.details.role)
        assertEquals(DomainResult.Failure(DomainError.BlankField(RequiredField.INSPECTION_NAME)), operations.create(" ", "Address", InspectionRole.TENANT))
        assertEquals(DomainResult.Failure(DomainError.BlankField(RequiredField.ADDRESS)), operations.create("Inspection", "\t", InspectionRole.LANDLORD))
        val landlord = operations.create("Departure", "Other Street 2", InspectionRole.LANDLORD, LocalDate.of(2025, 12, 31)).value()
        assertEquals(LocalDate.of(2025, 12, 31), landlord.details.date)
        assertEquals(InspectionRole.LANDLORD, landlord.details.role)
        assertNotEquals(inspection.id, landlord.id)
    }

    @Test fun completionRequiresWarningWithActualUncheckedCountAndExplicitConfirmation() {
        val initial = DomainFixtures.withRoom()
        val partial = operations.setCheckState(initial, initial.firstCheck().id, CheckState.NOT_APPLICABLE).value()
        assertEquals(DomainResult.Failure(DomainError.UncheckedConfirmationRequired(2)), operations.complete(initial))
        assertEquals(DomainResult.Failure(DomainError.UncheckedConfirmationRequired(1)), operations.complete(partial))
        assertEquals(InspectionState.IN_PROGRESS, partial.state)
        val completed = operations.complete(partial, confirmUnchecked = true).value()
        assertEquals(InspectionState.COMPLETED, completed.state)
        assertEquals(CheckState.UNCHECKED, completed.rooms.first().checks[1].state)
        assertEquals(partial.rooms, completed.rooms)
    }

    @Test fun completionWithoutUncheckedChecksAndEmptyCompletionNeedNoWarning() {
        var checked = DomainFixtures.withRoom()
        for (check in checked.rooms.first().checks) checked = operations.setCheckState(checked, check.id, CheckState.NOT_APPLICABLE).value()
        assertEquals(InspectionState.COMPLETED, operations.complete(checked).value().state)
        assertEquals(InspectionState.COMPLETED, operations.complete(DomainFixtures.inspection()).value().state)
    }

    @Test fun completedEditsPreserveCompletionAndCreationIdentityWhileAdvancingModificationTime() {
        val completed = operations.complete(DomainFixtures.withRoom(), confirmUnchecked = true).value()
        val laterClock = Clock.fixed(Instant.parse("2026-02-01T12:00:00Z"), ZoneOffset.UTC)
        val later = InspectionOperations(laterClock)
        val checkId = completed.firstCheck().id
        val details = later.editDetails(completed, "Updated name", "Updated Street 20", LocalDate.of(2026, 2, 1), InspectionRole.LANDLORD, null).value()
        val check = later.editCheck(details, checkId, "Edited floor", null, "Updated comment").value()
        val issue = later.addIssue(check, checkId, "Observed mark").value()
        val removed = later.deleteIssue(issue, checkId, issue.firstCheck().issues.single().id).value().inspection
        val state = later.setCheckState(removed, checkId, CheckState.OK).value()
        val room = later.addRoom(state, DomainFixtures.room("Another bedroom")).value()
        for (edited in listOf(details, check, issue, removed, state, room)) {
            assertEquals(InspectionState.COMPLETED, edited.state)
            assertEquals(completed.id, edited.id)
            assertEquals(completed.createdAt, edited.createdAt)
            assertEquals(laterClock.instant(), edited.modifiedAt)
        }
        assertEquals("Updated Street 20", room.details.property.address)
        assertEquals("Updated comment", room.firstCheck().comment)
        assertSame(room, later.complete(room).value())
    }

    @Test fun detachingSelectedPropertyPreservesSnapshotResultsMediaAndCompletion() {
        val card = PropertyCard(PropertyId.new(), "Apartment", "Original Street 1")
        val initial = DomainFixtures.withRoom(inspection = DomainFixtures.inspection(card.id))
        val issued = operations.addIssue(initial, initial.firstCheck().id, "Surface mark", listOf(DomainFixtures.photo())).value()
        val completed = operations.complete(issued, confirmUnchecked = true).value()
        val detached = operations.detachProperty(completed, card.id)
        assertEquals(null, detached.propertyId)
        assertEquals(completed.details, detached.details)
        assertEquals(completed.rooms, detached.rooms)
        assertEquals(completed.state, detached.state)
        assertEquals(completed.id, detached.id)
        assertEquals(completed.createdAt, detached.createdAt)
        assertSame(completed, operations.detachProperty(completed, PropertyId.new()))
    }

    @Test fun progressSeparatesUncheckedProblematicAndIssueCountsAndCountsNotApplicable() {
        val empty = DomainFixtures.inspection()
        assertEquals(InspectionProgress(0, 0, 0, 0, 0), empty.progress())
        assertEquals(0.0, empty.progress().fraction, 0.0)
        val initial = DomainFixtures.withRoom()
        assertEquals(InspectionProgress(2, 0, 2, 0, 0), initial.progress())
        var mixed = operations.addRoom(initial, DomainFixtures.room("Second bedroom")).value()
        val checks = mixed.rooms.flatMap { it.checks }
        mixed = operations.setCheckState(mixed, checks[0].id, CheckState.NOT_APPLICABLE).value()
        mixed = operations.setCheckState(mixed, checks[1].id, CheckState.OK).value()
        mixed = operations.addIssue(mixed, checks[2].id, "First issue").value()
        mixed = operations.addIssue(mixed, checks[2].id, "Second issue").value()
        assertEquals(InspectionProgress(4, 3, 1, 1, 2), mixed.progress())
        assertEquals(0.75, mixed.progress().fraction, 0.0)
        var notApplicable = initial
        for (check in checks.take(2)) notApplicable = operations.setCheckState(notApplicable, check.id, CheckState.NOT_APPLICABLE).value()
        assertEquals(InspectionProgress(2, 2, 0, 0, 0), notApplicable.progress())
        assertEquals(1.0, notApplicable.progress().fraction, 0.0)
    }

    @Test fun invalidEditsAndRepeatedIdentitiesAreRejected() {
        val initial = DomainFixtures.withRoom()
        assertEquals(DomainResult.Failure(DomainError.AlreadyExists), operations.addRoom(initial, initial.rooms.first()))
        assertEquals(DomainResult.Failure(DomainError.BlankField(RequiredField.CHECK_TITLE)), operations.editCheck(initial, initial.firstCheck().id, " ", null, null))
        assertEquals(DomainResult.Failure(DomainError.BlankField(RequiredField.INSPECTION_NAME)), operations.editDetails(initial, "", "Address", initial.details.date, initial.details.role, null))
        assertEquals(DomainResult.Failure(DomainError.BlankField(RequiredField.ADDRESS)), operations.editDetails(initial, "Inspection", "", initial.details.date, initial.details.role, null))
    }
}
