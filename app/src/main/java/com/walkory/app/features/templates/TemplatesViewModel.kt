package com.walkory.app.features.templates

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.walkory.app.domain.*
import com.walkory.app.ui_kit.SaveStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class TemplateItemDraft(val key: String, val title: String, val guidance: String)

@Serializable
internal data class TemplateDraft(val id: String, val name: String, val checks: List<TemplateItemDraft>, val isNew: Boolean)

internal data class TemplatesState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val templates: List<ChecklistTemplate> = emptyList(),
    val draft: TemplateDraft? = null,
    val validation: Boolean = false,
    val saveStatus: SaveStatus = SaveStatus.IDLE,
    val confirmDiscard: Boolean = false,
    val exitEditor: Boolean = false,
    val deleting: Boolean = false,
    val deleteFailed: Boolean = false,
    val deletedId: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
internal class TemplatesViewModel(
    private val repository: TemplateRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val language = MutableStateFlow(AppLanguage.SYSTEM)
    private val revision = MutableStateFlow(0)
    private val mutableState = MutableStateFlow(TemplatesState(
        draft = savedState.get<String>("draft")?.let { Json.decodeFromString<TemplateDraft>(it) },
    ))
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(language, revision) { locale, _ -> locale }.flatMapLatest { repository.observeAll(it) }.collect { result ->
                mutableState.update { old -> when (result) {
                    is DomainResult.Success -> old.copy(loading = false, loadFailed = false, templates = result.value)
                    is DomainResult.Failure -> old.copy(loading = false, loadFailed = true)
                } }
            }
        }
    }

    fun setLanguage(value: AppLanguage) { language.value = value }
    fun retry() { mutableState.update { it.copy(loading = true, loadFailed = false) }; revision.update { it + 1 } }

    fun beginNew(): String = begin(TemplateDraft(TemplateId.new().value, "", emptyList(), true))

    fun beginEdit(template: ChecklistTemplate): String? {
        if (template.origin != TemplateOrigin.Custom) return null
        return begin(template.toDraft(template.id.value, template.name, false))
    }

    fun beginCopy(template: ChecklistTemplate, name: String): String =
        begin(template.toDraft(TemplateId.new().value, name, true))

    private fun ChecklistTemplate.toDraft(id: String, name: String, isNew: Boolean) = TemplateDraft(
        id, name, checks.map { TemplateItemDraft(CheckId.new().value, it.title, it.guidance.orEmpty()) }, isNew,
    )

    private fun begin(draft: TemplateDraft): String {
        savedState["baseline"] = Json.encodeToString(draft)
        store(draft)
        mutableState.update { it.copy(validation = false, saveStatus = SaveStatus.IDLE, confirmDiscard = false, exitEditor = false) }
        return draft.id
    }

    private fun store(draft: TemplateDraft?) {
        savedState["draft"] = draft?.let { Json.encodeToString(it) }
        mutableState.update { it.copy(draft = draft) }
    }

    private fun change(transform: (TemplateDraft) -> TemplateDraft) {
        if (state.value.saveStatus == SaveStatus.SAVING) return
        val draft = state.value.draft ?: return
        store(transform(draft))
        mutableState.update { it.copy(saveStatus = SaveStatus.IDLE) }
    }

    fun rename(name: String) = change { it.copy(name = name) }
    fun addItem() = change { it.copy(checks = it.checks + TemplateItemDraft(CheckId.new().value, "", "")) }
    fun editItem(key: String, title: String, guidance: String) = change { draft ->
        draft.copy(checks = draft.checks.map { if (it.key == key) it.copy(title = title, guidance = guidance) else it })
    }
    fun removeItem(key: String) = change { it.copy(checks = it.checks.filterNot { item -> item.key == key }) }
    fun moveItem(key: String, targetKey: String) = change { draft ->
        val from = draft.checks.indexOfFirst { it.key == key }
        val to = draft.checks.indexOfFirst { it.key == targetKey }
        if (from < 0 || to < 0 || from == to) draft
        else draft.copy(checks = draft.checks.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun requestClose() {
        if (state.value.saveStatus == SaveStatus.SAVING) return
        val draft = state.value.draft
        val changed = draft != null && Json.encodeToString(draft) != savedState.get<String>("baseline")
        if (changed) mutableState.update { it.copy(confirmDiscard = true) }
        else closeEditor()
    }
    fun cancelDiscard() { mutableState.update { it.copy(confirmDiscard = false) } }
    fun closeEditor() {
        if (state.value.saveStatus == SaveStatus.SAVING) return
        store(null)
        savedState["baseline"] = null
        mutableState.update { it.copy(confirmDiscard = false, exitEditor = true) }
    }
    fun acknowledgeExit() { mutableState.update { it.copy(exitEditor = false) } }

    fun save() {
        val current = state.value
        val draft = current.draft ?: return
        if (current.saveStatus == SaveStatus.SAVING) return
        mutableState.update { it.copy(validation = true) }
        if (draft.name.isBlank() || draft.checks.isEmpty() || draft.checks.any { it.title.isBlank() }) return
        val template = ChecklistTemplate(TemplateId(draft.id), draft.name, TemplateOrigin.Custom,
            draft.checks.map { TemplateCheck(it.title, it.guidance.ifBlank { null }) })
        mutableState.update { it.copy(saveStatus = SaveStatus.SAVING) }
        viewModelScope.launch {
            when (repository.saveCustom(template)) {
                is DomainResult.Success -> {
                    store(null)
                    savedState["baseline"] = null
                    mutableState.update { it.copy(saveStatus = SaveStatus.SAVED, exitEditor = true) }
                }
                is DomainResult.Failure -> mutableState.update { it.copy(saveStatus = SaveStatus.ERROR) }
            }
        }
    }

    fun delete(template: ChecklistTemplate) {
        if (state.value.deleting || template.origin != TemplateOrigin.Custom) return
        mutableState.update { it.copy(deleting = true, deleteFailed = false) }
        viewModelScope.launch {
            when (repository.deleteCustom(template.id)) {
                is DomainResult.Success -> mutableState.update { it.copy(deleting = false, deletedId = template.id.value) }
                is DomainResult.Failure -> mutableState.update { it.copy(deleting = false, deleteFailed = true) }
            }
        }
    }
    fun acknowledgeDeletion() { mutableState.update { it.copy(deletedId = null, deleteFailed = false) } }
}
