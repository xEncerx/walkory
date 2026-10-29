package com.walkory.app.app

import android.content.Context
import com.walkory.app.data.catalog.CatalogTemplateRepository
import com.walkory.app.data.catalog.ResourceTemplateCatalog
import com.walkory.app.data.local.AttachmentStorageCoordinator
import com.walkory.app.data.local.RoomInspectionRepository
import com.walkory.app.data.local.RoomPropertyRepository
import com.walkory.app.data.local.RoomTemplateRepository
import com.walkory.app.data.local.WalkoryDatabase
import com.walkory.app.data.preferences.DataStoreSettingsRepository
import com.walkory.app.data.preferences.createPreferencesStore
import com.walkory.app.domain.InspectionRepository
import com.walkory.app.domain.PropertyRepository
import com.walkory.app.domain.SettingsRepository
import com.walkory.app.domain.TemplateRepository
import com.walkory.app.platform.files.PrivateAttachmentFiles
import com.walkory.app.platform.images.createLocalImageLoader
import com.walkory.app.platform.images.PhotographMedia
import com.walkory.app.data.local.RoomReportSnapshots
import com.walkory.app.domain.ReportEngine
import com.walkory.app.platform.pdf.ITextReportEngine
import com.walkory.app.platform.pdf.ReportFiles
import java.io.File

class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext

    private val database = WalkoryDatabase.open(applicationContext)
    private val attachmentFiles = PrivateAttachmentFiles(File(applicationContext.filesDir, "attachments"))
    private val attachments = AttachmentStorageCoordinator(database.records(), attachmentFiles)
    private val inspectionRepository = RoomInspectionRepository(database, attachments)

    val inspections: InspectionRepository = inspectionRepository
    internal val photographs = PhotographMedia(applicationContext, inspectionRepository, attachmentFiles)
    val properties: PropertyRepository = RoomPropertyRepository(database)
    val templates: TemplateRepository = CatalogTemplateRepository(RoomTemplateRepository(database), ResourceTemplateCatalog(applicationContext))
    val settings: SettingsRepository = DataStoreSettingsRepository(createPreferencesStore(applicationContext))
    internal val reportFiles = ReportFiles(File(applicationContext.filesDir, "report-staging"), File(applicationContext.cacheDir, "reports"))
    val reports: ReportEngine = ITextReportEngine(applicationContext,
        RoomReportSnapshots(database, attachments, attachmentFiles, reportFiles), reportFiles)
    internal val reportDelivery = com.walkory.app.platform.pdf.AndroidReportDelivery(applicationContext, reportFiles)
    val imageLoader = createLocalImageLoader(applicationContext)
}
