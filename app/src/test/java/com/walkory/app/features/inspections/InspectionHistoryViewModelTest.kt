package com.walkory.app.features.inspections

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.walkory.app.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InspectionHistoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { store.clear(); Dispatchers.resetMain() }
    @Test fun failedDeletionNeverExitsButPostCommitCleanupFailureExitsAndRetriesOnlyCleanup() = runTest(dispatcher) {
        val repository = HistoryRepository()
        val handle = SavedStateHandle()
        val model = InspectionHistoryViewModel(repository, handle).also { store.put("history", it) }
        val selected = InspectionId.new()
        repository.outcome = DomainResult.Failure(DomainError.StorageUnavailable)
        model.delete(selected); runCurrent()
        assertNull(model.state.value.deletedId)
        assertEquals(DomainError.StorageUnavailable, model.state.value.deleteError)
        assertTrue(repository.exists)
        repository.outcome = DomainResult.Failure(DomainError.AttachmentCleanupPending)
        model.delete(selected); runCurrent()
        assertFalse(repository.exists)
        assertEquals(selected, model.state.value.deletedId)
        assertEquals(DomainError.AttachmentCleanupPending, model.state.value.deleteError)
        model.acknowledgeDeleted(); model.retryCleanup(); runCurrent()
        assertEquals(2, repository.deletes)
        assertEquals(1, repository.cleanups)
        assertNull(model.state.value.deleteError)
        assertNull(handle.get<String>("deleted_inspection"))
    }
}

private class HistoryRepository : InspectionRepository {
    var outcome: DomainResult<Unit> = DomainResult.Success(Unit)
    var exists = true
    var deletes = 0
    var cleanups = 0
    override fun observeAll() = flowOf(DomainResult.Success(emptyList<Inspection>()))
    override fun observe(id: InspectionId) = flowOf(DomainResult.Success(null))
    override suspend fun create(inspection: Inspection): DomainResult<Inspection> = error("Not used")
    override suspend fun update(id: InspectionId, transform: (Inspection) -> DomainResult<Inspection>): DomainResult<Inspection> = error("Not used")
    override suspend fun delete(id: InspectionId): DomainResult<Unit> {
        deletes++
        if (outcome is DomainResult.Success || (outcome as? DomainResult.Failure)?.error == DomainError.AttachmentCleanupPending) exists = false
        return outcome
    }
    override suspend fun retryAttachmentCleanup(): DomainResult<Unit> { cleanups++; return DomainResult.Success(Unit) }
}
