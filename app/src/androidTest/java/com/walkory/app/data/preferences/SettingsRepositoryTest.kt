package com.walkory.app.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.walkory.app.data.local.PersistenceFixture
import com.walkory.app.data.local.PersistenceRecords
import com.walkory.app.data.local.accepted
import com.walkory.app.data.local.read
import com.walkory.app.data.local.readAll
import com.walkory.app.domain.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {
    @Test fun allPreferencesSurviveStoreRecreationAndResetCannotDeleteRecords() = runBlocking {
        PersistenceFixture().use { fixture ->
            val card = PersistenceRecords.card()
            val template = PersistenceRecords.template()
            fixture.properties.save(card).accepted()
            fixture.templates.saveCustom(template).accepted()
            val inspection = PersistenceRecords.inspection(card)
            fixture.writePhotos(inspection)
            fixture.inspections.create(inspection).accepted()
            val originals = inspection.rooms.flatMap { room -> room.checks.flatMap { check ->
                check.conditionPhotos + check.issues.flatMap { it.photos }
            } }.associate { it.fileId to fixture.files.resolve(it.fileId).readBytes().toList() }
            val file = File(fixture.directory, "settings.preferences_pb")
            var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            fun repository() = DataStoreSettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
            var repository = repository()
            try {
                val selected = Preferences(AppLanguage.RUSSIAN, AppTheme.DARK, Accent.PURPLE, true,
                    AppTextScale.EXTRA_LARGE, true, true, PdfPhotoQuality.HIGH, "Authored inspector")
                assertEquals(selected, repository.update { selected }.accepted())
                scope.coroutineContext[Job]!!.cancelAndJoin()
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                repository = repository()
                assertEquals(selected, withTimeout(10_000) { repository.observe().first().accepted() })
                PdfPhotoQuality.entries.forEach { quality ->
                    repository.update { it.copy(pdfPhotoQuality = quality) }.accepted()
                    originals.forEach { (id, bytes) -> assertEquals(bytes, fixture.files.resolve(id).readBytes().toList()) }
                }
                repository.reset().accepted()
                fixture.reopen()
                assertEquals(inspection, fixture.inspections.read(inspection.id))
                assertEquals(listOf(card), fixture.properties.observeAll().first().accepted())
                assertEquals(listOf(template), fixture.templates.readAll())
                originals.forEach { (id, bytes) -> assertEquals(bytes, fixture.files.resolve(id).readBytes().toList()) }
            } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
        }
    }

    @Test fun serializedPreferenceUpdatesDoNotOverwriteUnrelatedConfirmedValues() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "settings-${UUID.randomUUID()}.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repository = DataStoreSettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
            val first = async(Dispatchers.IO) { repository.update { it.copy(language = AppLanguage.ENGLISH) }.accepted() }
            val second = async(Dispatchers.IO) { repository.update { it.copy(theme = AppTheme.DARK) }.accepted() }
            first.await(); second.await()
            val saved = repository.observe().first().accepted()
            assertEquals(AppLanguage.ENGLISH, saved.language)
            assertEquals(AppTheme.DARK, saved.theme)
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin(); file.delete() }
    }

    @Test fun actualFilesystemFailureIsReportedWithoutClaimingPreferencesWereSaved() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val blocked = File(context.cacheDir, "blocked-settings-${UUID.randomUUID()}").apply { writeText("Not a directory") }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repository = DataStoreSettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { File(blocked, "settings.preferences_pb") })
            val confirmed = withTimeout(10_000) { repository.observe().first().accepted() }
            assertEquals(DomainResult.Failure(DomainError.StorageUnavailable), repository.update { it.copy(theme = AppTheme.DARK) })
            assertEquals(confirmed, withTimeout(10_000) { repository.observe().first().accepted() })
            assertTrue(blocked.isFile)
            assertEquals("Not a directory", blocked.readText())
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin(); blocked.delete() }
    }
}
