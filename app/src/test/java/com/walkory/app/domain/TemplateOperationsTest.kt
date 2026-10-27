package com.walkory.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateOperationsTest {
    @Test fun copiesAndPreparedRoomsRemainIndependentAfterSourceEdits() {
        val source = DomainFixtures.template()
        val custom = TemplateOperations.copy(source, "Personal checklist").value()
        val room = TemplateOperations.prepareRoom(RoomType.BEDROOM, "First bedroom", custom).value()
        val other = TemplateOperations.prepareRoom(RoomType.BEDROOM, "Second bedroom", custom).value()
        val edited = TemplateOperations.edit(custom, "Changed", listOf(TemplateCheck("Door"))).value()

        assertNotEquals(source.id, custom.id)
        assertEquals(TemplateOrigin.Custom, custom.origin)
        assertEquals(listOf("Door"), edited.checks.map { it.title })
        assertEquals(listOf("Floor", "Window"), room.checks.map { it.title })
        assertEquals(source.checks.map { it.guidance }, room.checks.map { it.guidance })
        assertEquals(listOf("Floor", "Window"), source.checks.map { it.title })
        assertNotEquals(room.id, other.id)
        assertTrue(room.checks.map { it.id }.intersect(other.checks.map { it.id }.toSet()).isEmpty())
        assertTrue(room.checks.all { it.state == CheckState.UNCHECKED && it.comment == null && it.issues.isEmpty() && it.conditionPhotos.isEmpty() })
    }

    @Test fun savingObservedRoomCopiesOnlyPreparedContent() {
        val operations = DomainFixtures.operations
        val initial = DomainFixtures.withRoom()
        val id = initial.firstCheck().id
        val observed = operations.attachPhotograph(
            operations.addIssue(initial, id, "Surface mark", listOf(DomainFixtures.photo("Detail"))).value(),
            PhotographOwner.Condition(id), DomainFixtures.photo("Overview"),
        ).value()
        val commented = operations.editCheck(observed, id, "Edited floor", "Authored guidance", "General observation").value()
        val saved = TemplateOperations.saveRoom(commented.rooms.first(), "Saved list").value()
        val restored = TemplateOperations.prepareRoom(RoomType.CUSTOM, "New room", saved).value()

        assertEquals(listOf("Edited floor", "Window"), restored.checks.map { it.title })
        assertEquals("Authored guidance", restored.checks.first().guidance)
        assertTrue(restored.checks.all { it.state == CheckState.UNCHECKED && it.comment == null && it.issues.isEmpty() && it.conditionPhotos.isEmpty() })
        assertEquals("General observation", commented.firstCheck().comment)
        assertEquals("Detail", commented.firstCheck().issues.single().photos.single().caption)
    }

    @Test fun customEditingSupportsReorderingWithoutEditingBuiltIns() {
        val source = DomainFixtures.template()
        assertEquals(DomainResult.Failure(DomainError.ReadOnlyTemplate), TemplateOperations.edit(source, "Changed", emptyList()))
        val custom = TemplateOperations.copy(source, "Custom").value()
        val reversed = TemplateOperations.edit(custom, "Renamed", custom.checks.reversed()).value()
        assertEquals(custom.id, reversed.id)
        assertEquals(listOf("Window", "Floor"), reversed.checks.map { it.title })
        assertEquals(listOf("Floor", "Window"), custom.checks.map { it.title })
    }

    @Test fun emptyPreparationAndRepeatedRoomTypesAreSupported() {
        val first = TemplateOperations.prepareRoom(RoomType.BATHROOM, "First bathroom", null).value()
        val second = TemplateOperations.prepareRoom(RoomType.BATHROOM, "Second bathroom", null).value()
        val inspection = DomainFixtures.operations.addRoom(DomainFixtures.withRoom(first), second).value()
        assertEquals(listOf(RoomType.BATHROOM, RoomType.BATHROOM), inspection.rooms.map { it.type })
        assertEquals(listOf("First bathroom", "Second bathroom"), inspection.rooms.map { it.name })
        assertEquals(0, inspection.progress().totalChecks)
        assertEquals(null, inspection.propertyId)
    }

    @Test fun blankTemplateAndRoomNamesProducePresentationErrors() {
        val template = DomainFixtures.template()
        val custom = TemplateOperations.copy(template, "Custom").value()
        val templateError = DomainResult.Failure(DomainError.BlankField(RequiredField.TEMPLATE_NAME))
        assertEquals(templateError, TemplateOperations.copy(template, "  "))
        assertEquals(templateError, TemplateOperations.edit(custom, "", emptyList()))
        assertEquals(templateError, TemplateOperations.saveRoom(DomainFixtures.room(), "\t"))
        assertEquals(DomainResult.Failure(DomainError.BlankField(RequiredField.ROOM_NAME)), TemplateOperations.prepareRoom(RoomType.CUSTOM, "", null))
    }
}
