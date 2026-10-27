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
import java.io.File

class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext

    private val database = WalkoryDatabase.open(applicationContext)
    private val attachments = AttachmentStorageCoordinator(
        database.records(),
        PrivateAttachmentFiles(File(applicationContext.filesDir, "attachments")),
    )

    val inspections: InspectionRepository = RoomInspectionRepository(database, attachments)
    val properties: PropertyRepository = RoomPropertyRepository(database)
    val templates: TemplateRepository = CatalogTemplateRepository(RoomTemplateRepository(database), ResourceTemplateCatalog(applicationContext))
    val settings: SettingsRepository = DataStoreSettingsRepository(createPreferencesStore(applicationContext))
    val imageLoader = createLocalImageLoader(applicationContext)
}
