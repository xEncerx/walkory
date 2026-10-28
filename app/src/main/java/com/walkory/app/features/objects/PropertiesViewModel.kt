package com.walkory.app.features.objects

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
import com.walkory.app.ui_kit.SaveStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class PropertyDraft(val id: String, val name: String = "", val address: String = "", val isNew: Boolean = true)

internal data class PropertiesState(
    val draft: PropertyDraft? = null,
    val validation: Boolean = false,
    val status: SaveStatus = SaveStatus.IDLE,
    val confirmDiscard: Boolean = false,
    val discardToRoot: Boolean = false,
    val savedId: String? = null,
    val deleting: Boolean = false,
    val deleteFailedId: PropertyId? = null,
    val deletedId: String? = null,
)

internal class PropertiesViewModel(private val repository: PropertyRepository, private val savedState: SavedStateHandle) : ViewModel() {
    private val mutableState = MutableStateFlow(PropertiesState(
        draft = savedState.get<String>("property_draft")?.let { Json.decodeFromString<PropertyDraft>(it) },
        savedId = savedState["property_saved"], deletedId = savedState["property_deleted"],
    ))
    val state = mutableState.asStateFlow()
    fun begin(property: PropertyCard? = null) {
        val draft = property?.let { PropertyDraft(it.id.value, it.name, it.address, false) } ?: PropertyDraft(PropertyId.new().value)
        savedState["property_baseline"] = Json.encodeToString(draft)
        store(draft)
        mutableState.update { it.copy(validation = false, status = SaveStatus.IDLE, confirmDiscard = false) }
    }
    private fun store(draft: PropertyDraft?) {
        savedState["property_draft"] = draft?.let { Json.encodeToString(it) }
        mutableState.update { it.copy(draft = draft) }
    }
    fun details(name: String, address: String) {
        if (state.value.status == SaveStatus.SAVING) return
        state.value.draft?.let { store(it.copy(name = name, address = address)) }
        mutableState.update { it.copy(status = SaveStatus.IDLE) }
    }
    fun requestDiscard(toRoot: Boolean = false): Boolean {
        if (state.value.status == SaveStatus.SAVING) return false
        val unchanged = state.value.draft?.let { Json.encodeToString(it) } == savedState.get<String>("property_baseline")
        if (unchanged) { discard(); return true }
        mutableState.update { it.copy(confirmDiscard = true, discardToRoot = toRoot) }
        return false
    }
    fun cancelDiscard() { mutableState.update { it.copy(confirmDiscard = false) } }
    fun discard() {
        if (state.value.status == SaveStatus.SAVING) return
        store(null)
        mutableState.update { it.copy(confirmDiscard = false, status = SaveStatus.IDLE) }
    }
    fun save() {
        if (state.value.status == SaveStatus.SAVING) return
        val draft = state.value.draft ?: return
        mutableState.update { it.copy(validation = true) }
        if (draft.name.isBlank() || draft.address.isBlank()) return
        mutableState.update { it.copy(status = SaveStatus.SAVING) }
        viewModelScope.launch {
            when (repository.save(PropertyCard(PropertyId(draft.id), draft.name.trim(), draft.address.trim()))) {
                is DomainResult.Failure -> mutableState.update { it.copy(status = SaveStatus.ERROR) }
                is DomainResult.Success -> {
                    savedState["property_saved"] = draft.id
                    store(null)
                    mutableState.update { it.copy(status = SaveStatus.SAVED, savedId = draft.id) }
                }
            }
        }
    }
    fun acknowledgeSaved() { savedState["property_saved"] = null; mutableState.update { it.copy(savedId = null) } }
    fun delete(id: PropertyId) {
        if (state.value.deleting) return
        mutableState.update { it.copy(deleting = true, deleteFailedId = null) }
        viewModelScope.launch {
            when (repository.deleteAndDetach(id)) {
                is DomainResult.Failure -> mutableState.update { it.copy(deleting = false, deleteFailedId = id) }
                is DomainResult.Success -> {
                    savedState["property_deleted"] = id.value
                    mutableState.update { it.copy(deleting = false, deletedId = id.value) }
                }
            }
        }
    }
    fun acknowledgeDeleted() { savedState["property_deleted"] = null; mutableState.update { it.copy(deletedId = null) } }
}

@Composable
internal fun rememberPropertiesModel(repository: PropertyRepository, key: String): PropertiesViewModel {
    val factory = remember(repository) { viewModelFactory { initializer { PropertiesViewModel(repository, createSavedStateHandle()) } } }
    return viewModel(key = key, factory = factory)
}
