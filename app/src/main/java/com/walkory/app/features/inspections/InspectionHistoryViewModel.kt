package com.walkory.app.features.inspections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.walkory.app.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

internal enum class InspectionSort { NEWEST, OLDEST, NAME }

@Serializable
internal data class InspectionDiscovery(
    val query: String = "", val role: InspectionRole? = null, val status: InspectionState? = null,
    val sort: InspectionSort = InspectionSort.NEWEST,
) {
    fun select(records: List<Inspection>): List<Inspection> {
        val search = query.trim()
        val matches = records.filter { inspection ->
            (role == null || inspection.details.role == role) && (status == null || inspection.state == status) &&
                (search.isEmpty() || inspection.details.name.contains(search, ignoreCase = true) ||
                    inspection.details.property.address.contains(search, ignoreCase = true))
        }
        val ordering = when (sort) {
            InspectionSort.NEWEST -> compareByDescending<Inspection> { it.modifiedAt }
            InspectionSort.OLDEST -> compareBy<Inspection> { it.modifiedAt }
            InspectionSort.NAME -> compareBy<Inspection> { it.details.name.lowercase(Locale.ROOT) }
        }.thenBy { it.id.value }
        return matches.sortedWith(ordering)
    }
}

internal data class InspectionHistoryState(
    val discovery: InspectionDiscovery = InspectionDiscovery(), val deletingId: InspectionId? = null,
    val deletedId: InspectionId? = null, val deleteError: DomainError? = null,
)

internal class InspectionHistoryViewModel(private val repository: InspectionRepository, private val savedState: SavedStateHandle) : ViewModel() {
    private val mutableState = MutableStateFlow(InspectionHistoryState(
        discovery = savedState.get<String>("discovery")?.let { Json.decodeFromString<InspectionDiscovery>(it) } ?: InspectionDiscovery(),
        deletedId = savedState.get<String>("deleted_inspection")?.let(::InspectionId),
    ))
    val state = mutableState.asStateFlow()
    fun discover(value: InspectionDiscovery) {
        savedState["discovery"] = Json.encodeToString(value)
        mutableState.update { it.copy(discovery = value) }
    }
    fun delete(id: InspectionId) {
        if (state.value.deletingId != null) return
        mutableState.update { it.copy(deletingId = id, deleteError = null) }
        viewModelScope.launch {
            val result = repository.delete(id)
            val error = (result as? DomainResult.Failure)?.error
            val committed = result is DomainResult.Success || error == DomainError.AttachmentCleanupPending
            if (committed) savedState["deleted_inspection"] = id.value
            mutableState.update { it.copy(deletingId = null, deletedId = if (committed) id else null, deleteError = error) }
        }
    }
    fun acknowledgeDeleted() { savedState["deleted_inspection"] = null; mutableState.update { it.copy(deletedId = null) } }
    fun retryCleanup() {
        viewModelScope.launch {
            val result = repository.retryAttachmentCleanup()
            mutableState.update { it.copy(deleteError = (result as? DomainResult.Failure)?.error) }
        }
    }
}

@Composable
internal fun rememberInspectionHistoryModel(repository: InspectionRepository): InspectionHistoryViewModel {
    val factory = remember(repository) { viewModelFactory { initializer { InspectionHistoryViewModel(repository, createSavedStateHandle()) } } }
    return viewModel(factory = factory)
}
