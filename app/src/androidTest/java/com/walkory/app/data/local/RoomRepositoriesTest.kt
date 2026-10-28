package com.walkory.app.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.walkory.app.domain.*
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomRepositoriesTest {
    @Test fun orderedAggregateAndCustomTemplateSurviveCloseAndReopen() = runBlocking {
        PersistenceFixture().use { fixture ->
            val card = PersistenceRecords.card()
            val template = PersistenceRecords.template()
            val inspection = PersistenceRecords.inspection(card)
            fixture.properties.save(card).accepted()
            fixture.templates.saveCustom(template).accepted()
            fixture.inspections.create(inspection).accepted()
            fixture.reopen()
            assertEquals(inspection, fixture.inspections.read(inspection.id))
            assertEquals(listOf(template), fixture.templates.readAll())
            assertEquals(listOf(card), fixture.properties.observeAll().first().accepted())
            assertEquals(123456789, fixture.inspections.read(inspection.id)!!.modifiedAt.nano)
        }
    }

    @Test fun propertyEditsAndDeletionPreserveEveryLinkedSnapshotAndMedia() = runBlocking {
        PersistenceFixture().use { fixture ->
            val card = PersistenceRecords.card()
            fixture.properties.save(card).accepted()
            val first = PersistenceRecords.inspection(card)
            val second = PersistenceRecords.inspection(card)
            val standalone = PersistenceRecords.inspection()
            listOf(first, second, standalone).forEach { fixture.inspections.create(it).accepted(); fixture.writePhotos(it) }
            fixture.properties.save(card.copy(name = "Renamed", address = "Other Street 20")).accepted()
            assertEquals(first, fixture.inspections.read(first.id))
            fixture.properties.deleteAndDetach(card.id).accepted()
            fixture.reopen()
            assertEquals(first.copy(propertyId = null), fixture.inspections.read(first.id))
            assertEquals(second.copy(propertyId = null), fixture.inspections.read(second.id))
            assertEquals(standalone, fixture.inspections.read(standalone.id))
            assertTrue(fixture.properties.observeAll().first().accepted().isEmpty())
            assertTrue(fixture.files.resolve(first.rooms[0].checks[0].conditionPhotos[0].fileId).isFile)
        }
    }

    @Test fun explicitLinkRelinkDetachAndMissingCardRejectionKeepCompletedSnapshotAndEveryPhotograph() = runBlocking {
        PersistenceFixture().use { fixture ->
            val original = PersistenceRecords.inspection()
            fixture.inspections.create(original).accepted()
            fixture.writePhotos(original)
            val first = PersistenceRecords.card()
            val second = PersistenceRecords.card().copy(address = "Another address")
            listOf(first, second).forEach { fixture.properties.save(it).accepted() }
            for (link in listOf(first.id, second.id, null)) {
                fixture.inspections.update(original.id) { PersistenceRecords.operations.linkProperty(it, link) }.accepted()
                fixture.reopen()
                val expected = original.copy(propertyId = link)
                assertEquals(expected, fixture.inspections.read(original.id))
                for (check in expected.rooms.flatMap { it.checks }) for (photo in check.conditionPhotos + check.issues.flatMap { it.photos }) {
                    assertTrue(fixture.files.resolve(photo.fileId).isFile)
                }
            }
            assertEquals(DomainResult.Failure(DomainError.NotFound(RecordKind.PROPERTY)),
                fixture.inspections.update(original.id) { PersistenceRecords.operations.linkProperty(it, PropertyId.new()) })
            assertEquals(original, fixture.inspections.read(original.id))
        }
    }

    @Test fun templateChangesAndDeletionNeverRewriteCopiedInspectionContent() = runBlocking {
        PersistenceFixture().use { fixture ->
            val template = PersistenceRecords.template()
            fixture.templates.saveCustom(template).accepted()
            val room = TemplateOperations.prepareRoom(RoomType.CUSTOM, "Authored room", template).accepted()
            val initial = PersistenceRecords.operations.create("Inspection", "Sample Street 1", InspectionRole.TENANT).accepted()
            val inspection = PersistenceRecords.operations.addRoom(initial, room).accepted()
            fixture.inspections.create(inspection).accepted()
            val changed = template.copy(name = "Renamed", checks = template.checks.reversed())
            fixture.templates.saveCustom(changed).accepted()
            assertEquals(listOf(changed), fixture.templates.readAll())
            fixture.templates.deleteCustom(template.id).accepted()
            fixture.reopen()
            assertEquals(inspection, fixture.inspections.read(inspection.id))
            assertTrue(fixture.templates.readAll().isEmpty())
            assertEquals(DomainResult.Failure(DomainError.ReadOnlyTemplate), fixture.templates.saveCustom(template.copy(origin = TemplateOrigin.BuiltIn("bedroom.tenant", InspectionRole.TENANT, RoomType.BEDROOM))))
        }
    }

    @Test fun deletingInspectionRemovesOnlyItsDescendantsAndPrivateFiles() = runBlocking {
        PersistenceFixture().use { fixture ->
            val card = PersistenceRecords.card()
            val template = PersistenceRecords.template()
            fixture.properties.save(card).accepted()
            fixture.templates.saveCustom(template).accepted()
            val removed = PersistenceRecords.inspection(card)
            val retained = PersistenceRecords.inspection(card)
            listOf(removed, retained).forEach { fixture.inspections.create(it).accepted(); fixture.writePhotos(it) }
            val exported = File(fixture.directory, "exported.pdf").apply { writeText("Independent export") }
            fixture.inspections.delete(removed.id).accepted()
            fixture.reopen()
            assertNull(fixture.inspections.read(removed.id))
            assertEquals(retained, fixture.inspections.read(retained.id))
            assertEquals(listOf(card), fixture.properties.observeAll().first().accepted())
            assertEquals(listOf(template), fixture.templates.readAll())
            for (check in removed.rooms.flatMap { it.checks }) {
                assertEquals(0, fixture.database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM checks WHERE id = ?", arrayOf(check.id.value)).use { it.moveToFirst(); it.getInt(0) })
                for (photo in check.conditionPhotos + check.issues.flatMap { it.photos }) assertFalse(fixture.files.resolve(photo.fileId).exists())
            }
            assertTrue(fixture.files.resolve(retained.rooms[0].checks[0].conditionPhotos[0].fileId).isFile)
            assertEquals("Independent export", exported.readText())
            assertTrue(fixture.database.records().pendingFiles().isEmpty())
        }
    }

    @Test fun updatesPersistNewOrderAndLastIssueDeletionWithoutClearingCompletion() = runBlocking {
        PersistenceFixture().use { fixture ->
            val original = PersistenceRecords.inspection()
            fixture.inspections.create(original).accepted()
            val edited = fixture.inspections.update(original.id) { current ->
                DomainResult.Success(current.copy(rooms = current.rooms.reversed().map { it.copy(checks = it.checks.reversed()) }))
            }.accepted()
            val check = edited.rooms[1].checks[2]
            for (issue in check.issues) fixture.inspections.update(original.id) { current ->
                PersistenceRecords.operations.deleteIssue(current, check.id, issue.id).map { it.inspection }
            }.accepted()
            fixture.reopen()
            val restored = fixture.inspections.read(original.id)!!
            assertEquals(listOf("Second bedroom", "First bedroom"), restored.rooms.map { it.name })
            assertEquals(listOf("Door", "Window", "Authored floor"), restored.rooms[1].checks.map { it.title })
            assertEquals(CheckState.UNCHECKED, restored.rooms[1].checks[2].state)
            assertEquals(InspectionState.COMPLETED, restored.state)
            assertEquals(original.rooms[0].checks[0].conditionPhotos, restored.rooms[1].checks[2].conditionPhotos)
        }
    }

    @Test fun rejectedTransformsAndOwnershipChangesDoNotPartiallyCommit() = runBlocking {
        PersistenceFixture().use { fixture ->
            val original = PersistenceRecords.inspection()
            fixture.inspections.create(original).accepted()
            val check = original.rooms[0].checks[0]
            assertEquals(DomainResult.Failure(DomainError.IssuesPreventStateChange(2)), fixture.inspections.update(original.id) { PersistenceRecords.operations.setCheckState(it, check.id, CheckState.OK) })
            assertEquals(DomainResult.Failure(DomainError.InvalidMutation), fixture.inspections.update(original.id) { DomainResult.Success(it.copy(id = InspectionId.new())) })
            assertEquals(DomainResult.Failure(DomainError.InvalidMutation), fixture.inspections.update(original.id) { DomainResult.Success(it.copy(state = InspectionState.IN_PROGRESS)) })
            val movedPhoto = original.rooms[0].checks[0].conditionPhotos[0]
            assertEquals(DomainResult.Failure(DomainError.InvalidMutation), fixture.inspections.update(original.id) { current ->
                val room = current.rooms[0]
                val checks = room.checks.toMutableList()
                checks[0] = checks[0].copy(conditionPhotos = checks[0].conditionPhotos.drop(1))
                checks[1] = checks[1].copy(conditionPhotos = listOf(movedPhoto))
                DomainResult.Success(current.copy(rooms = listOf(room.copy(checks = checks), current.rooms[1])))
            })
            fixture.reopen()
            assertEquals(original, fixture.inspections.read(original.id))
        }
    }

    @Test fun concurrentUpdatesTransformLatestCommittedAggregateWithoutLostRooms() = runBlocking {
        PersistenceFixture().use { fixture ->
            val initial = PersistenceRecords.operations.create("Concurrent inspection", "Sample Street 1", InspectionRole.TENANT).accepted()
            fixture.inspections.create(initial).accepted()
            coroutineScope {
                (1..20).map { index -> async(Dispatchers.IO) {
                    val room = TemplateOperations.prepareRoom(RoomType.CUSTOM, "Room $index", null).accepted()
                    fixture.inspections.update(initial.id) { PersistenceRecords.operations.addRoom(it, room) }.accepted()
                } }.awaitAll()
            }
            fixture.reopen()
            assertEquals((1..20).map { "Room $it" }.toSet(), fixture.inspections.read(initial.id)!!.rooms.map { it.name }.toSet())
        }
    }

    @Test fun sqlForeignKeysOwnerExclusivityAndIssueStatesRejectInvalidRows() = runBlocking {
        PersistenceFixture().use { fixture ->
            val inspection = PersistenceRecords.inspection()
            fixture.inspections.create(inspection).accepted()
            val sql = fixture.database.openHelper.writableDatabase
            expectConstraint { sql.execSQL("INSERT INTO rooms(id, inspectionId, type, name, position) VALUES('orphan', 'missing', 'CUSTOM', 'Room', 0)") }
            val check = inspection.rooms[0].checks[0]
            expectConstraint { sql.execSQL("UPDATE checks SET state = 'OK' WHERE id = ?", arrayOf(check.id.value)) }
            val photo = check.conditionPhotos[0]
            expectConstraint { sql.execSQL("UPDATE photographs SET issueId = ? WHERE id = ?", arrayOf(check.issues[0].id.value, photo.id.value)) }
            expectConstraint { sql.execSQL("UPDATE photographs SET checkId = NULL WHERE id = ?", arrayOf(photo.id.value)) }
            val otherCheck = inspection.rooms[1].checks[0]
            expectConstraint { sql.execSQL("INSERT INTO issues(id, checkId, description, position) VALUES('invalid-issue', ?, 'Note', 0)", arrayOf(otherCheck.id.value)) }
            assertEquals(inspection, fixture.inspections.read(inspection.id))
        }
    }

    @Test fun duplicateGlobalIdentitiesAndFilesRollBackNewInspection() = runBlocking {
        PersistenceFixture().use { fixture ->
            val original = PersistenceRecords.inspection()
            fixture.inspections.create(original).accepted()
            val duplicate = original.copy(id = InspectionId.new())
            assertEquals(DomainResult.Failure(DomainError.AttachmentAlreadyOwned), fixture.inspections.create(duplicate))
            assertNull(fixture.inspections.read(duplicate.id))
            val duplicateRoom = original.rooms[1].copy(checks = emptyList())
            val candidate = PersistenceRecords.operations.create("Other inspection", "Other Street 2", InspectionRole.TENANT).accepted().copy(rooms = listOf(duplicateRoom))
            assertEquals(DomainResult.Failure(DomainError.AlreadyExists), fixture.inspections.create(candidate))
            assertNull(fixture.inspections.read(candidate.id))
            assertEquals(original, fixture.inspections.read(original.id))
        }
    }

    @Test fun failedFileCleanupRetainsDurableQueueAndRetriesAfterReopen() = runBlocking {
        PersistenceFixture().use { fixture ->
            val removed = PersistenceRecords.inspection()
            val retained = PersistenceRecords.inspection()
            fixture.inspections.create(removed).accepted()
            fixture.inspections.create(retained).accepted()
            fixture.writePhotos(retained)
            val fileId = removed.rooms[0].checks[0].conditionPhotos[0].fileId
            val blocked = fixture.files.resolve(fileId)
            check(blocked.mkdir())
            File(blocked, "keep").writeText("Not an attachment file")
            assertEquals(DomainResult.Failure(DomainError.AttachmentCleanupPending), fixture.inspections.delete(removed.id))
            assertNull(fixture.inspections.read(removed.id))
            assertTrue(blocked.isDirectory)
            fixture.reopen()
            assertTrue(fileId.value in fixture.database.records().pendingFiles())
            check(File(blocked, "keep").delete()); check(blocked.delete())
            blocked.writeText("Recovered private attachment")
            fixture.inspections.retryAttachmentCleanup().accepted()
            assertFalse(blocked.exists())
            assertTrue(fixture.database.records().pendingFiles().isEmpty())
            assertEquals(retained, fixture.inspections.read(retained.id))
            assertTrue(fixture.files.resolve(retained.rooms[0].checks[0].conditionPhotos[0].fileId).isFile)
        }
    }

    @Test fun fullDatabaseReportsFailureAndPreservesLastConfirmedSnapshot() = runBlocking {
        PersistenceFixture().use { fixture ->
            val original = PersistenceRecords.inspection()
            fixture.inspections.create(original).accepted()
            val sql = fixture.database.openHelper.writableDatabase
            val pages = sql.query("PRAGMA page_count").use { it.moveToFirst(); it.getInt(0) }
            // This pragma is connection-local; pin it to the primary writer, not a WAL reader.
            sql.beginTransaction()
            try {
                assertEquals(pages, sql.query("PRAGMA max_page_count = $pages").use { it.moveToFirst(); it.getInt(0) })
                sql.setTransactionSuccessful()
            } finally { sql.endTransaction() }
            try {
                assertEquals(DomainResult.Failure(DomainError.InsufficientStorage), fixture.inspections.update(original.id) {
                    DomainResult.Success(it.copy(details = it.details.copy(inspector = "x".repeat(2_000_000))))
                })
                assertEquals(original, fixture.inspections.read(original.id))
            } finally {
                sql.beginTransaction()
                try {
                    sql.query("PRAGMA max_page_count = 1073741823").use { it.moveToFirst() }
                    sql.setTransactionSuccessful()
                } finally { sql.endTransaction() }
            }
        }
    }

    @Test fun cancellationIsNotReportedAsSavedOrConvertedToStorageFailure() = runBlocking {
        PersistenceFixture().use { fixture ->
            val original = PersistenceRecords.inspection()
            fixture.inspections.create(original).accepted()
            var cancelled = false
            try { fixture.inspections.update(original.id) { throw CancellationException("Cancelled edit") } }
            catch (_: CancellationException) { cancelled = true }
            assertTrue(cancelled)
            assertEquals(original, fixture.inspections.read(original.id))
        }
    }
}

private fun expectConstraint(action: () -> Unit) {
    try { action(); fail("Expected a real SQLite constraint failure") }
    catch (_: SQLiteConstraintException) { /* Expected database rejection, not a domain fake. */ }
}
