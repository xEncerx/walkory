package com.walkory.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class DatabaseSchemaTest {
    @get:Rule val schemas = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), WalkoryDatabase::class.java)

    /** Retain this v1 asset when later versions add actual upgrade paths to this validation fixture. */
    @Test fun exportedInitialSchemaReopensWithItsOrderedRelationshipsIntact() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "schema-validation.db"
        try {
            schemas.createDatabase(name, 1).apply {
                execSQL("INSERT INTO properties(id, name, address) VALUES('property', 'Apartment', 'Original address')")
                execSQL("""INSERT INTO inspections(id, name, address, date, role, inspector, propertyId, state, createdSeconds, createdNanos, modifiedSeconds, modifiedNanos)
                    VALUES('inspection', 'Inspection', 'Snapshot address', '2026-01-02', 'TENANT', NULL, 'property', 'IN_PROGRESS', 10, 123, 20, 456)""")
                execSQL("INSERT INTO rooms(id, inspectionId, type, name, position) VALUES('room', 'inspection', 'CUSTOM', 'Room', 0)")
                execSQL("INSERT INTO checks(id, roomId, title, guidance, state, comment, position) VALUES('later', 'room', 'Later', NULL, 'NOT_APPLICABLE', NULL, 1)")
                execSQL("INSERT INTO checks(id, roomId, title, guidance, state, comment, position) VALUES('first', 'room', 'First', 'Guidance', 'UNCHECKED', NULL, 0)")
                close()
            }
            schemas.runMigrationsAndValidate(name, 1, true).close()
            val database = WalkoryDatabase.open(context, name)
            try {
                val restored = database.records().inspection("inspection")!!.toDomain()
                assertEquals(listOf("First", "Later"), restored.rooms.single().checks.map { it.title })
                assertEquals("Snapshot address", restored.details.property.address)
                assertEquals(456, restored.modifiedAt.nano)
                database.records().deleteProperty("property")
                assertEquals(null, database.records().inspection("inspection")!!.record.propertyId)
            } finally { database.close() }
        } finally { context.deleteDatabase(name) }
    }
}
