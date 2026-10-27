package com.walkory.app.features.inspections

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.walkory.app.domain.*
import com.walkory.app.ui_kit.SaveStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.LocalDate

@Serializable
internal data class PreparedCheck(val id: String, val title: String = "", val guidance: String = "")

@Serializable
internal data class PreparedRoom(
    val id: String, val type: RoomType, val name: String, val checks: List<PreparedCheck> = emptyList(),
)

@Serializable
internal data class InspectionDraft(
    val id: String, val role: InspectionRole? = null, val name: String = "", val address: String = "",
    val date: String, val inspector: String = "", val rooms: List<PreparedRoom> = emptyList(),
)

internal data class InspectionCreationState(
    val draft: InspectionDraft? = null,
    val validation: Boolean = false,
    val saveStatus: SaveStatus = SaveStatus.IDLE,
    val confirmDiscard: Boolean = false,
    val createdId: String? = null,
    val loadingTemplates: Boolean = true,
    val templateError: Boolean = false,
    val templates: List<ChecklistTemplate> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
internal class InspectionCreationViewModel(
    private val inspections: InspectionRepository,
    private val templates: TemplateRepository,
    private val savedState: SavedStateHandle,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    private val language = MutableStateFlow(AppLanguage.SYSTEM)
    private val revision = MutableStateFlow(0)
    private val mutableState = MutableStateFlow(InspectionCreationState(
        draft = savedState.get<String>("inspection_draft")?.let { Json.decodeFromString<InspectionDraft>(it) },
        createdId = savedState["created_inspection"],
    ))
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(language, revision) { locale, _ -> locale }.flatMapLatest { templates.observeAll(it) }.collect { result ->
                mutableState.update { old -> when (result) {
                    is DomainResult.Success -> old.copy(loadingTemplates = false, templateError = false, templates = result.value)
                    is DomainResult.Failure -> old.copy(loadingTemplates = false, templateError = true)
                } }
            }
        }
    }

    fun setLanguage(value: AppLanguage) { language.value = value }
    fun retryTemplates() { mutableState.update { it.copy(loadingTemplates = true, templateError = false) }; revision.update { it + 1 } }
    fun begin() {
        store(InspectionDraft(InspectionId.new().value, date = LocalDate.now(clock).toString()))
        mutableState.update { it.copy(validation = false, saveStatus = SaveStatus.IDLE, confirmDiscard = false) }
    }
    private fun store(draft: InspectionDraft?) {
        savedState["inspection_draft"] = draft?.let { Json.encodeToString(it) }
        mutableState.update { it.copy(draft = draft) }
    }
    private fun change(transform: (InspectionDraft) -> InspectionDraft) {
        if (state.value.saveStatus == SaveStatus.SAVING) return
        state.value.draft?.let { store(transform(it)) }
        mutableState.update { it.copy(saveStatus = SaveStatus.IDLE) }
    }
    fun role(role: InspectionRole) = change { it.copy(role = role) }
    fun details(name: String, address: String, inspector: String) = change { it.copy(name = name, address = address, inspector = inspector) }
    fun date(date: LocalDate) = change { it.copy(date = date.toString()) }
    fun validateDetails(): Boolean {
        mutableState.update { it.copy(validation = true) }
        return state.value.draft?.let { it.role != null && it.name.isNotBlank() && it.address.isNotBlank() && parsedDate(it) != null } == true
    }
    fun validateRooms(): Boolean {
        mutableState.update { it.copy(validation = true) }
        return state.value.draft?.let { draft -> draft.rooms.isNotEmpty() && draft.rooms.all { room ->
            room.name.isNotBlank() && room.checks.all { it.title.isNotBlank() }
        } } == true
    }
    fun addRoom(type: RoomType, name: String): String? {
        if (name.isBlank() || state.value.saveStatus == SaveStatus.SAVING) return null
        val room = PreparedRoom(RoomId.new().value, type, name)
        change { it.copy(rooms = it.rooms + room) }
        return room.id
    }
    private fun room(id: String, transform: (PreparedRoom) -> PreparedRoom) = change { draft ->
        draft.copy(rooms = draft.rooms.map { if (it.id == id) transform(it) else it })
    }
    fun renameRoom(id: String, name: String) = room(id) { it.copy(name = name) }
    fun removeRoom(id: String) = change { it.copy(rooms = it.rooms.filterNot { room -> room.id == id }) }
    fun useTemplate(id: String, template: ChecklistTemplate?) = room(id) { room ->
        // Copy only the content now: later catalog changes and locale changes cannot rewrite it.
        val prepared = TemplateOperations.prepareRoom(room.type, room.name, template)
        if (prepared is DomainResult.Success) room.copy(checks = prepared.value.checks.map { PreparedCheck(it.id.value, it.title, it.guidance.orEmpty()) })
        else room
    }
    fun addCheck(id: String) = room(id) { it.copy(checks = it.checks + PreparedCheck(CheckId.new().value)) }
    fun editCheck(room: String, id: String, title: String, guidance: String) = room(room) { draft ->
        draft.copy(checks = draft.checks.map { if (it.id == id) it.copy(title = title, guidance = guidance) else it })
    }
    fun removeCheck(room: String, id: String) = room(room) { it.copy(checks = it.checks.filterNot { check -> check.id == id }) }
    fun moveCheck(room: String, id: String, target: String) = room(room) { draft ->
        val from = draft.checks.indexOfFirst { it.id == id }
        val to = draft.checks.indexOfFirst { it.id == target }
        if (from < 0 || to < 0 || from == to) draft
        else draft.copy(checks = draft.checks.toMutableList().apply { add(to, removeAt(from)) })
    }
    fun requestDiscard() { if (state.value.saveStatus != SaveStatus.SAVING) mutableState.update { it.copy(confirmDiscard = true) } }
    fun cancelDiscard() { mutableState.update { it.copy(confirmDiscard = false) } }
    fun discard() {
        if (state.value.saveStatus == SaveStatus.SAVING) return
        store(null)
        mutableState.update { it.copy(confirmDiscard = false, validation = false, saveStatus = SaveStatus.IDLE) }
    }
    private fun parsedDate(draft: InspectionDraft): LocalDate? = try { LocalDate.parse(draft.date) } catch (_: java.time.DateTimeException) { null }

    fun create() {
        if (state.value.saveStatus == SaveStatus.SAVING || !validateDetails() || !validateRooms()) return
        val draft = state.value.draft ?: return
        val prepared = InspectionOperations(clock).create(draft.name, draft.address, draft.role!!, parsedDate(draft)!!, draft.inspector.ifBlank { null })
        if (prepared !is DomainResult.Success) return
        val inspection = prepared.value.copy(id = InspectionId(draft.id), rooms = draft.rooms.map { room ->
            InspectionRoom(RoomId(room.id), room.type, room.name, room.checks.map { InspectionCheck(CheckId(it.id), it.title, it.guidance.ifBlank { null }) })
        })
        mutableState.update { it.copy(saveStatus = SaveStatus.SAVING) }
        viewModelScope.launch {
            // An interrupted successful commit may have left the restored form on screen.
            val existing = inspections.observe(inspection.id).first()
            val result = when (existing) {
                is DomainResult.Failure -> existing
                is DomainResult.Success -> if (existing.value != null) DomainResult.Success(existing.value) else inspections.create(inspection)
            }
            when (result) {
                is DomainResult.Failure -> mutableState.update { it.copy(saveStatus = SaveStatus.ERROR) }
                is DomainResult.Success -> {
                    savedState["created_inspection"] = result.value.id.value
                    store(null)
                    mutableState.update { it.copy(saveStatus = SaveStatus.SAVED, createdId = result.value.id.value) }
                }
            }
        }
    }
    fun acknowledgeCreated() { savedState["created_inspection"] = null; mutableState.update { it.copy(createdId = null) } }
}
