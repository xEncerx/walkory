package com.walkory.app.data.local

import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import android.system.ErrnoException
import android.system.OsConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.walkory.app.domain.*
import com.walkory.app.platform.images.createLocalImageLoader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotographStorageTest {
    private val operations = InspectionOperations()
    private fun image(): ByteArray {
        val bitmap = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }
    private suspend fun create(f: PersistenceFixture): Inspection {
        val room = TemplateOperations.prepareRoom(RoomType.KITCHEN, "Kitchen", ChecklistTemplate(TemplateId.new(), "Checks", TemplateOrigin.Custom, listOf(TemplateCheck("Floor")))).accepted()
        return f.inspections.create(operations.create("Photo inspection", "Sample Street 8", InspectionRole.TENANT).accepted().copy(rooms = listOf(room))).accepted()
    }
    private suspend fun add(f: PersistenceFixture, record: Inspection, owner: PhotographOwner, count: Int = 2, description: String? = null): Inspection =
        f.inspections.importPhotographs(record.id, owner, count, description) { photos ->
            for (photo in photos) {
                val copied = f.files.copyImage(photo.fileId) { ByteArrayInputStream(image()) }
                if (copied is DomainResult.Failure) return@importPhotographs copied
            }
            DomainResult.Success(Unit)
        }.accepted()

    @Test fun privateCopiesCaptionsAndSelectiveOwnerDeletionSurviveReopen() = runBlocking {
        PersistenceFixture().use { f ->
            val initial = create(f)
            val check = initial.rooms.single().checks.single().id
            val condition = PhotographOwner.Condition(check)
            val issue = PhotographOwner.IssueEvidence(check, IssueId.new())
            val imported = add(f, add(f, initial, condition), issue, description = "")
            val originalBytes = image()
            val all = imported.rooms.single().checks.single()
            val first = all.conditionPhotos.first()
            val captioned = f.inspections.update(initial.id) { operations.captionPhotograph(it, condition, first.id, "Общий вид") }.accepted()
            f.reopen()
            assertEquals(captioned, f.inspections.read(initial.id))
            assertArrayEquals(originalBytes, f.files.resolve(first.fileId).readBytes())
            val removed = f.inspections.update(initial.id) { operations.removePhotograph(it, condition, first.id) }.accepted()
            assertFalse(f.files.resolve(first.fileId).exists())
            assertEquals(listOf(all.conditionPhotos.last()), removed.rooms.single().checks.single().conditionPhotos)
            val retained = all.conditionPhotos.last()
            f.inspections.update(initial.id) { operations.deleteIssue(it, check, issue.issueId).map { result -> result.inspection } }.accepted()
            all.issues.single().photos.forEach { assertFalse(f.files.resolve(it.fileId).exists()) }
            assertTrue(f.files.resolve(retained.fileId).exists())
            assertEquals(CheckState.UNCHECKED, f.inspections.read(initial.id)!!.rooms.single().checks.single().state)
        }
    }

    @Test fun invalidBatchAndCancellationNeverExposePartialAttachments() = runBlocking {
        PersistenceFixture().use { f ->
            val initial = create(f)
            val owner = PhotographOwner.Condition(initial.rooms.single().checks.single().id)
            val previous = add(f, initial, owner)
            val allocated = mutableListOf<Photograph>()
            val rejected = f.inspections.importPhotographs(initial.id, owner, 2, null) { photos ->
                allocated += photos
                f.files.copyImage(photos[0].fileId) { ByteArrayInputStream(image()) }.accepted()
                f.files.copyImage(photos[1].fileId) { ByteArrayInputStream("not an image".toByteArray()) }
            }
            assertEquals(DomainResult.Failure(DomainError.InvalidImage), rejected)
            assertEquals(previous, f.inspections.read(initial.id))
            allocated.forEach { assertFalse(f.files.resolve(it.fileId).exists()) }
            try {
                f.inspections.importPhotographs(initial.id, owner, 2, null) { photos ->
                    allocated += photos
                    f.files.copyImage(photos[0].fileId) { ByteArrayInputStream(image()) }.accepted()
                    throw CancellationException("Interrupted copy")
                }
                fail("Cancellation must propagate")
            } catch (_: CancellationException) { /* Expected; existing photos stay committed. */ }
            f.reopen()
            assertEquals(previous, f.inspections.read(initial.id))
            allocated.forEach { assertFalse(f.files.resolve(it.fileId).exists()) }
        }
    }

    @Test fun interruptedJournalAndFailedCleanupRecoverWithoutTouchingOwnedFiles() = runBlocking {
        PersistenceFixture().use { f ->
            val initial = create(f)
            val owner = PhotographOwner.Condition(initial.rooms.single().checks.single().id)
            val previous = add(f, initial, owner)
            val orphan = LocalFileId.new()
            f.database.records().insertFiles(listOf(AttachmentFileEntity(orphan.value, true)))
            f.files.resolve(orphan).mkdir()
            f.files.resolve(orphan).resolve("blocker").writeText("partial")
            f.reopen()
            assertEquals(DomainResult.Failure(DomainError.AttachmentCleanupPending), f.inspections.retryAttachmentCleanup())
            assertEquals(listOf(orphan.value), f.database.records().pendingFiles())
            f.files.resolve(orphan).resolve("blocker").delete()
            f.files.resolve(orphan).delete()
            f.files.resolve(orphan).writeText("partial")
            f.inspections.retryAttachmentCleanup().accepted()
            assertFalse(f.files.resolve(orphan).exists())
            assertTrue(f.database.records().pendingFiles().isEmpty())
            assertEquals(previous, f.inspections.read(initial.id))
            previous.rooms.single().checks.single().conditionPhotos.forEach { assertTrue(f.files.resolve(it.fileId).exists()) }
        }
    }

    @Test fun fullStorageAndUnreadableSourcePreservePriorRecords() = runBlocking {
        PersistenceFixture().use { f ->
            val initial = create(f)
            val owner = PhotographOwner.Condition(initial.rooms.single().checks.single().id)
            val previous = add(f, initial, owner)
            val full = f.inspections.importPhotographs(initial.id, owner, 1, null) { photos ->
                f.files.copyImage(photos.single().fileId) { object : ByteArrayInputStream(image()) {
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = throw IOException("Full", ErrnoException("write", OsConstants.ENOSPC))
                } }
            }
            assertEquals(DomainResult.Failure(DomainError.InsufficientStorage), full)
            val denied = f.inspections.importPhotographs(initial.id, owner, 1, null) { photos ->
                f.files.copyImage(photos.single().fileId) { throw SecurityException("Revoked grant") }
            }
            assertEquals(DomainResult.Failure(DomainError.InvalidImage), denied)
            assertEquals(previous, f.inspections.read(initial.id))
            assertTrue(f.database.records().pendingFiles().isEmpty())
        }
    }

    @Test fun coilHonorsExifRotationAndRequestedDisplaySize() = runBlocking {
        PersistenceFixture().use { f ->
            val original = f.directory.resolve("rotated.jpg").apply { writeBytes(image()) }
            ExifInterface(original.path).apply { setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString()); saveAttributes() }
            val loader = createLocalImageLoader(f.context)
            try {
                val result = loader.execute(ImageRequest.Builder(f.context).data(original).size(40, 60).build()) as SuccessResult
                assertEquals(40, result.image.width)
                assertEquals(60, result.image.height)
                assertTrue(result.isSampled)
                assertTrue(original.exists())
            } finally { loader.shutdown() }
        }
    }
}
