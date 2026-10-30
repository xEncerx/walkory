package com.walkory.app.data.local

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.walkory.app.domain.*
import com.walkory.app.platform.files.PrivateAttachmentFiles
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

internal class PersistenceFixture : AutoCloseable {
    val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    val name = "persistence-test-${UUID.randomUUID()}.db"
    val directory = File(context.cacheDir, name).apply { check(mkdirs()) }
    val files = PrivateAttachmentFiles(File(directory, "attachments").apply { check(mkdirs()) })
    var database = WalkoryDatabase.open(context, name)
        private set
    lateinit var inspections: RoomInspectionRepository
        private set
    lateinit var properties: RoomPropertyRepository
        private set
    lateinit var templates: RoomTemplateRepository
        private set
    val reportFiles = com.walkory.app.platform.pdf.ReportFiles(File(directory, "staging"), File(directory, "reports"))
    lateinit var reportSnapshots: RoomReportSnapshots
        private set
    lateinit var reports: com.walkory.app.platform.pdf.ITextReportEngine
        private set
    val reportSettings = object : SettingsRepository {
        private val value = kotlinx.coroutines.flow.MutableStateFlow<DomainResult<Preferences>>(DomainResult.Success(Preferences()))
        override fun observe() = value
        override suspend fun update(transform: (Preferences) -> Preferences): DomainResult<Preferences> {
            val next = DomainResult.Success(transform(value.value.accepted()))
            value.value = next
            return next
        }
        override suspend fun reset() = update { Preferences() }
    }
    init { bind() }

    private fun bind() {
        val coordinator = AttachmentStorageCoordinator(database.records(), files)
        inspections = RoomInspectionRepository(database, coordinator)
        reportSnapshots = RoomReportSnapshots(database, coordinator, files, reportFiles)
        reports = com.walkory.app.platform.pdf.ITextReportEngine(context, reportSnapshots, reportFiles, inspections)
        properties = RoomPropertyRepository(database)
        templates = RoomTemplateRepository(database)
    }

    fun reopen() {
        database.close()
        database = WalkoryDatabase.open(context, name)
        bind()
    }

    fun writePhotos(inspection: Inspection) {
        for (room in inspection.rooms) for (check in room.checks) {
            for (photo in check.conditionPhotos) files.resolve(photo.fileId).writeText("condition:${photo.id.value}")
            for (issue in check.issues) for (photo in issue.photos) files.resolve(photo.fileId).writeText("issue:${photo.id.value}")
        }
    }

    override fun close() {
        database.close()
        context.deleteDatabase(name)
        directory.deleteRecursively()
    }
}

internal object PersistenceRecords {
    val operations = InspectionOperations(Clock.fixed(Instant.parse("2026-01-02T12:30:00.123456789Z"), ZoneOffset.UTC))
    fun card() = PropertyCard(PropertyId.new(), "Rental apartment", "Sample Street 10")
    fun template() = ChecklistTemplate(TemplateId.new(), "Authored checklist", TemplateOrigin.Custom,
        listOf(TemplateCheck("Floor — Пол", "Visible condition"), TemplateCheck("Window", null), TemplateCheck("Door", "Ordinary operation")))
    fun photo(caption: String? = null) = Photograph(PhotographId.new(), LocalFileId.new(), caption)

    fun inspection(property: PropertyCard? = null): Inspection {
        var result = operations.create("Rental inspection", "Sample Street 10", InspectionRole.LANDLORD,
            LocalDate.of(2025, 12, 31), "Inspector", property?.id).accepted()
        val first = TemplateOperations.prepareRoom(RoomType.BEDROOM, "First bedroom", template()).accepted()
        val second = TemplateOperations.prepareRoom(RoomType.BEDROOM, "Second bedroom", template()).accepted()
        result = operations.addRoom(result, first).accepted()
        result = operations.addRoom(result, second).accepted()
        result = operations.addIssue(result, first.checks[0].id, "Visible mark", listOf(photo("Detail one"), photo("Деталь два"))).accepted()
        result = operations.addIssue(result, first.checks[0].id, null, listOf(photo())).accepted()
        result = operations.attachPhotograph(result, PhotographOwner.Condition(first.checks[0].id), photo("Overview one")).accepted()
        result = operations.attachPhotograph(result, PhotographOwner.Condition(first.checks[0].id), photo()).accepted()
        result = operations.editCheck(result, first.checks[0].id, "Authored floor", "Authored guidance", "General note").accepted()
        result = operations.setCheckState(result, first.checks[1].id, CheckState.NOT_APPLICABLE).accepted()
        result = operations.setCheckState(result, second.checks[0].id, CheckState.OK).accepted()
        return operations.complete(result, confirmUnchecked = true).accepted()
    }
}

internal fun <T> DomainResult<T>.accepted(): T = when (this) {
    is DomainResult.Success -> value
    is DomainResult.Failure -> error("Unexpected domain failure: $error")
}

internal suspend fun InspectionRepository.read(id: InspectionId): Inspection? = withTimeout(10_000) { observe(id).first().accepted() }
internal suspend fun TemplateRepository.readAll(): List<ChecklistTemplate> = withTimeout(10_000) { observeAll(AppLanguage.ENGLISH).first().accepted() }
