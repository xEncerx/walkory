package com.walkory.app.features.inspections

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.walkory.app.R
import com.walkory.app.app.CollectionState
import com.walkory.app.data.catalog.CatalogTemplateRepository
import com.walkory.app.data.catalog.ResourceTemplateCatalog
import com.walkory.app.data.local.*
import com.walkory.app.domain.*
import com.walkory.app.ui_kit.WalkoryTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class InspectionCreationTest {
    @get:Rule val compose = createComposeRule()
    private val fixture = PersistenceFixture()
    private val store = ViewModelStore()
    private lateinit var model: InspectionCreationViewModel
    private var keyboard: SoftwareKeyboardController? = null
    private fun text(id: Int) = fixture.context.getString(id)
    private fun start(): StateRestorationTester {
        model = InspectionCreationViewModel(fixture.inspections,
            CatalogTemplateRepository(fixture.templates, ResourceTemplateCatalog(fixture.context)), SavedStateHandle())
        store.put("creation", model)
        val workflow = InspectionWorkflowViewModel(fixture.inspections, fixture.templates)
        store.put("workflow", workflow)
        val photographs = com.walkory.app.features.media.PhotographViewModel(
            com.walkory.app.platform.images.PhotographMedia(fixture.context, fixture.inspections, fixture.files), fixture.inspections, SavedStateHandle())
        store.put("photographs", photographs)
        val history = InspectionHistoryViewModel(fixture.inspections, SavedStateHandle())
        val picker = com.walkory.app.features.objects.PropertiesViewModel(fixture.properties, SavedStateHandle())
        store.put("history", history); store.put("picker", picker)
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val records by fixture.inspections.observeAll().collectAsState(initial = DomainResult.Success(emptyList()))
            keyboard = LocalSoftwareKeyboardController.current
            val collection = when (val value = records) {
                is DomainResult.Success -> CollectionState.Content(value.value)
                is DomainResult.Failure -> CollectionState.Error
            }
            WalkoryTheme { InspectionsWorkspace(model, collection, false, rememberInspectionsNavigation(), {}, workflow, photographs,
                history, CollectionState.Content(emptyList()), picker, {}) }
        }
        return restoration
    }
    private fun hideKeyboard() { compose.runOnIdle { keyboard?.hide() } }
    private fun click(tag: String) { compose.onNodeWithTag(tag).performScrollTo().performClick() }
    private fun fill(tag: String, value: String) { compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value); hideKeyboard() }
    private fun roomListScroll(tag: String) { compose.onNodeWithTag("room_checks").performScrollToNode(hasTestTag(tag)) }
    @After fun close() { compose.runOnIdle { store.clear() }; fixture.close() }

    @Test fun tenantCreationCopiesRepeatedRoomsAndReopensDurableOverview() = createAndReopen(InspectionRole.TENANT)
    @Test fun landlordCreationCopiesRepeatedRoomsAndReopensDurableOverview() = createAndReopen(InspectionRole.LANDLORD)

    private fun createAndReopen(role: InspectionRole) {
        val source = ChecklistTemplate(TemplateId.new(), "Authored kitchen", TemplateOrigin.Custom,
            listOf(TemplateCheck("Floor", "Visible surfaces"), TemplateCheck("Window", "Ordinary controls")))
        runBlocking { fixture.templates.saveCustom(source).accepted() }
        val restoration = start()
        compose.onNodeWithTag("inspection_new").performClick()
        compose.onNodeWithTag("inspection_role_${role.name}").performClick()
        click("inspection_role_next")
        fill("inspection_address", "Sample Street 8")
        click("inspection_details_next")
        compose.onNodeWithTag("inspection_name").assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Error))
        compose.onNodeWithTag("inspection_address").assertTextContains("Sample Street 8")
        fill("inspection_name", "Rental inspection")
        fill("inspection_inspector", "Freely authored inspector")
        click("inspection_date")
        compose.onNodeWithText(text(R.string.common_cancel)).performClick()
        compose.onNodeWithTag("inspection_address").assertTextContains("Sample Street 8")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("inspection_name").assertTextContains("Rental inspection")
        compose.runOnIdle { model.date(LocalDate.of(2030, 2, 28)) }
        click("inspection_details_next")
        click("inspection_rooms_next")
        compose.onNodeWithText(text(R.string.inspections_room_required)).assertIsDisplayed()
        fun add(name: String) {
            click("inspection_add_room")
            fill("new_room_name", name)
            compose.onNodeWithTag("room_add_confirm").performClick()
            roomListScroll("room_choose_macro")
            compose.onNodeWithTag("room_choose_macro").performClick()
            compose.onNodeWithTag("room_macros").performScrollToNode(hasTestTag("room_macro_${source.id.value}"))
            compose.onNodeWithTag("room_macro_${source.id.value}").performClick()
        }
        add("North kitchen")
        roomListScroll("room_check_title_0")
        compose.onNodeWithTag("room_check_title_0").performTextReplacement("Authored floor")
        hideKeyboard()
        roomListScroll("room_check_up_1")
        compose.onNodeWithTag("room_check_up_1").performClick()
        compose.onNodeWithTag("room_done").performClick()
        add("South kitchen")
        compose.onNodeWithTag("room_done").performClick()
        click("inspection_rooms_next")
        click("inspection_create")
        compose.onNodeWithTag("inspection_overview").assertIsDisplayed()
        val saved = runBlocking { fixture.inspections.observeAll().first().accepted().single() }
        assertEquals(role, saved.details.role)
        assertEquals(LocalDate.of(2030, 2, 28), saved.details.date)
        assertEquals("Freely authored inspector", saved.details.inspector)
        assertEquals(listOf(RoomType.KITCHEN, RoomType.KITCHEN), saved.rooms.map { it.type })
        assertEquals(listOf("Window", "Authored floor"), saved.rooms.first().checks.map { it.title })
        assertEquals(source.checks.map { it.title }, saved.rooms.last().checks.map { it.title })
        assertNotEquals(saved.rooms[0].checks[0].id, saved.rooms[1].checks[1].id)
        saved.rooms.flatMap { it.checks }.forEach {
            assertEquals(CheckState.UNCHECKED, it.state); assertNull(it.comment); assertTrue(it.issues.isEmpty()); assertTrue(it.conditionPhotos.isEmpty())
        }
        compose.onNodeWithTag("overview_room_${saved.rooms.first().id.value}").performScrollTo().performClick()
        compose.onNodeWithText("Authored floor").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { store.clear() }
        runBlocking {
            fixture.templates.deleteCustom(source.id).accepted()
            fixture.reopen()
            assertEquals(saved, fixture.inspections.read(saved.id))
        }
    }

    @Test fun modalAndDiscardCancellationRetainRoleAndDraftWithoutPhantomInspection() {
        val restoration = start()
        compose.onNodeWithTag("inspection_new").performClick()
        compose.onNodeWithTag("inspection_role_LANDLORD").performClick()
        click("inspection_role_next")
        fill("inspection_name", "Unsaved inspection")
        fill("inspection_address", "Sample Street 9")
        click("inspection_details_next")
        click("inspection_add_room")
        fill("new_room_name", "Unconfirmed room")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("new_room_name").assertTextContains("Unconfirmed room")
        compose.onNodeWithTag("room_type").performClick()
        compose.onNodeWithTag("room_type_BEDROOM").performClick()
        compose.onNodeWithTag("new_room_name").assertTextContains("Unconfirmed room")
        compose.onNodeWithText(text(R.string.common_cancel)).performClick()
        compose.onNodeWithTag("navigate_back").performClick()
        compose.onNodeWithTag("inspection_name").assertTextContains("Unsaved inspection")
        compose.onNodeWithTag("navigate_back").performClick()
        compose.onNodeWithTag("inspection_role_LANDLORD").assertIsSelected()
        compose.onNodeWithTag("navigate_back").performClick()
        compose.onNodeWithText(text(R.string.common_cancel)).performClick()
        compose.onNodeWithTag("inspection_role_LANDLORD").assertIsSelected()
        compose.onNodeWithTag("navigate_back").performClick()
        compose.onNodeWithText(text(R.string.inspections_discard)).performClick()
        compose.onNodeWithTag("inspection_list").assertIsDisplayed()
        runBlocking { assertTrue(fixture.inspections.observeAll().first().accepted().isEmpty()) }
    }
}
