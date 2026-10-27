package com.walkory.app.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.walkory.app.domain.AppLanguage
import com.walkory.app.domain.ChecklistTemplate
import com.walkory.app.domain.DomainResult
import com.walkory.app.domain.Inspection
import com.walkory.app.domain.InspectionRepository
import com.walkory.app.domain.PropertyCard
import com.walkory.app.domain.PropertyRepository
import com.walkory.app.domain.TemplateRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

internal sealed interface CollectionState<out T> {
    data object Loading : CollectionState<Nothing>
    data object Error : CollectionState<Nothing>
    data class Content<T>(val items: List<T>) : CollectionState<T>
}

internal data class ShellState(
    val inspections: CollectionState<Inspection> = CollectionState.Loading,
    val objects: CollectionState<PropertyCard> = CollectionState.Loading,
    val templates: CollectionState<ChecklistTemplate> = CollectionState.Loading,
)

@OptIn(ExperimentalCoroutinesApi::class)
internal class ShellViewModel(
    inspections: InspectionRepository,
    properties: PropertyRepository,
    templates: TemplateRepository,
) : ViewModel() {
    private val revision = MutableStateFlow(0)
    private val language = MutableStateFlow(AppLanguage.SYSTEM)

    val state: StateFlow<ShellState> = revision.flatMapLatest {
        combine(
            inspections.observeAll().collectionState(),
            properties.observeAll().collectionState(),
            language.flatMapLatest { templates.observeAll(it) }.collectionState(),
        ) { inspectionState, propertyState, templateState ->
            ShellState(inspectionState, propertyState, templateState)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShellState())

    fun retry() { revision.update { it + 1 } }
    fun setLanguage(value: AppLanguage) { language.value = value }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == ShellViewModel::class.java)
            return ShellViewModel(container.inspections, container.properties, container.templates) as T
        }
    }
}

private fun <T> Flow<DomainResult<List<T>>>.collectionState(): Flow<CollectionState<T>> = map {
    when (it) {
        is DomainResult.Success -> CollectionState.Content(it.value)
        is DomainResult.Failure -> CollectionState.Error
    }
}
