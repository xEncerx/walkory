package com.walkory.app.features.objects

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.walkory.app.R
import com.walkory.app.app.CollectionState
import com.walkory.app.app.WalkoryShell
import com.walkory.app.data.local.*
import com.walkory.app.domain.*
import com.walkory.app.features.inspections.*
import com.walkory.app.features.media.PhotographViewModel
import com.walkory.app.ui_kit.WalkoryTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer

class PropertiesHistoryTest {
    @get:Rule val compose = createComposeRule()
    private val fixture = PersistenceFixture()
    private var keyboard: SoftwareKeyboardController? = null
    private val expanded = mutableStateOf(false)
    @After fun close() { fixture.close() }
    private fun text(id: Int) = fixture.context.getString(id)
    private fun click(tag: String) { compose.onNodeWithTag(tag).performScrollTo().performClick() }
    private fun fill(tag: String, value: String) {
        compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)
        compose.runOnIdle { keyboard?.hide() }
    }
    private fun confirm(id: Int) { compose.onNode(hasText(text(id)) and hasClickAction() and hasAnyAncestor(isDialog())).performClick() }
    private fun records() = runBlocking { fixture.inspections.observeAll().first().accepted() }
    private fun cards() = runBlocking { fixture.properties.observeAll().first().accepted() }
    private fun start(): StateRestorationTester {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val inspectionResult by fixture.inspections.observeAll().collectAsState(initial = DomainResult.Success(emptyList()))
            val propertyResult by fixture.properties.observeAll().collectAsState(initial = DomainResult.Success(emptyList()))
            val inspections = when (val result = inspectionResult) {
                is DomainResult.Success -> CollectionState.Content(result.value)
                is DomainResult.Failure -> CollectionState.Error
            }
            val properties = when (val result = propertyResult) {
                is DomainResult.Success -> CollectionState.Content(result.value)
                is DomainResult.Failure -> CollectionState.Error
            }
            val propertyModel = rememberPropertiesModel(fixture.properties, "properties")
            val picker = rememberPropertiesModel(fixture.properties, "picker")
            val propertyNavigation = rememberPropertiesNavigation()
            val creation = rememberInspectionCreationModel(fixture.inspections, fixture.templates)
            val navigation = rememberInspectionsNavigation()
            val workflow = rememberInspectionWorkflowModel(fixture.inspections, fixture.templates)
            val history = rememberInspectionHistoryModel(fixture.inspections)
            val reports = com.walkory.app.features.reports.rememberReportModel(fixture.reports, fixture.reportSettings)
            val photoFactory = remember { androidx.lifecycle.viewmodel.viewModelFactory {
                initializer { PhotographViewModel(com.walkory.app.platform.images.PhotographMedia(fixture.context, fixture.inspections, fixture.files),
                    fixture.inspections, createSavedStateHandle()) }
            } }
            val photographs: PhotographViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = photoFactory)
            keyboard = LocalSoftwareKeyboardController.current
            WalkoryTheme {
                WalkoryShell(expanded = expanded.value, onObjectsReselect = { propertyNavigation.reselect(propertyModel) },
                    onInspectionsReselect = { navigation.reselect(creation) },
                    settingsContent = { _, _ -> TextFixture() },
                    objectsContent = { wide, showInspections ->
                        PropertiesWorkspace(propertyModel, properties, inspections, wide, propertyNavigation, {},
                            { navigation.select(it, propertyNavigation.selectedId()); showInspections() }, { navigation.begin(creation, it); showInspections() })
                    }, inspectionsContent = { wide, showProperties -> InspectionsWorkspace(creation, inspections, wide, navigation, {}, workflow, photographs,
                        history, properties, picker, reports) { id ->
                            propertyNavigation.root()
                            if ((properties as? CollectionState.Content)?.items?.any { it.id.value == id } == true) propertyNavigation.select(id)
                            showProperties()
                        } }, templatesContent = { TextFixture() })
            }
        }
        return restoration
    }

    @Test fun cardCreationPreselectionEditableSnapshotAndDeletionPreserveLinkedInspectionEvidence() {
        val restoration = start()
        compose.onNodeWithTag("nav_OBJECTS").performClick()
        compose.onNodeWithText(text(R.string.objects_empty_title)).assertIsDisplayed()
        compose.onNodeWithTag("property_new").performClick()
        click("property_save")
        compose.onNodeWithTag("property_name").assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Error))
        fill("property_name", "Rental home"); fill("property_address", "Original Street 4")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("property_address").assertTextContains("Original Street 4")
        click("property_save")
        compose.onNodeWithTag("property_detail").assertIsDisplayed()
        val card = cards().single()
        click("property_inspect")
        compose.onNodeWithTag("inspection_role_LANDLORD").performClick()
        click("inspection_role_next")
        compose.onNodeWithTag("inspection_address").assertTextContains(card.address)
        fill("inspection_name", "Return inspection"); fill("inspection_address", "Authored snapshot 9")
        click("inspection_details_next"); click("inspection_add_room")
        fill("new_room_name", "Empty bedroom"); compose.onNodeWithTag("room_add_confirm").performClick()
        compose.onNodeWithTag("room_done").performClick()
        click("inspection_rooms_next"); click("inspection_create")
        compose.onNodeWithTag("inspection_overview").assertIsDisplayed()
        val created = records().single()
        assertEquals(card.id, created.propertyId)
        assertEquals(InspectionRole.LANDLORD, created.details.role)
        assertEquals("Authored snapshot 9", created.details.property.address)
        val evidence = PersistenceRecords.inspection(card)
        runBlocking { fixture.inspections.create(evidence).accepted(); fixture.writePhotos(evidence) }
        compose.onNodeWithTag("navigate_back").performClick()
        compose.onNodeWithTag("nav_OBJECTS").assertIsSelected()
        compose.onNodeWithTag("property_detail").assertIsDisplayed()
        click("inspection_${created.id.value}")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("navigate_back").performClick()
        compose.onNodeWithTag("nav_OBJECTS").assertIsSelected()
        click("inspection_${created.id.value}")
        compose.onNodeWithTag("nav_OBJECTS").performClick()
        click("property_edit"); fill("property_address", "Changed card address"); click("property_save")
        compose.onNodeWithTag("property_detail").assertIsDisplayed()
        assertEquals(created, records().find { it.id == created.id })
        click("property_delete"); compose.onNodeWithText(text(R.string.common_cancel)).performClick()
        assertEquals(card.id, cards().single().id)
        click("property_delete"); confirm(R.string.objects_delete)
        compose.onNodeWithText(text(R.string.objects_empty_title)).assertIsDisplayed()
        compose.onNodeWithTag("nav_INSPECTIONS").performClick()
        click("result_property")
        compose.onNodeWithTag("property_none").assertIsSelected()
        compose.onNodeWithText(text(R.string.common_cancel)).performClick()
        runBlocking {
            fixture.reopen()
            assertEquals(created.copy(propertyId = null), fixture.inspections.read(created.id))
            assertEquals(evidence.copy(propertyId = null), fixture.inspections.read(evidence.id))
            for (check in evidence.rooms.flatMap { it.checks }) for (photo in check.conditionPhotos + check.issues.flatMap { it.photos }) {
                assertTrue(fixture.files.resolve(photo.fileId).isFile)
            }
        }
    }

    @Test fun combinedDiscoveryRestoresQueryFiltersAndDetailWhileConfirmedDeletionIsSelective() {
        val card = PersistenceRecords.card()
        val original = PersistenceRecords.inspection(card).copy(details = PersistenceRecords.inspection().details.copy(name = "Old return", property = PropertySnapshot("Garden 2")),
            modifiedAt = Instant.parse("2026-01-01T00:00:00Z"))
        val standalone = PersistenceRecords.inspection().copy(details = original.details.copy(name = "Move-in", role = InspectionRole.TENANT),
            state = InspectionState.IN_PROGRESS, modifiedAt = Instant.parse("2026-01-03T00:00:00Z"))
        val other = PersistenceRecords.inspection().copy(details = original.details.copy(name = "Other home", property = PropertySnapshot("Oak 3")),
            modifiedAt = Instant.parse("2026-01-02T00:00:00Z"))
        val template = PersistenceRecords.template()
        runBlocking {
            fixture.properties.save(card).accepted(); fixture.templates.saveCustom(template).accepted()
            listOf(original, standalone, other).forEach { fixture.inspections.create(it).accepted(); fixture.writePhotos(it) }
        }
        val export = java.io.File(fixture.directory, "report.pdf").apply { writeText("Independent report") }
        val restoration = start()
        fill("history_search", "gARden")
        click("history_filters"); compose.onNodeWithTag("history_role").performClick()
        compose.onNodeWithTag("history_role_LANDLORD").performClick()
        compose.onNodeWithTag("history_status").performClick(); compose.onNodeWithTag("history_status_COMPLETED").performClick()
        compose.onNodeWithTag("history_sort").performClick(); compose.onNodeWithTag("history_sort_NAME").performClick()
        compose.onNodeWithTag("history_apply").performClick()
        compose.onNodeWithTag("inspection_${original.id.value}").assertIsDisplayed()
        compose.onNodeWithTag("inspection_${standalone.id.value}").assertDoesNotExist()
        click("inspection_${original.id.value}")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("inspection_overview").assertIsDisplayed()
        compose.runOnIdle { expanded.value = true }
        compose.onNodeWithTag("history_search").assertTextContains("gARden")
        compose.onNodeWithTag("inspection_${original.id.value}").assertIsSelected()
        compose.runOnIdle { expanded.value = false }
        click("result_property"); compose.onNodeWithTag("property_none").performClick()
        compose.waitUntil(10_000) { records().find { it.id == original.id }?.propertyId == null }
        click("result_property"); compose.onNodeWithTag("property_pick_${card.id.value}").performClick()
        compose.waitUntil(10_000) { records().find { it.id == original.id }?.propertyId == card.id }
        assertEquals("Garden 2", records().find { it.id == original.id }!!.details.property.address)
        click("result_delete_inspection"); compose.onNodeWithText(text(R.string.common_cancel)).performClick()
        assertEquals(3, records().size)
        click("result_delete_inspection"); confirm(R.string.inspections_delete)
        compose.onNodeWithText(text(R.string.inspections_no_results)).assertIsDisplayed()
        click("history_reset")
        compose.onNodeWithTag("inspection_${standalone.id.value}").assertIsDisplayed()
        runBlocking {
            fixture.reopen()
            assertNull(fixture.inspections.read(original.id))
            assertEquals(standalone, fixture.inspections.read(standalone.id))
            assertEquals(other, fixture.inspections.read(other.id))
            assertEquals(listOf(card), cards())
            assertEquals(listOf(template), fixture.templates.readAll())
        }
        for (check in original.rooms.flatMap { it.checks }) for (photo in check.conditionPhotos + check.issues.flatMap { it.photos }) assertFalse(fixture.files.resolve(photo.fileId).exists())
        assertEquals("Independent report", export.readText())
    }
}

@Composable private fun TextFixture() { androidx.compose.material3.Text("Template fixture") }
