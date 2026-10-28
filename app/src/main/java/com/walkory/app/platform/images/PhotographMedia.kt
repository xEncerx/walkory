package com.walkory.app.platform.images

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.walkory.app.data.local.RoomInspectionRepository
import com.walkory.app.domain.*
import com.walkory.app.platform.files.PrivateAttachmentFiles
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Android handles stay at the media boundary; records contain only private file identities. */
internal class PhotographMedia(
    private val context: Context,
    private val repository: RoomInspectionRepository,
    private val files: PrivateAttachmentFiles,
) {
    fun file(id: LocalFileId): File = files.resolve(id)

    // One external capture at a time. Retained across process recreation; never a visible attachment.
    private val capture get() = File(context.cacheDir, "camera/capture.jpg")

    suspend fun prepareCapture(): Uri = withContext(Dispatchers.IO) {
        val file = capture
        if (!file.parentFile!!.isDirectory && !file.parentFile!!.mkdirs()) throw IOException("Cannot prepare camera")
        if (file.exists() && !file.delete()) throw IOException("Cannot clear previous capture")
        if (!file.createNewFile()) throw IOException("Cannot prepare camera")
        FileProvider.getUriForFile(context, "${context.packageName}.media", file)
    }

    suspend fun discardCapture(): Boolean = withContext(Dispatchers.IO) {
        !capture.exists() || capture.delete()
    }

    suspend fun import(
        inspection: InspectionId,
        owner: PhotographOwner,
        sources: List<Uri>,
        newIssueDescription: String? = null,
    ): DomainResult<Inspection> = repository.importPhotographs(inspection, owner, sources.size, newIssueDescription) { photos ->
        for ((index, photo) in photos.withIndex()) {
            val result = files.copyImage(photo.fileId) {
                context.contentResolver.openInputStream(sources[index]) ?: throw IOException("Unreadable image")
            }
            if (result is DomainResult.Failure) return@importPhotographs result
        }
        DomainResult.Success(Unit)
    }
}
