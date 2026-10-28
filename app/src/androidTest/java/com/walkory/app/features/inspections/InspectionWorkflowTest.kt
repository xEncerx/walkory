package com.walkory.app.features.inspections

import androidx.compose.runtime.mutableStateOf
import com.walkory.app.app.CollectionState
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModelStore
import com.walkory.app.R
import com.walkory.app.data.local.*
import com.walkory.app.domain.*
import com.walkory.app.ui_kit.SaveStatus
import com.walkory.app.ui_kit.WalkoryTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class InspectionWorkflowTest {
    @get:Rule val compose = createComposeRule()
    private val fixture = PersistenceFixture()
    private val store = ViewModelStore()
    private lateinit var model: InspectionWorkflowViewModel
    private var keyboard: SoftwareKeyboardController? = null
    private val expanded = mutableStateOf(false)
    private val operations = InspectionOperations()
    private val template = ChecklistTemplate(TemplateId.new(), "Prepared room", TemplateOrigin.Custom,
        listOf(TemplateCheck("Floor", "Look at joints"), TemplateCheck("Window")))
    private val room = TemplateOperations.prepareRoom(RoomType.BEDROOM, "North bedroom", template).accepted()
    private val other = TemplateOperations.prepareRoom(RoomType.BEDROOM, "South bedroom", template).accepted()
    private val original = operations.create("Rental inspection", "Sample Street 7", InspectionRole.TENANT).accepted().copy(rooms = listOf(room, other))
    private fun text(id: Int) = fixture.context.getString(id)
    private fun confirm(id: Int) { compose.onNode(hasText(text(id)) and hasClickAction() and hasAnyAncestor(isDialog())).performClick() }
    private fun start(repository: InspectionRepository = fixture.inspections) {
        runBlocking { fixture.inspections.create(original).accepted() }
        model = InspectionWorkflowViewModel(repository, fixture.templates)
        val photographs = com.walkory.app.features.media.PhotographViewModel(
            com.walkory.app.platform.images.PhotographMedia(fixture.context, fixture.inspections, fixture.files), fixture.inspections, androidx.lifecycle.SavedStateHandle())
        store.put("photographs", photographs)
        store.put("workflow", model)
        compose.setContent {
            keyboard = LocalSoftwareKeyboardController.current
            WalkoryTheme {
                InspectionBrowser(CollectionState.Content(listOf(original)), original.id.value, expanded.value, {}, {}) {
                    InspectionWorkflow(model, original.id, {}, InspectionCreationState(loadingTemplates = false), {}, photographs)
                }
            }
        }
        compose.waitUntil(10_000) { model.state.value.records.containsKey(original.id) }
    }
    private fun click(tag: String) {
        val node = compose.onNodeWithTag(tag)
        if (tag == "result_edit_save" || tag == "result_template_save") node.performClick()
        else node.performScrollTo().performClick()
    }
    private fun fill(tag: String, value: String) { compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value); compose.runOnIdle { keyboard?.hide() } }
    private fun saved() {
        compose.waitUntil(10_000) { model.state.value.records[original.id]?.saveStatus == SaveStatus.SAVED }
        compose.waitForIdle()
    }
    private fun read() = runBlocking { fixture.inspections.read(original.id)!! }
    private fun addIssue(description: String): Issue {
        click("result_add_issue"); fill("result_edit_text", description); click("result_edit_save"); saved()
        return read().rooms.first().checks.first().issues.last()
    }
    @After fun close() { compose.runOnIdle { store.clear() }; fixture.close() }

    @Test fun separateIssuesStateRejectionLastDeletionCompletionAndCompletedEditingSurviveReopen() {
        start()
        click("overview_room_${room.id.value}"); click("result_check_${room.checks.first().id.value}")
        compose.onNodeWithText("Look at joints").assertIsDisplayed()
        CheckState.entries.forEach { state -> click("result_state"); compose.onNodeWithTag("result_state_${state.name}").performClick(); saved() }
        assertNull(read().rooms.first().checks.first().comment)
        assertTrue(read().rooms.first().checks.first().issues.isEmpty())
        click("result_add_issue"); click("result_edit_save")
        compose.onNodeWithText(text(R.string.inspections_empty_issue)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.common_cancel)).performClick()
        val first = addIssue("Visible scratch")
        val second = addIssue("Loose joint")
        fill("result_comment", "General observation"); saved()
        click("result_state"); compose.onNodeWithTag("result_state_OK").performClick()
        compose.onNodeWithText(text(R.string.inspections_issues_prevent_state)).assertIsDisplayed()
        assertEquals(CheckState.HAS_ISSUE, read().rooms.first().checks.first().state)
        click("result_issue_edit_${first.id.value}"); fill("result_edit_text", "Revised scratch"); click("result_edit_save"); saved()
        click("result_issue_remove_${first.id.value}")
        compose.onNodeWithText(text(R.string.common_cancel)).performClick()
        assertEquals(2, read().rooms.first().checks.first().issues.size)
        click("result_issue_remove_${first.id.value}"); confirm(R.string.inspections_delete_issue); saved()
        assertEquals(listOf(second), read().rooms.first().checks.first().issues)
        click("result_issue_remove_${second.id.value}"); confirm(R.string.inspections_delete_issue)
        compose.onNodeWithText(text(R.string.inspections_last_issue_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.common_cancel)).performClick(); saved()
        assertEquals(CheckState.UNCHECKED, read().rooms.first().checks.first().state)
        assertEquals("General observation", read().rooms.first().checks.first().comment)
        click("result_back_list"); compose.onNodeWithTag("navigate_back").performClick()
        click("result_unchecked")
        compose.onNodeWithTag("summary_check_${room.checks.first().id.value}").assertExists()
        compose.onNodeWithTag("navigate_back").performClick()
        click("result_complete")
        compose.onNodeWithText(fixture.context.resources.getQuantityString(R.plurals.inspections_complete_unchecked, 4, 4)).assertIsDisplayed()
        confirm(R.string.inspections_complete); saved()
        click("overview_room_${room.id.value}"); click("result_check_${room.checks.first().id.value}")
        fill("result_comment", "After completion"); saved()
        val final = read()
        assertEquals(InspectionState.COMPLETED, final.state)
        compose.runOnIdle { store.clear() }
        runBlocking { fixture.reopen(); assertEquals(final, fixture.inspections.read(original.id)) }
    }

    @Test fun summariesFilterByRoomAndListChangesPreserveOtherRoomsAndSaveOnlyTemplateContent() {
        start()
        compose.runOnIdle {
            model.addIssue(original.id, room.checks.first().id, "North scratch")
            model.addIssue(original.id, room.checks.first().id, "North joint")
            model.setState(original.id, other.checks.first().id, CheckState.HAS_ISSUE)
        }
        saved()
        click("result_issues")
        compose.onNodeWithText(fixture.context.getString(R.string.inspections_issue_counts, 2, 2)).assertIsDisplayed()
        click("summary_room_filter"); compose.onNodeWithTag("summary_filter_${other.id.value}").performClick()
        compose.onNodeWithText(text(R.string.inspections_no_issue_records)).assertIsDisplayed()
        compose.onNodeWithText("North scratch").assertDoesNotExist()
        compose.onNodeWithTag("navigate_back").performClick()
        click("overview_room_${room.id.value}"); click("result_edit_list"); click("result_up_1"); saved()
        click("result_add_check"); fill("result_edit_text", "Door"); fill("result_edit_guidance", "Ordinary controls"); click("result_edit_save"); saved()
        click("result_rename_room"); fill("result_edit_text", "Authored bedroom"); click("result_edit_save"); saved()
        click("result_save_template"); fill("result_template_name", "Independent prepared list"); click("result_template_save")
        compose.waitUntil(10_000) { model.state.value.records.getValue(original.id).templateStatus == SaveStatus.SAVED }
        confirm(R.string.inspections_room_done)
        val copied = runBlocking { fixture.templates.readAll().single() }
        assertEquals(listOf(TemplateCheck("Window"), TemplateCheck("Floor", "Look at joints"), TemplateCheck("Door", "Ordinary controls")), copied.checks)
        val evidence = read().rooms.first()
        click("result_remove_room"); compose.onNodeWithText(text(R.string.common_cancel)).performClick()
        assertEquals(evidence, read().rooms.first())
        click("result_check_${room.checks.first().id.value}"); click("result_remove_check")
        compose.onNodeWithText(text(R.string.common_cancel)).performClick()
        assertEquals(evidence, read().rooms.first())
        click("result_remove_check"); confirm(R.string.inspections_remove_check); saved()
        assertEquals(listOf("Window", "Door"), read().rooms.first().checks.map { it.title })
        assertEquals(other.copy(checks = listOf(other.checks.first().copy(state = CheckState.HAS_ISSUE), other.checks.last())), read().rooms.last())
        click("result_remove_room"); confirm(R.string.inspections_remove_room); saved()
        compose.onNodeWithTag("inspection_overview").assertIsDisplayed()
        assertEquals(listOf(other.id), read().rooms.map { it.id })
        assertEquals(copied, runBlocking { fixture.templates.readAll().single() })
    }

    @Test fun failedAutosaveRetainsEditableTextAndExplicitRetryPersistsIt() {
        val faulty = FailingWorkflowRepository(fixture.inspections)
        start(faulty)
        click("overview_room_${room.id.value}"); click("result_check_${room.checks.first().id.value}")
        faulty.fail = true
        fill("result_comment", "Retained through failure")
        compose.waitUntil(10_000) { model.state.value.records.getValue(original.id).saveStatus == SaveStatus.ERROR }
        compose.onNodeWithText(text(R.string.inspections_storage_full)).assertIsDisplayed()
        assertNull(read().rooms.first().checks.first().comment)
        fill("result_comment", "Edited while storage unavailable")
        compose.onNodeWithTag("result_comment").assertTextContains("Edited while storage unavailable")
        faulty.fail = false
        compose.onNodeWithText(text(R.string.common_retry)).performClick(); saved()
        assertEquals("Edited while storage unavailable", read().rooms.first().checks.first().comment)
        val final = read()
        compose.runOnIdle { store.clear() }
        runBlocking { fixture.reopen(); assertEquals(final, fixture.inspections.read(original.id)) }
    }

    @Test fun selectedCheckAndIssueDraftSurviveMovingBetweenPaneLayouts() {
        start()
        click("overview_room_${room.id.value}"); click("result_check_${room.checks.first().id.value}")
        click("result_add_issue"); fill("result_edit_text", "Uncommitted issue description")
        compose.runOnIdle { expanded.value = true }
        compose.onNodeWithTag("result_edit_text").assertTextContains("Uncommitted issue description")
        compose.runOnIdle { expanded.value = false }
        compose.onNodeWithTag("result_edit_text").assertTextContains("Uncommitted issue description")
        click("result_edit_save"); saved()
        compose.onNodeWithTag("result_check_detail").assertIsDisplayed()
        assertEquals("Uncommitted issue description", read().rooms.first().checks.first().issues.single().description)
    }
}

private class FailingWorkflowRepository(private val delegate: InspectionRepository) : InspectionRepository by delegate {
    @Volatile var fail = false
    override suspend fun update(id: InspectionId, transform: (Inspection) -> DomainResult<Inspection>): DomainResult<Inspection> =
        if (fail) DomainResult.Failure(DomainError.InsufficientStorage) else delegate.update(id, transform)
}
