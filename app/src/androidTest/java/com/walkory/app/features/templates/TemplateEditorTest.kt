package com.walkory.app.features.templates

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.espresso.Espresso
import com.walkory.app.R
import com.walkory.app.data.catalog.CatalogTemplateRepository
import com.walkory.app.data.catalog.ResourceTemplateCatalog
import com.walkory.app.data.local.PersistenceFixture
import com.walkory.app.data.local.PersistenceRecords
import com.walkory.app.data.local.accepted
import com.walkory.app.data.local.readAll
import com.walkory.app.domain.*
import com.walkory.app.ui_kit.WalkoryTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TemplateEditorTest {
    @get:Rule val compose = createComposeRule()
    private val fixture = PersistenceFixture()
    private val store = ViewModelStore()
    private lateinit var model: TemplatesViewModel
    private fun text(id: Int) = fixture.context.getString(id)
    private fun start() {
        val repository = CatalogTemplateRepository(fixture.templates, ResourceTemplateCatalog(fixture.context))
        model = TemplatesViewModel(repository, SavedStateHandle())
        store.put("templates", model)
    }
    @After fun close() {
        compose.runOnIdle { store.clear() }
        fixture.close()
    }
    private fun scroll(tag: String) {
        compose.onNodeWithTag("template_editor_list").performScrollToNode(hasTestTag(tag))
    }

    @Test fun creationValidatesWithoutLosingInputAndReorderPersistsAfterReopen() {
        start()
        compose.setContent { WalkoryTheme { TemplatesWorkspace(model, false) } }
        compose.onNodeWithTag("templates_new").performClick()
        compose.onNodeWithTag("template_name").performTextReplacement("Authored macro")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("template_save").performClick()
        compose.onNodeWithText(text(R.string.templates_at_least_one)).assertIsDisplayed()
        compose.onNodeWithTag("template_add_item").performClick()
        scroll("template_item_guidance_0")
        compose.onNodeWithTag("template_item_guidance_0").performTextReplacement("Keep this guidance")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("template_save").performClick()
        scroll("template_item_title_0")
        compose.onNodeWithTag("template_item_title_0").assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Error))
        compose.onNodeWithTag("template_item_title_0").performTextReplacement("First check")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("template_add_item").performClick()
        scroll("template_item_title_1")
        compose.onNodeWithTag("template_item_title_1").performTextReplacement("Second check")
        Espresso.closeSoftKeyboard()
        scroll("template_up_1")
        compose.onNodeWithTag("template_up_1").performClick()
        compose.onNodeWithTag("template_save").performClick()
        compose.onNodeWithTag("template_detail").assertIsDisplayed()
        compose.runOnIdle { store.clear() }
        runBlocking {
            fixture.reopen()
            val saved = fixture.templates.readAll().single()
            assertEquals("Authored macro", saved.name)
            assertEquals(listOf(TemplateCheck("Second check"), TemplateCheck("First check", "Keep this guidance")), saved.checks)
        }
    }

    @Test fun discardAndDestructiveCancellationLeaveDurableTemplateUnchanged() {
        val source = PersistenceRecords.template()
        runBlocking { fixture.templates.saveCustom(source).accepted() }
        start()
        compose.setContent { WalkoryTheme { TemplatesWorkspace(model, false) } }
        compose.onNodeWithTag("templates_filter_OWN").performClick()
        compose.onNodeWithTag("template_${source.id.value}").performClick()
        compose.onNodeWithTag("templates_edit").performClick()
        compose.onNodeWithTag("template_name").performTextReplacement("Unsaved name")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("template_discard").performClick()
        compose.onNodeWithText(text(R.string.common_cancel)).performClick()
        compose.onNodeWithTag("template_name").assertTextContains("Unsaved name")
        compose.onNodeWithTag("template_discard").performClick()
        compose.onAllNodesWithText(text(R.string.templates_discard)).onLast().performClick()
        compose.onNodeWithTag("template_detail").assertIsDisplayed()
        compose.onNodeWithTag("templates_delete").performClick()
        compose.onNodeWithText(text(R.string.common_cancel)).performClick()
        runBlocking { assertEquals(listOf(source), fixture.templates.readAll()) }
        compose.onNodeWithTag("templates_delete").performClick()
        compose.onAllNodesWithText(text(R.string.templates_delete)).onLast().performClick()
        compose.onNodeWithTag("templates_catalog").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.templates_empty_title)).assertIsDisplayed()
        runBlocking { assertEquals(emptyList<ChecklistTemplate>(), fixture.templates.readAll()) }
    }

    @Test fun builtInIsReadOnlyAndCopyDraftSurvivesSavedStateRestorationAndItemDeletionCancel() {
        start()
        val restoration = StateRestorationTester(compose)
        restoration.setContent { WalkoryTheme { TemplatesWorkspace(model, false) } }
        val source = ResourceTemplateCatalog(fixture.context).templates(AppLanguage.ENGLISH).first()
        compose.onNodeWithTag("template_${source.id.value}").performClick()
        compose.onNodeWithTag("templates_edit").assertDoesNotExist()
        compose.onNodeWithTag("templates_delete").assertDoesNotExist()
        compose.onNodeWithTag("templates_copy").performClick()
        compose.onNodeWithTag("template_name").performTextReplacement("Independent authored copy")
        Espresso.closeSoftKeyboard()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("template_editor").assertIsDisplayed()
        scroll("template_name")
        compose.onNodeWithTag("template_name").assertTextContains("Independent authored copy")
        scroll("template_remove_0")
        compose.onNodeWithTag("template_remove_0").performClick()
        compose.onNodeWithText(text(R.string.common_cancel)).performClick()
        compose.onNodeWithTag("template_save").performClick()
        compose.onNodeWithTag("template_detail").assertIsDisplayed()
        runBlocking {
            val copied = fixture.templates.readAll().single()
            assertNotEquals(source.id, copied.id)
            assertEquals(TemplateOrigin.Custom, copied.origin)
            assertEquals(source.checks, copied.checks)
        }
    }
    @Test fun fullShellRestoresEditorRouteAndDraftAcrossTabSwitchAndSavedStateRecreation() {
        val repository = CatalogTemplateRepository(fixture.templates, ResourceTemplateCatalog(fixture.context))
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            WalkoryTheme {
                val templateModel = rememberTemplatesViewModel(repository)
                val navigation = rememberTemplatesNavigation()
                com.walkory.app.app.WalkoryShell(
                    expanded = false,
                    onTemplatesReselect = { navigation.returnToCatalog(templateModel) },
                    inspectionsContent = { _, _ -> androidx.compose.material3.Text("Inspection area fixture") },
                    objectsContent = { _, _ -> androidx.compose.material3.Text("Property area fixture") },
                ) { expanded ->
                    TemplatesWorkspace(templateModel, expanded, navigation)
                }
            }
        }
        compose.onNodeWithTag("nav_TEMPLATES").performClick()
        compose.onNodeWithTag("templates_new").performClick()
        compose.onNodeWithTag("template_name").performTextReplacement("Restorable authored draft")
        Espresso.closeSoftKeyboard()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("template_editor").assertIsDisplayed()
        compose.onNodeWithTag("template_name").assertTextContains("Restorable authored draft")
        compose.onNodeWithTag("nav_OBJECTS").performClick()
        compose.onNodeWithTag("nav_TEMPLATES").performClick()
        compose.onNodeWithTag("template_name").assertTextContains("Restorable authored draft")
        compose.onNodeWithTag("nav_TEMPLATES").performClick()
        compose.onNodeWithText(text(R.string.common_cancel)).performClick()
        compose.onNodeWithTag("template_name").assertTextContains("Restorable authored draft")
        compose.onNodeWithTag("nav_TEMPLATES").performClick()
        compose.onAllNodesWithText(text(R.string.templates_discard)).onLast().performClick()
        compose.onNodeWithTag("templates_catalog").assertIsDisplayed()
        compose.onNodeWithTag("template_editor").assertDoesNotExist()
        val source = ResourceTemplateCatalog(fixture.context).templates(AppLanguage.ENGLISH).first()
        compose.onNodeWithTag("template_${source.id.value}").performClick()
        compose.onNodeWithTag("template_detail").assertIsDisplayed()
        compose.onNodeWithTag("nav_TEMPLATES").performClick()
        compose.onNodeWithTag("templates_catalog").assertIsDisplayed()
        compose.onNodeWithTag("template_detail").assertDoesNotExist()
    }

    @Test fun roleFiltersAllowIndependentSelectionAndRestoreBothSelections() {
        val catalog = ResourceTemplateCatalog(fixture.context).templates(AppLanguage.ENGLISH)
        val tenant = catalog.first { (it.origin as TemplateOrigin.BuiltIn).role == InspectionRole.TENANT }
        val landlord = catalog.first { (it.origin as TemplateOrigin.BuiltIn).role == InspectionRole.LANDLORD }
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            var roles by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(emptyList<InspectionRole>()) }
            WalkoryTheme {
                TemplatesBrowser(TemplatesState(loading = false, templates = listOf(tenant, landlord)), null, false,
                    TemplateFilter.ALL, roles, {}, { role -> roles = if (role in roles) roles - role else roles + role },
                    {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithTag("templates_role_TENANT").performClick()
        compose.onNodeWithTag("template_${tenant.id.value}").assertIsDisplayed()
        compose.onNodeWithTag("template_${landlord.id.value}").assertDoesNotExist()
        compose.onNodeWithTag("templates_role_LANDLORD").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("templates_role_TENANT").assertIsSelected()
        compose.onNodeWithTag("templates_role_LANDLORD").assertIsSelected()
        compose.onNodeWithTag("template_${tenant.id.value}").assertIsDisplayed()
        compose.onNodeWithTag("template_${landlord.id.value}").assertIsDisplayed()
        compose.onNodeWithTag("templates_role_TENANT").performClick()
        compose.onNodeWithTag("template_${tenant.id.value}").assertDoesNotExist()
        compose.onNodeWithTag("template_${landlord.id.value}").assertIsDisplayed()
        compose.onNodeWithTag("templates_role_LANDLORD").performClick()
        compose.onNodeWithTag("template_${tenant.id.value}").assertIsDisplayed()
        compose.onNodeWithTag("template_${landlord.id.value}").assertIsDisplayed()
    }
}
