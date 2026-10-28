package com.walkory.app.features.objects

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.walkory.app.domain.*
import com.walkory.app.ui_kit.SaveStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PropertiesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { store.clear(); Dispatchers.resetMain() }

    @Test fun invalidAndFailedEditsKeepSavedCardAndRestoredInputUntilExplicitRetry() = runTest(dispatcher) {
        val original = PropertyCard(PropertyId.new(), "Home", "Original address")
        val repository = PropertyStore(original)
        val handle = SavedStateHandle()
        val model = PropertiesViewModel(repository, handle).also { store.put("original", it) }
        model.begin(original); model.details(" ", "Authored address"); model.save(); runCurrent()
        assertEquals(original, repository.card)
        assertEquals("Authored address", model.state.value.draft!!.address)
        model.details("Renamed", "New address")
        repository.fail = true
        model.save(); runCurrent()
        assertEquals(SaveStatus.ERROR, model.state.value.status)
        assertEquals(original, repository.card)
        assertNull(model.state.value.savedId)
        val restored = PropertiesViewModel(repository, SavedStateHandle(mapOf("property_draft" to handle.get<String>("property_draft"),
            "property_baseline" to handle.get<String>("property_baseline")))).also { store.put("restored", it) }
        assertFalse(restored.requestDiscard())
        restored.cancelDiscard()
        assertEquals("Renamed", restored.state.value.draft!!.name)
        repository.fail = false
        restored.save(); runCurrent()
        assertEquals(original.copy(name = "Renamed", address = "New address"), repository.card)
        assertNull(restored.state.value.draft)
        assertEquals(original.id.value, restored.state.value.savedId)
    }
}

private class PropertyStore(var card: PropertyCard) : PropertyRepository {
    var fail = false
    override fun observeAll() = MutableStateFlow<DomainResult<List<PropertyCard>>>(DomainResult.Success(listOf(card)))
    override suspend fun save(property: PropertyCard): DomainResult<PropertyCard> {
        if (fail) return DomainResult.Failure(DomainError.InsufficientStorage)
        card = property
        return DomainResult.Success(property)
    }
    override suspend fun deleteAndDetach(id: PropertyId): DomainResult<Unit> = error("Not used")
}
