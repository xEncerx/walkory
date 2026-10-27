package com.walkory.app.features.inspections

import androidx.lifecycle.ViewModelStore
import com.walkory.app.domain.*
import com.walkory.app.ui_kit.SaveStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InspectionWorkflowViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { store.clear(); Dispatchers.resetMain() }
    private fun model(repository: WorkflowRepository, templates: WorkflowTemplates = WorkflowTemplates()) =
        InspectionWorkflowViewModel(repository, templates).also { store.put("workflow", it) }

    @Test fun rapidEditsStayResponsiveAndOldCommitsAndFlowEmissionsCannotReplaceNewInput() = runTest(dispatcher) {
        val original = DomainFixtures.withRoom()
        val repository = WorkflowRepository(original)
        val model = model(repository)
        runCurrent()
        val check = original.firstCheck().id
        model.comment(original.id, check, "First")
        runCurrent()
        model.comment(original.id, check, "Newest")
        model.setState(original.id, check, CheckState.NOT_APPLICABLE)
        assertEquals("Newest", model.record(original.id).inspection.firstCheck().comment)
        assertEquals(SaveStatus.SAVING, model.record(original.id).saveStatus)
        advanceTimeBy(100); runCurrent()
        assertEquals("First", repository.durable.firstCheck().comment)
        assertEquals("Newest", model.record(original.id).inspection.firstCheck().comment)
        assertEquals(SaveStatus.SAVING, model.record(original.id).saveStatus)
        advanceUntilIdle()
        assertEquals("Newest", repository.durable.firstCheck().comment)
        assertEquals(CheckState.NOT_APPLICABLE, repository.durable.firstCheck().state)
        assertEquals(SaveStatus.SAVED, model.record(original.id).saveStatus)
        repository.emissions.value = DomainResult.Success(listOf(original))
        runCurrent()
        assertEquals(repository.durable, model.record(original.id).inspection)
        assertEquals(1, repository.maximumConcurrentWrites)
    }

    @Test fun failureKeepsEditableInputAndRetryCommitsEveryLaterEditInOrder() = runTest(dispatcher) {
        val original = DomainFixtures.withRoom()
        val repository = WorkflowRepository(original).apply { fail = true }
        val model = model(repository)
        runCurrent()
        val check = original.firstCheck().id
        model.comment(original.id, check, "Retained")
        advanceUntilIdle()
        assertEquals(SaveStatus.ERROR, model.record(original.id).saveStatus)
        assertNull(repository.durable.firstCheck().comment)
        model.comment(original.id, check, "Edited during failure")
        model.addIssue(original.id, check, "Independent observation")
        val issue = model.record(original.id).inspection.firstCheck().issues.single().id
        model.editIssue(original.id, check, issue, "Revised observation")
        runCurrent()
        assertEquals(SaveStatus.ERROR, model.record(original.id).saveStatus)
        repository.emissions.value = DomainResult.Success(listOf(original))
        runCurrent()
        assertEquals("Edited during failure", model.record(original.id).inspection.firstCheck().comment)
        repository.fail = false
        model.retry(original.id)
        advanceUntilIdle()
        assertEquals("Edited during failure", repository.durable.firstCheck().comment)
        assertEquals(issue, repository.durable.firstCheck().issues.single().id)
        assertEquals("Revised observation", repository.durable.firstCheck().issues.single().description)
        assertEquals(CheckState.HAS_ISSUE, repository.durable.firstCheck().state)
        assertEquals(SaveStatus.SAVED, model.record(original.id).saveStatus)
        val restarted = model(repository)
        runCurrent()
        assertEquals(repository.durable, restarted.record(original.id).inspection)
    }

    @Test fun issueRulesCompletionAndListEditsKeepIndependentEvidenceAndCompletedStatus() = runTest(dispatcher) {
        val original = DomainFixtures.withRoom()
        val repository = WorkflowRepository(original)
        val templates = WorkflowTemplates()
        val model = model(repository, templates)
        runCurrent()
        val id = original.id
        val room = original.rooms.single()
        val check = original.firstCheck().id
        assertFalse(model.addIssue(id, check, " \t"))
        assertEquals(original, model.record(id).inspection)
        model.setState(id, check, CheckState.HAS_ISSUE)
        model.addIssue(id, check, "First issue")
        model.addIssue(id, check, "Second issue")
        val issues = model.record(id).inspection.firstCheck().issues
        assertFalse(model.setState(id, check, CheckState.OK))
        assertEquals(DomainError.IssuesPreventStateChange(2), model.record(id).error)
        assertFalse(model.editIssue(id, check, issues[0].id, ""))
        model.deleteIssue(id, check, issues[0].id)
        assertEquals(listOf(issues[1]), model.record(id).inspection.firstCheck().issues)
        model.deleteIssue(id, check, issues[1].id)
        assertEquals(CheckState.UNCHECKED, model.record(id).inspection.firstCheck().state)
        assertFalse(model.complete(id, false))
        assertEquals(DomainError.UncheckedConfirmationRequired(2), model.record(id).error)
        model.complete(id, true)
        model.comment(id, check, "After completion")
        model.editCheck(id, check, "Authored floor", "Authored guidance")
        model.moveCheck(id, room.id, check, room.checks.last().id)
        model.renameRoom(id, room.id, "Renamed room")
        advanceUntilIdle()
        val saved = repository.durable
        assertEquals(InspectionState.COMPLETED, saved.state)
        assertEquals("After completion", saved.rooms.single().checks.last().comment)
        assertEquals(listOf("Window", "Authored floor"), saved.rooms.single().checks.map { it.title })
        assertEquals("Renamed room", saved.rooms.single().name)
        model.saveTemplate(id, room.id, "Independent template")
        advanceUntilIdle()
        assertEquals(listOf(TemplateCheck("Window", "Check ordinary operation"), TemplateCheck("Authored floor", "Authored guidance")), templates.saved!!.checks)
        model.removeCheck(id, check)
        model.removeRoom(id, room.id)
        advanceUntilIdle()
        assertTrue(repository.durable.rooms.isEmpty())
        assertEquals(InspectionState.COMPLETED, repository.durable.state)
    }

    @Test fun postCommitCleanupRetryDoesNotReplayDeletionAndStillSavesLaterInput() = runTest(dispatcher) {
        val original = DomainFixtures.withRoom()
        val repository = WorkflowRepository(original)
        val model = model(repository)
        runCurrent()
        val check = original.firstCheck().id
        model.addIssue(original.id, check, "Observation")
        advanceUntilIdle()
        val issue = repository.durable.firstCheck().issues.single().id
        repository.postCommitCleanupFailure = true
        model.deleteIssue(original.id, check, issue)
        advanceUntilIdle()
        assertTrue(repository.durable.firstCheck().issues.isEmpty())
        assertEquals(DomainError.AttachmentCleanupPending, model.record(original.id).error)
        model.comment(original.id, check, "Retained after deletion")
        assertEquals(SaveStatus.ERROR, model.record(original.id).saveStatus)
        model.retry(original.id)
        advanceUntilIdle()
        assertEquals(1, repository.cleanupRetries)
        assertEquals(CheckState.UNCHECKED, repository.durable.firstCheck().state)
        assertEquals("Retained after deletion", repository.durable.firstCheck().comment)
        assertEquals(SaveStatus.SAVED, model.record(original.id).saveStatus)
    }
}

private fun InspectionWorkflowViewModel.record(id: InspectionId) = state.value.records.getValue(id)
private class WorkflowTemplates : TemplateRepository {
    var saved: ChecklistTemplate? = null
    override fun observeAll(language: AppLanguage) = flowOf(DomainResult.Success(emptyList<ChecklistTemplate>()))
    override suspend fun saveCustom(template: ChecklistTemplate): DomainResult<ChecklistTemplate> { saved = template; return DomainResult.Success(template) }
    override suspend fun deleteCustom(id: TemplateId): DomainResult<Unit> = error("Not used")
}
private class WorkflowRepository(initial: Inspection) : InspectionRepository {
    var durable = initial
    val emissions = MutableStateFlow<DomainResult<List<Inspection>>>(DomainResult.Success(listOf(initial)))
    var fail = false
    var postCommitCleanupFailure = false
    var cleanupRetries = 0
    private var concurrentWrites = 0
    var maximumConcurrentWrites = 0
    override fun observeAll() = emissions
    override fun observe(id: InspectionId) = emissions.map { result -> result.map { rows -> rows.find { it.id == id } } }
    override suspend fun update(id: InspectionId, transform: (Inspection) -> DomainResult<Inspection>): DomainResult<Inspection> {
        concurrentWrites++
        maximumConcurrentWrites = maxOf(maximumConcurrentWrites, concurrentWrites)
        try {
            delay(100)
            if (fail) return DomainResult.Failure(DomainError.InsufficientStorage)
            val result = transform(durable)
            if (result is DomainResult.Success) { durable = result.value; emissions.value = DomainResult.Success(listOf(durable)) }
            if (result is DomainResult.Success && postCommitCleanupFailure) {
                postCommitCleanupFailure = false
                return DomainResult.Failure(DomainError.AttachmentCleanupPending)
            }
            return result
        } finally { concurrentWrites-- }
    }
    override suspend fun create(inspection: Inspection): DomainResult<Inspection> = error("Not used")
    override suspend fun delete(id: InspectionId): DomainResult<Unit> = error("Not used")
    override suspend fun retryAttachmentCleanup(): DomainResult<Unit> { cleanupRetries++; return DomainResult.Success(Unit) }
}
