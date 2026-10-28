package com.walkory.app.features.inspections

import com.walkory.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class InspectionDiscoveryTest {
    private fun inspection(id: String, name: String, address: String, role: InspectionRole, state: InspectionState, modified: String, linked: Boolean = false) =
        Inspection(InspectionId(id), InspectionDetails(name, PropertySnapshot(address), LocalDate.of(2020, 1, 1), role),
            if (linked) PropertyId("home") else null, state, emptyList(), Instant.EPOCH, Instant.parse(modified))
    private val first = inspection("a", "Заселение", "Садовая 3", InspectionRole.TENANT, InspectionState.IN_PROGRESS, "2026-01-03T12:00:00Z")
    private val second = inspection("b", "Возврат", "САДОВАЯ 3", InspectionRole.LANDLORD, InspectionState.COMPLETED, "2026-01-01T12:00:00Z", true)
    private val third = inspection("c", "Another home", "Oak 4", InspectionRole.TENANT, InspectionState.COMPLETED, "2026-01-02T12:00:00Z", true)
    private val records = listOf(second, first, third)

    @Test fun searchCombinesAuthoredNameOrSnapshotAddressWithRoleAndStatusAcrossLinkedAndStandaloneRecords() {
        assertEquals(listOf(first, second), InspectionDiscovery(query = "  садОВая  ").select(records))
        assertEquals(listOf(second), InspectionDiscovery(query = "возВРАТ", role = InspectionRole.LANDLORD, status = InspectionState.COMPLETED).select(records))
        assertEquals(listOf(third), InspectionDiscovery(role = InspectionRole.TENANT, status = InspectionState.COMPLETED).select(records))
        assertTrue(InspectionDiscovery(query = "Садовая", role = InspectionRole.LANDLORD, status = InspectionState.IN_PROGRESS).select(records).isEmpty())
        assertTrue(InspectionDiscovery(query = "absent").select(records).isEmpty())
        assertEquals(records, listOf(second, first, third))
    }

    @Test fun orderingUsesModificationNotInspectionDateAndHasStableIdentityTies() {
        assertEquals(listOf(first, third, second), InspectionDiscovery().select(records))
        assertEquals(listOf(second, third, first), InspectionDiscovery(sort = InspectionSort.OLDEST).select(records))
        assertEquals(listOf(third, second, first), InspectionDiscovery(sort = InspectionSort.NAME).select(records))
        val tied = third.copy(id = InspectionId("d"), details = third.details.copy(name = "ANOTHER HOME"))
        assertEquals(listOf(third, tied), InspectionDiscovery(sort = InspectionSort.NAME).select(listOf(tied, third)))
        assertEquals(listOf(third, tied), InspectionDiscovery().select(listOf(tied, third)))
        assertTrue(InspectionDiscovery().select(emptyList()).isEmpty())
    }
}
