package com.walkory.app.features.media

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModelStore
import com.walkory.app.R
import com.walkory.app.data.local.*
import com.walkory.app.domain.*
import com.walkory.app.features.inspections.InspectionWorkflowViewModel
import com.walkory.app.platform.images.PhotographMedia
import com.walkory.app.ui_kit.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PhotographViewerTest {
    @get:Rule val compose = createComposeRule()
    private val fixture = PersistenceFixture()
    private val store = ViewModelStore()
    private var keyboard: SoftwareKeyboardController? = null
    @After fun close() { compose.runOnIdle { store.clear() }; fixture.close() }

    @Test fun pagingZoomCaptionDraftRestorationAndSelectiveRemovalStayWithinOwner() {
        val operations = InspectionOperations()
        val room = TemplateOperations.prepareRoom(RoomType.KITCHEN, "Kitchen", ChecklistTemplate(TemplateId.new(), "List", TemplateOrigin.Custom, listOf(TemplateCheck("Floor")))).accepted()
        val initial = operations.create("Inspection", "Sample Street 8", InspectionRole.TENANT).accepted().copy(rooms = listOf(room))
        val owner = PhotographOwner.Condition(room.checks.single().id)
        val bytes = ByteArrayOutputStream().use { out ->
            val bitmap = Bitmap.createBitmap(32, 48, Bitmap.Config.ARGB_8888)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); bitmap.recycle(); out.toByteArray()
        }
        val record = runBlocking {
            fixture.inspections.create(initial).accepted()
            fixture.inspections.importPhotographs(initial.id, owner, 2, null) { photos ->
                photos.forEach { fixture.files.copyImage(it.fileId) { ByteArrayInputStream(bytes) }.accepted() }
                DomainResult.Success(Unit)
            }.accepted()
        }
        val photos = record.rooms.single().checks.single().conditionPhotos
        val workflow = InspectionWorkflowViewModel(fixture.inspections, fixture.templates)
        val media = PhotographViewModel(PhotographMedia(fixture.context, fixture.inspections, fixture.files), fixture.inspections, androidx.lifecycle.SavedStateHandle())
        store.put("workflow", workflow); store.put("media", media)
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            keyboard = LocalSoftwareKeyboardController.current
            val state by workflow.state.collectAsState()
            val current = state.records[record.id]
            WalkoryTheme {
                Column {
                    if (current != null) PhotographCollection(record.id, owner, current.inspection.rooms.single().checks.single().conditionPhotos, media,
                        { selectedOwner, photo, caption -> workflow.caption(record.id, selectedOwner, photo, caption) },
                        { selectedOwner, photo -> workflow.removePhoto(record.id, selectedOwner, photo) }, current.saveStatus, { workflow.retry(record.id) })
                }
            }
        }
        compose.waitUntil(10_000) { workflow.state.value.records.containsKey(record.id) }
        compose.onNodeWithTag("photo_${photos.first().id.value}").performClick()
        compose.onNodeWithTag("photo_next").performClick()
        compose.onNodeWithText(fixture.context.getString(R.string.inspections_media_position, 2, 2)).assertIsDisplayed()
        compose.onNodeWithTag("photo_zoom_${photos.last().id.value}").assertIsDisplayed().performTouchInput {
            pinch(Offset(center.x - width * .1f, center.y), Offset(center.x + width * .1f, center.y),
                Offset(center.x - width * .4f, center.y), Offset(center.x + width * .4f, center.y))
        }
        compose.onNodeWithTag("photo_reset_zoom").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(fixture.context.getString(R.string.inspections_media_position, 2, 2)).assertIsDisplayed()
        compose.onNodeWithTag("photo_reset_zoom").performClick()
        compose.onNodeWithTag("photo_caption").performClick()
        compose.onNodeWithTag("photo_caption_input").performTextReplacement("Локальная подпись")
        compose.runOnIdle { keyboard?.hide() }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("photo_caption_input").assertTextContains("Локальная подпись")
        compose.onNodeWithTag("photo_caption_save").performClick()
        compose.waitUntil(10_000) { workflow.state.value.records[record.id]?.saveStatus == SaveStatus.SAVED }
        val captioned = runBlocking { fixture.inspections.read(record.id)!! }.rooms.single().checks.single().conditionPhotos
        assertNull(captioned.first().caption)
        assertEquals("Локальная подпись", captioned.last().caption)
        compose.onNodeWithTag("photo_delete").performClick()
        compose.onNodeWithText(fixture.context.getString(R.string.common_cancel)).performClick()
        assertTrue(fixture.files.resolve(photos.last().fileId).exists())
        compose.onNodeWithTag("photo_delete").performClick()
        compose.onNode(hasText(fixture.context.getString(R.string.inspections_media_delete)) and hasClickAction() and
            hasAnyAncestor(isDialog()) and !hasTestTag("photo_delete")).performClick()
        compose.waitUntil(10_000) { !fixture.files.resolve(photos.last().fileId).exists() }
        compose.onNodeWithText(fixture.context.getString(R.string.inspections_media_position, 1, 1)).assertIsDisplayed()
        assertTrue(fixture.files.resolve(photos.first().fileId).exists())
        assertEquals(listOf(photos.first()), runBlocking { fixture.inspections.read(record.id)!! }.rooms.single().checks.single().conditionPhotos)
    }
}
