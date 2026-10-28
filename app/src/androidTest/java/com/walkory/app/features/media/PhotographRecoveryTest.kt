package com.walkory.app.features.media

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.walkory.app.data.local.*
import com.walkory.app.domain.*
import com.walkory.app.platform.images.PhotographMedia
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class PhotographRecoveryTest {
    private val operations = InspectionOperations()
    private fun image(): ByteArray = ByteArrayOutputStream().use { out ->
        val bitmap = Bitmap.createBitmap(48, 32, Bitmap.Config.ARGB_8888)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); bitmap.recycle(); out.toByteArray()
    }
    private suspend fun create(f: PersistenceFixture): Inspection {
        val room = TemplateOperations.prepareRoom(RoomType.KITCHEN, "Kitchen", ChecklistTemplate(TemplateId.new(), "List", TemplateOrigin.Custom, listOf(TemplateCheck("Floor")))).accepted()
        return f.inspections.create(operations.create("Inspection", "Sample Street 8", InspectionRole.TENANT).accepted().copy(rooms = listOf(room))).accepted()
    }
    private fun restored(handle: SavedStateHandle) = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })

    @Test fun restoredPickerTargetCreatesOnlyItsPhotoIssueAndOwnsAnIndependentCopy() = runBlocking {
        PersistenceFixture().use { f ->
            val original = create(f)
            val other = create(f)
            val owner = PhotographOwner.IssueEvidence(original.rooms.single().checks.single().id, IssueId.new())
            val source = f.directory.resolve("source.png").apply { writeBytes(image()) }
            val media = PhotographMedia(f.context, f.inspections, f.files)
            val handle = SavedStateHandle()
            val firstStore = ViewModelStore()
            val recoveredStore = ViewModelStore()
            try {
                val recovered = withContext(Dispatchers.Main) {
                    val first = PhotographViewModel(media, f.inspections, handle)
                    firstStore.put("media", first)
                    first.requestSource(original.id, owner, "")
                    first.beginPicker()
                    val saved = restored(handle)
                    firstStore.clear()
                    PhotographViewModel(media, f.inspections, saved).also { recoveredStore.put("media", it) }
                }
                withContext(Dispatchers.Main) { recovered.picked(listOf(android.net.Uri.fromFile(source))) }
                withTimeout(10_000) { recovered.state.first { !it.busy } }
                assertNull(recovered.state.value.error)
                assertFalse(recovered.awaitingResult.value)
                val check = f.inspections.read(original.id)!!.rooms.single().checks.single()
                assertEquals(CheckState.HAS_ISSUE, check.state)
                assertTrue(check.conditionPhotos.isEmpty())
                assertEquals(owner.issueId, check.issues.single().id)
                assertEquals("", check.issues.single().description)
                val photo = check.issues.single().photos.single()
                val bytes = source.readBytes()
                assertTrue(source.delete())
                f.reopen()
                assertArrayEquals(bytes, f.files.resolve(photo.fileId).readBytes())
                assertEquals(photo, f.inspections.read(original.id)!!.rooms.single().checks.single().issues.single().photos.single())
                assertEquals(other, f.inspections.read(other.id))
            } finally { withContext(Dispatchers.Main) { firstStore.clear(); recoveredStore.clear() } }
        }
    }

    @Test fun restoredCameraSuccessAndCancellationKeepConditionOwnershipAndExistingEvidence() = runBlocking {
        PersistenceFixture().use { f ->
            val original = create(f)
            val owner = PhotographOwner.Condition(original.rooms.single().checks.single().id)
            val media = PhotographMedia(f.context, f.inspections, f.files)
            val handle = SavedStateHandle()
            val firstStore = ViewModelStore()
            val recoveredStore = ViewModelStore()
            try {
                val first = withContext(Dispatchers.Main) { PhotographViewModel(media, f.inspections, handle).also {
                    firstStore.put("media", it); it.requestSource(original.id, owner, null); it.chooseCamera()
                } }
                val uri = media.prepareCapture()
                f.context.contentResolver.openOutputStream(uri)!!.use { it.write(image()) }
                val recovered = withContext(Dispatchers.Main) {
                    first.capturePrepared(uri)
                    val saved = restored(handle)
                    firstStore.clear()
                    PhotographViewModel(media, f.inspections, saved).also { recoveredStore.put("media", it); it.captured(true) }
                }
                withTimeout(10_000) { recovered.state.first { !it.busy } }
                assertNull(recovered.state.value.error)
                val committed = f.inspections.read(original.id)!!
                val check = committed.rooms.single().checks.single()
                assertEquals(CheckState.UNCHECKED, check.state)
                assertTrue(check.issues.isEmpty())
                val photo = check.conditionPhotos.single()
                withContext(Dispatchers.Main) { recovered.requestSource(original.id, owner, null); recovered.chooseCamera() }
                val cancelled = media.prepareCapture()
                withContext(Dispatchers.Main) { recovered.capturePrepared(cancelled); recovered.captured(false) }
                assertEquals(committed, f.inspections.read(original.id))
                assertTrue(f.files.resolve(photo.fileId).exists())
                assertFalse(recovered.awaitingResult.value)
            } finally { withContext(Dispatchers.Main) { firstStore.clear(); recoveredStore.clear() }; media.discardCapture() }
        }
    }
}
