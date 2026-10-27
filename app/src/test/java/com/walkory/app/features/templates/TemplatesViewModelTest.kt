package com.walkory.app.features.templates

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.walkory.app.domain.*
import com.walkory.app.ui_kit.SaveStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TemplatesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }

    @Test fun failedSaveAndLaterCatalogEmissionRetainDraftUntilExplicitRetryCommits() = runTest(dispatcher) {
        val source = ChecklistTemplate(TemplateId("custom"), "Original", TemplateOrigin.Custom, listOf(TemplateCheck("Floor", "Original guidance")))
        val repository = EditorRepository(source)
        val handle = SavedStateHandle()
        val model = TemplatesViewModel(repository, handle).also { store.put("editor", it) }
        runCurrent()
        model.beginEdit(source)
        model.rename("Authored name")
        val key = model.state.value.draft!!.checks.single().key
        model.editItem(key, "Edited floor", "Authored guidance")
        repository.failWrites = true
        model.save()
        assertEquals(SaveStatus.SAVING, model.state.value.saveStatus)
        runCurrent()
        assertEquals(SaveStatus.ERROR, model.state.value.saveStatus)
        assertFalse(model.state.value.exitEditor)
        assertEquals(source, repository.saved)
        repository.rows.value = DomainResult.Success(listOf(source.copy(name = "Later durable snapshot")))
        runCurrent()
        assertEquals("Authored name", model.state.value.draft!!.name)
        assertEquals("Edited floor", model.state.value.draft!!.checks.single().title)
        assertEquals("Authored guidance", model.state.value.draft!!.checks.single().guidance)
        val restored = TemplatesViewModel(repository, SavedStateHandle(mapOf("draft" to handle.get<String>("draft"), "baseline" to handle.get<String>("baseline"))))
            .also { store.put("restored", it) }
        runCurrent()
        assertEquals(model.state.value.draft, restored.state.value.draft)
        restored.requestClose()
        assertTrue(restored.state.value.confirmDiscard)
        restored.cancelDiscard()
        repository.failWrites = false
        restored.save()
        runCurrent()
        assertEquals(source.id, repository.saved.id)
        assertEquals("Authored name", repository.saved.name)
        assertEquals(listOf(TemplateCheck("Edited floor", "Authored guidance")), repository.saved.checks)
        assertTrue(restored.state.value.exitEditor)
        assertNull(restored.state.value.draft)
    }

    @Test fun blankTitlesAndEmptyListCannotReplaceDurableTemplateAndDiscardCancelKeepsInput() = runTest(dispatcher) {
        val source = ChecklistTemplate(TemplateId("custom"), "Original", TemplateOrigin.Custom, listOf(TemplateCheck("Floor", "Guidance")))
        val repository = EditorRepository(source)
        val model = TemplatesViewModel(repository, SavedStateHandle()).also { store.put("editor", it) }
        runCurrent()
        model.beginEdit(source)
        val key = model.state.value.draft!!.checks.single().key
        model.editItem(key, " \t", "Preserved input")
        model.save()
        runCurrent()
        assertTrue(model.state.value.validation)
        assertEquals(0, repository.writes)
        assertEquals("Preserved input", model.state.value.draft!!.checks.single().guidance)
        model.requestClose()
        model.cancelDiscard()
        assertEquals(" \t", model.state.value.draft!!.checks.single().title)
        model.removeItem(key)
        model.save()
        runCurrent()
        assertEquals(0, repository.writes)
        assertEquals(source, repository.saved)
        model.closeEditor()
        assertNull(model.state.value.draft)
        assertTrue(model.state.value.exitEditor)
    }
}

private class EditorRepository(initial: ChecklistTemplate) : TemplateRepository {
    val rows = MutableStateFlow<DomainResult<List<ChecklistTemplate>>>(DomainResult.Success(listOf(initial)))
    var saved = initial
    var failWrites = false
    var writes = 0
    override fun observeAll(language: AppLanguage) = rows
    override suspend fun saveCustom(template: ChecklistTemplate): DomainResult<ChecklistTemplate> {
        writes++
        if (failWrites) return DomainResult.Failure(DomainError.InsufficientStorage)
        saved = template
        rows.value = DomainResult.Success(listOf(template))
        return DomainResult.Success(template)
    }
    override suspend fun deleteCustom(id: TemplateId): DomainResult<Unit> = error("Deletion is not exercised by this editor scenario")
}
