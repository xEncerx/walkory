package com.walkory.app.features.inspections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.walkory.app.domain.*
import com.walkory.app.ui_kit.SaveStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

internal data class InspectionResultState(
    val inspection: Inspection,
    val saveStatus: SaveStatus = SaveStatus.IDLE,
    val error: DomainError? = null,
    val templateStatus: SaveStatus = SaveStatus.IDLE,
)

internal data class InspectionWorkflowState(
    val records: Map<InspectionId, InspectionResultState> = emptyMap(),
    val loading: Boolean = true,
    val readError: Boolean = false,
)

/** Lives in the application UI host, so Back and tab changes do not cancel pending saves. */
internal class InspectionWorkflowViewModel(
    private val repository: InspectionRepository,
    private val templates: TemplateRepository,
    private val operations: InspectionOperations = InspectionOperations(),
) : ViewModel() {
    private class Queue {
        val edits = ArrayDeque<(Inspection) -> DomainResult<Inspection>>()
        var running = false
        var failed = false
        var cleanupPending = false
    }
    private val queues = mutableMapOf<InspectionId, Queue>()
    private val revision = MutableStateFlow(0)
    private val mutableState = MutableStateFlow(InspectionWorkflowState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            revision.collectLatest {
                repository.observeAll().collect { result ->
                    when (result) {
                        is DomainResult.Failure -> mutableState.update { it.copy(loading = false, readError = true) }
                        is DomainResult.Success -> mutableState.update { old ->
                            val records = result.value.associate { inspection ->
                                val current = old.records[inspection.id]
                                val queue = queues[inspection.id]
                                // A commit result is authoritative too. Ignore delayed observations of
                                // pre-commit snapshots, and never replace uncommitted editable input.
                                val keep = current != null && (queue?.edits?.isNotEmpty() == true ||
                                    inspection.modifiedAt < current.inspection.modifiedAt ||
                                    (inspection.modifiedAt == current.inspection.modifiedAt && inspection != current.inspection))
                                inspection.id to if (keep) current else current?.copy(inspection = inspection) ?: InspectionResultState(inspection)
                            }.toMutableMap()
                            old.records.forEach { (id, record) -> if (queues[id]?.edits?.isNotEmpty() == true) records.putIfAbsent(id, record) }
                            old.copy(records = records, loading = false, readError = false)
                        }
                    }
                }
            }
        }
    }

    fun retryRead() { revision.update { it + 1 } }
    private fun update(id: InspectionId, change: (InspectionResultState) -> InspectionResultState) {
        mutableState.update { old -> old.records[id]?.let { old.copy(records = old.records + (id to change(it))) } ?: old }
    }
    fun dismissError(id: InspectionId) = update(id) { it.copy(error = null) }

    private fun edit(id: InspectionId, transform: (Inspection) -> DomainResult<Inspection>): Boolean {
        val current = state.value.records[id] ?: return false
        when (val next = transform(current.inspection)) {
            is DomainResult.Failure -> { update(id) { it.copy(error = next.error) }; return false }
            is DomainResult.Success -> {
                val queue = queues.getOrPut(id) { Queue() }
                queue.edits.addLast(transform)
                update(id) { it.copy(inspection = next.value, saveStatus = if (queue.failed) SaveStatus.ERROR else SaveStatus.SAVING, error = null) }
                drain(id, queue)
                return true
            }
        }
    }

    private fun drain(id: InspectionId, queue: Queue) {
        if (queue.running || queue.failed) return
        queue.running = true
        viewModelScope.launch {
            try {
                if (queue.cleanupPending) {
                    when (repository.retryAttachmentCleanup()) {
                        is DomainResult.Failure -> { queue.failed = true; update(id) { it.copy(saveStatus = SaveStatus.ERROR) }; return@launch }
                        is DomainResult.Success -> queue.cleanupPending = false
                    }
                }
                while (queue.edits.isNotEmpty()) {
                    when (val result = repository.update(id, queue.edits.first())) {
                        is DomainResult.Success -> {
                            queue.edits.removeFirst()
                            update(id) { it.copy(
                                inspection = if (queue.edits.isEmpty()) result.value else it.inspection,
                                saveStatus = if (queue.edits.isEmpty()) SaveStatus.SAVED else SaveStatus.SAVING,
                            ) }
                        }
                        is DomainResult.Failure -> {
                            // Cleanup failure is explicitly post-commit. Do not replay an already
                            // committed deletion (or report it as an uncommitted database write).
                            if (result.error == DomainError.AttachmentCleanupPending) {
                                queue.edits.removeFirst()
                                queue.cleanupPending = true
                            }
                            queue.failed = true
                            update(id) { it.copy(saveStatus = SaveStatus.ERROR, error = result.error) }
                            return@launch
                        }
                    }
                }
                update(id) { it.copy(saveStatus = SaveStatus.SAVED) }
            } finally { queue.running = false }
        }
    }

    fun retry(id: InspectionId) {
        val queue = queues[id] ?: return
        queue.failed = false
        update(id) { it.copy(saveStatus = SaveStatus.SAVING, error = null) }
        drain(id, queue)
    }
    fun setState(id: InspectionId, check: CheckId, state: CheckState) = edit(id) { operations.setCheckState(it, check, state) }
    fun comment(id: InspectionId, check: CheckId, value: String) = edit(id) { inspection ->
        val point = inspection.rooms.asSequence().flatMap { it.checks }.find { it.id == check }
            ?: return@edit DomainResult.Failure(DomainError.NotFound(RecordKind.CHECK))
        operations.editCheck(inspection, check, point.title, point.guidance, value.ifEmpty { null })
    }
    fun editCheck(id: InspectionId, check: CheckId, title: String, guidance: String) = edit(id) { inspection ->
        val point = inspection.rooms.asSequence().flatMap { it.checks }.find { it.id == check }
            ?: return@edit DomainResult.Failure(DomainError.NotFound(RecordKind.CHECK))
        operations.editCheck(inspection, check, title, guidance.ifEmpty { null }, point.comment)
    }
    fun addCheck(id: InspectionId, room: RoomId, title: String, guidance: String): Boolean {
        if (title.isBlank()) return false
        val check = InspectionCheck(CheckId.new(), title, guidance.ifEmpty { null })
        return edit(id) { operations.addCheck(it, room, check) }
    }
    fun removeCheck(id: InspectionId, check: CheckId) = edit(id) { operations.removeCheck(it, check) }
    fun moveCheck(id: InspectionId, room: RoomId, check: CheckId, target: CheckId) = edit(id) { operations.moveCheck(it, room, check, target) }
    fun renameRoom(id: InspectionId, room: RoomId, name: String) = edit(id) { operations.renameRoom(it, room, name) }
    fun removeRoom(id: InspectionId, room: RoomId) = edit(id) { operations.removeRoom(it, room) }
    fun addRoom(id: InspectionId, type: RoomType, name: String, template: ChecklistTemplate?): Boolean {
        val room = TemplateOperations.prepareRoom(type, name, template)
        return when (room) {
            is DomainResult.Failure -> false
            is DomainResult.Success -> edit(id) { operations.addRoom(it, room.value) }
        }
    }
    fun addIssue(id: InspectionId, check: CheckId, description: String): Boolean {
        val issueId = IssueId.new()
        return edit(id) { operations.addIssue(it, check, description, issueId = issueId) }
    }
    fun editIssue(id: InspectionId, check: CheckId, issue: IssueId, description: String) = edit(id) { operations.editIssue(it, check, issue, description) }
    fun deleteIssue(id: InspectionId, check: CheckId, issue: IssueId) = edit(id) { operations.deleteIssue(it, check, issue).map { deletion -> deletion.inspection } }
    fun caption(id: InspectionId, owner: PhotographOwner, photo: PhotographId, value: String) =
        edit(id) { operations.captionPhotograph(it, owner, photo, value) }
    fun removePhoto(id: InspectionId, owner: PhotographOwner, photo: PhotographId) =
        edit(id) { operations.removePhotograph(it, owner, photo) }
    fun complete(id: InspectionId, confirmed: Boolean) = edit(id) { operations.complete(it, confirmed) }

    fun saveTemplate(id: InspectionId, roomId: RoomId, name: String) {
        val record = state.value.records[id] ?: return
        if (record.templateStatus == SaveStatus.SAVING) return
        val room = record.inspection.rooms.find { it.id == roomId } ?: return
        update(id) { it.copy(templateStatus = SaveStatus.SAVING) }
        viewModelScope.launch {
            when (val result = templates.saveRoomAsTemplate(room, name)) {
                is DomainResult.Success -> update(id) { it.copy(templateStatus = SaveStatus.SAVED) }
                is DomainResult.Failure -> update(id) { it.copy(templateStatus = SaveStatus.ERROR, error = result.error) }
            }
        }
    }
    fun clearTemplateStatus(id: InspectionId) = update(id) { it.copy(templateStatus = SaveStatus.IDLE) }
}
