package com.walkory.app.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.walkory.app.data.catalog.CatalogTemplateRepository
import com.walkory.app.data.catalog.ResourceTemplateCatalog
import com.walkory.app.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TemplateCatalogTest {
    @Test fun catalogCoversRoomTypesAndRolesWithStableLocalizedIdentitiesAndCompleteKitchenVariants() = runBlocking {
        PersistenceFixture().use { fixture ->
            val catalog = ResourceTemplateCatalog(fixture.context)
            val english = catalog.templates(AppLanguage.ENGLISH)
            val russian = catalog.templates(AppLanguage.RUSSIAN)
            assertEquals(28, english.size)
            assertEquals(english.map { it.id }, russian.map { it.id })
            assertEquals(28, english.map { it.id }.toSet().size)
            val expectedCounts = mapOf(RoomType.KITCHEN to listOf(12, 13), RoomType.BEDROOM to listOf(9),
                RoomType.LIVING_ROOM to listOf(10), RoomType.COMBINED_BATHROOM to listOf(11), RoomType.BATHROOM to listOf(10),
                RoomType.TOILET to listOf(8), RoomType.ENTRANCE_HALL to listOf(9), RoomType.BALCONY to listOf(7),
                RoomType.WALK_IN_WARDROBE to listOf(7), RoomType.STORAGE_ROOM to listOf(6), RoomType.STUDY to listOf(9),
                RoomType.LAUNDRY_UTILITY_ROOM to listOf(9), RoomType.DINING_ROOM to listOf(8))
            for (localized in listOf(english, russian)) for (role in InspectionRole.entries) {
                val selected = localized.filter { (it.origin as TemplateOrigin.BuiltIn).role == role }
                assertEquals(RoomType.entries.toSet() - RoomType.CUSTOM, selected.map { (it.origin as TemplateOrigin.BuiltIn).roomType }.toSet())
                for ((type, counts) in expectedCounts) assertEquals(counts, selected.filter { (it.origin as TemplateOrigin.BuiltIn).roomType == type }.map { it.checks.size })
                val kitchens = selected.filter { (it.origin as TemplateOrigin.BuiltIn).roomType == RoomType.KITCHEN }
                assertEquals(kitchens[0].checks, kitchens[1].checks.dropLast(1))
                assertNotEquals(kitchens[0].id, kitchens[1].id)
            }
            for ((en, ru) in english.zip(russian)) {
                assertNotEquals(en.name, ru.name)
                assertNotEquals(en.checks, ru.checks)
            }
            for (room in expectedCounts.keys) {
                val variants = english.filter { (it.origin as TemplateOrigin.BuiltIn).roomType == room }
                val tenant = variants.first { (it.origin as TemplateOrigin.BuiltIn).role == InspectionRole.TENANT }
                val landlord = variants.first { (it.origin as TemplateOrigin.BuiltIn).role == InspectionRole.LANDLORD }
                assertEquals(tenant.checks.map { it.title }, landlord.checks.map { it.title })
                assertNotEquals(tenant.checks.map { it.guidance }, landlord.checks.map { it.guidance })
            }
            assertEquals(emptyList<Inspection>(), fixture.inspections.observeAll().first().accepted())
            assertEquals(emptyList<ChecklistTemplate>(), fixture.templates.readAll())
        }
    }

    @Test fun localizedCopyRemainsAuthoredAcrossLanguageChangesReorderDeletionAndReopen() = runBlocking {
        PersistenceFixture().use { fixture ->
            fun library() = CatalogTemplateRepository(fixture.templates, ResourceTemplateCatalog(fixture.context))
            val builtIn = library().observeAll(AppLanguage.RUSSIAN).first().accepted().first()
            val custom = TemplateOperations.copy(builtIn, "Authored copy").accepted()
            library().saveCustom(custom).accepted()
            val room = TemplateOperations.prepareRoom(RoomType.KITCHEN, "Room", custom).accepted()
            val inspection = PersistenceRecords.operations.addRoom(PersistenceRecords.operations.create("Inspection", "Sample Street 1", InspectionRole.TENANT).accepted(), room).accepted()
            fixture.inspections.create(inspection).accepted()
            val changed = TemplateOperations.edit(custom, "Edited copy", custom.checks.reversed()).accepted()
            library().saveCustom(changed).accepted()
            fixture.reopen()
            assertEquals(changed, library().observeAll(AppLanguage.ENGLISH).first().accepted().first { it.id == custom.id })
            assertEquals(inspection, fixture.inspections.read(inspection.id))
            assertEquals(DomainResult.Failure(DomainError.ReadOnlyTemplate), library().saveCustom(builtIn))
            assertEquals(DomainResult.Failure(DomainError.ReadOnlyTemplate), library().saveCustom(builtIn.copy(origin = TemplateOrigin.Custom)))
            assertEquals(DomainResult.Failure(DomainError.ReadOnlyTemplate), library().deleteCustom(builtIn.id))
            library().deleteCustom(custom.id).accepted()
            fixture.reopen()
            assertEquals(inspection, fixture.inspections.read(inspection.id))
            assertEquals(emptyList<ChecklistTemplate>(), fixture.templates.readAll())
            assertEquals(builtIn, library().observeAll(AppLanguage.RUSSIAN).first().accepted().first { it.id == builtIn.id })
        }
    }

    @Test fun savingObservedRoomPersistsOnlyPreparedContentAndRejectsEmptyListsWithoutReplacingSavedData() = runBlocking {
        PersistenceFixture().use { fixture ->
            val inspection = PersistenceRecords.inspection()
            fixture.inspections.create(inspection).accepted()
            val saved = fixture.templates.saveRoomAsTemplate(inspection.rooms[0], "Reusable list").accepted()
            assertEquals(inspection.rooms[0].checks.map { TemplateCheck(it.title, it.guidance) }, saved.checks)
            assertEquals(DomainResult.Failure(DomainError.EmptyTemplate), fixture.templates.saveCustom(saved.copy(checks = emptyList())))
            assertEquals(DomainResult.Failure(DomainError.EmptyTemplate), fixture.templates.saveRoomAsTemplate(inspection.rooms[0].copy(checks = emptyList()), "Empty list"))
            fixture.reopen()
            assertEquals(listOf(saved), fixture.templates.readAll())
            assertEquals(inspection, fixture.inspections.read(inspection.id))
            val prepared = TemplateOperations.prepareRoom(RoomType.CUSTOM, "New room", saved).accepted()
            assertTrue(prepared.checks.all { it.state == CheckState.UNCHECKED && it.comment == null && it.conditionPhotos.isEmpty() && it.issues.isEmpty() })
        }
    }
}
