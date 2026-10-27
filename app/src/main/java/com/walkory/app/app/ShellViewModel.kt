package com.walkory.app.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.walkory.app.domain.DomainResult
import com.walkory.app.domain.Inspection
import com.walkory.app.domain.InspectionRepository
import com.walkory.app.domain.PropertyCard
import com.walkory.app.domain.PropertyRepository
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
)

@OptIn(ExperimentalCoroutinesApi::class)
internal class ShellViewModel(
    inspections: InspectionRepository,
    properties: PropertyRepository,
) : ViewModel() {
    private val revision = MutableStateFlow(0)

    val state: StateFlow<ShellState> = revision.flatMapLatest {
        combine(
            inspections.observeAll().collectionState(),
            properties.observeAll().collectionState(),
        ) { inspectionState, propertyState ->
            ShellState(inspectionState, propertyState)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShellState())

    fun retry() { revision.update { it + 1 } }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == ShellViewModel::class.java)
            return ShellViewModel(container.inspections, container.properties) as T
        }
    }
}

private fun <T> Flow<DomainResult<List<T>>>.collectionState(): Flow<CollectionState<T>> = map {
    when (it) {
        is DomainResult.Success -> CollectionState.Content(it.value)
        is DomainResult.Failure -> CollectionState.Error
    }
}
