package com.walkory.app.features.inspections

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.walkory.app.domain.*
import com.walkory.app.ui_kit.SaveStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class InspectionCreationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val clock = Clock.fixed(Instant.parse("2026-01-02T23:30:00Z"), ZoneId.of("Asia/Tokyo"))
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { store.clear(); Dispatchers.resetMain() }
    private fun model(repository: CreationRepository, handle: SavedStateHandle = SavedStateHandle()) =
        InspectionCreationViewModel(repository, CreationTemplates(), handle, clock).also { store.put("model-${handle.hashCode()}", it) }

    @Test fun validationRetainsInputAndCancellationNeverCreatesRecords() = runTest(dispatcher) {
        val repository = CreationRepository()
        val model = model(repository)
        model.begin()
        assertEquals("2026-01-03", model.state.value.draft!!.date)
        model.details("  ", "Sample Street 5", "Any freely editable text")
        model.create()
        assertFalse(model.validateDetails())
        assertEquals("Sample Street 5", model.state.value.draft!!.address)
        model.role(InspectionRole.LANDLORD)
        model.details("Rental inspection", " \t", "")
        assertFalse(model.validateDetails())
        model.details("Rental inspection", "Sample Street 5", "")
        assertTrue(model.validateDetails())
        model.date(LocalDate.of(2030, 2, 28))
        assertFalse(model.validateRooms())
        assertNull(model.addRoom(RoomType.CUSTOM, " \n"))
        val room = model.addRoom(RoomType.CUSTOM, "Office")!!
        model.addCheck(room)
        val check = model.state.value.draft!!.rooms.single().checks.single()
        model.editCheck(room, check.id, " ", "Keep guidance")
        model.create()
        runCurrent()
        assertTrue(repository.rows.value.isEmpty())
        assertEquals("Keep guidance", model.state.value.draft!!.rooms.single().checks.single().guidance)
        model.requestDiscard(); model.cancelDiscard()
        assertEquals("2030-02-28", model.state.value.draft!!.date)
        model.discard()
        assertNull(model.state.value.draft)
        assertTrue(repository.rows.value.isEmpty())
        model.begin()
        assertNull(model.state.value.draft!!.role)
    }

    @Test fun repeatedRoomsRemainIndependentAcrossSourceChangesFailureRestoreAndRetry() = runTest(dispatcher) {
        val repository = CreationRepository()
        val handle = SavedStateHandle()
        val model = model(repository, handle)
        model.begin(); model.role(InspectionRole.TENANT)
        model.details("Move-in", "Sample Street 5", "Free text")
        val first = model.addRoom(RoomType.BEDROOM, "North bedroom")!!
        val second = model.addRoom(RoomType.BEDROOM, "South bedroom")!!
        val custom = model.addRoom(RoomType.CUSTOM, "Work area")!!
        val source = ChecklistTemplate(TemplateId.new(), "Source", TemplateOrigin.Custom, listOf(TemplateCheck("Floor", "Guidance"), TemplateCheck("Door")))
        model.useTemplate(first, source); model.useTemplate(second, source)
        val checks = model.state.value.draft!!.rooms.first().checks
        model.editCheck(first, checks.first().id, "Authored floor", "Authored guidance")
        model.moveCheck(first, checks.last().id, checks.first().id)
        model.useTemplate(second, null)
        model.useTemplate(second, source.copy(checks = listOf(TemplateCheck("New source"))))
        assertEquals(listOf("Door", "Authored floor"), model.state.value.draft!!.rooms.first().checks.map { it.title })
        assertTrue(model.state.value.draft!!.rooms.last().checks.isEmpty())
        assertEquals(custom, model.state.value.draft!!.rooms.last().id)
        repository.fail = true
        model.create(); runCurrent()
        assertEquals(SaveStatus.ERROR, model.state.value.saveStatus)
        assertNull(model.state.value.createdId)
        val restored = model(repository, SavedStateHandle(mapOf("inspection_draft" to handle.get<String>("inspection_draft"))))
        assertEquals(model.state.value.draft, restored.state.value.draft)
        repository.fail = false
        restored.create(); runCurrent()
        val saved = repository.rows.value.single()
        assertEquals(InspectionRole.TENANT, saved.details.role)
        assertEquals("Free text", saved.details.inspector)
        assertEquals(listOf("North bedroom", "South bedroom", "Work area"), saved.rooms.map { it.name })
        assertEquals(listOf("Door", "Authored floor"), saved.rooms.first().checks.map { it.title })
        assertEquals(listOf("New source"), saved.rooms[1].checks.map { it.title })
        val all = saved.rooms.flatMap { it.checks }
        assertEquals(all.size, all.map { it.id }.toSet().size)
        all.forEach { assertEquals(CheckState.UNCHECKED, it.state); assertNull(it.comment); assertTrue(it.conditionPhotos.isEmpty()); assertTrue(it.issues.isEmpty()) }
        assertEquals(saved.id.value, restored.state.value.createdId)
        assertNull(restored.state.value.draft)
    }

    @Test fun interruptedSuccessfulCommitReconcilesStableIdentityInsteadOfCreatingDuplicate() = runTest(dispatcher) {
        val repository = CreationRepository()
        val handle = SavedStateHandle()
        val model = model(repository, handle)
        model.begin(); model.role(InspectionRole.LANDLORD); model.details("Move-out", "Sample Street 6", "")
        model.addRoom(RoomType.CUSTOM, "Empty room")
        val retained = handle.get<String>("inspection_draft")!!
        model.create(); runCurrent()
        val saved = repository.rows.value.single()
        val restored = model(repository, SavedStateHandle(mapOf("inspection_draft" to retained)))
        restored.create(); runCurrent()
        assertEquals(listOf(saved), repository.rows.value)
        assertEquals(1, repository.creates)
        assertNull(saved.details.inspector)
        assertEquals(saved.id.value, restored.state.value.createdId)
    }
}

private class CreationTemplates : TemplateRepository {
    override fun observeAll(language: AppLanguage) = MutableStateFlow<DomainResult<List<ChecklistTemplate>>>(DomainResult.Success(emptyList()))
    override suspend fun saveCustom(template: ChecklistTemplate): DomainResult<ChecklistTemplate> = error("Not part of creation")
    override suspend fun deleteCustom(id: TemplateId): DomainResult<Unit> = error("Not part of creation")
}
private class CreationRepository : InspectionRepository {
    val rows = MutableStateFlow<List<Inspection>>(emptyList())
    var fail = false
    var creates = 0
    override fun observeAll() = rows.map { DomainResult.Success(it) }
    override fun observe(id: InspectionId) = rows.map { DomainResult.Success(it.find { row -> row.id == id }) }
    override suspend fun create(inspection: Inspection): DomainResult<Inspection> {
        if (fail) return DomainResult.Failure(DomainError.InsufficientStorage)
        creates++
        rows.value += inspection
        return DomainResult.Success(inspection)
    }
    override suspend fun update(id: InspectionId, transform: (Inspection) -> DomainResult<Inspection>): DomainResult<Inspection> = error("Not part of creation")
    override suspend fun delete(id: InspectionId): DomainResult<Unit> = error("Not part of creation")
    override suspend fun retryAttachmentCleanup(): DomainResult<Unit> = error("Not part of creation")
}
