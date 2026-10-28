package com.walkory.app.data.local

import com.walkory.app.domain.*
import com.walkory.app.platform.files.PrivateAttachmentFiles
import com.walkory.app.platform.pdf.ReportFiles
import java.io.FileNotFoundException
import com.walkory.app.platform.pdf.UnreadableReportPhoto
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal class RoomReportSnapshots(
    private val database: WalkoryDatabase,
    private val attachments: AttachmentStorageCoordinator,
    private val originals: PrivateAttachmentFiles,
    private val files: ReportFiles,
) : ReportSnapshots {
    override suspend fun <T> withSnapshot(id: InspectionId, render: suspend (ReportSnapshot) -> T): DomainResult<T> =
        withContext(Dispatchers.IO) {
            val stagingId = LocalFileId.new()
            try {
                files.prepare()
                val snapshot = attachments.coordinated {
                    // Room's aggregate read is transactional; copying holds only the storage gate.
                    val inspection = database.records().inspection(id.value)?.toDomain()
                        ?: return@coordinated null
                    val frozen = ReportSnapshot(inspection, stagingId)
                    files.createStaging(stagingId)
                    val target = files.photographs(stagingId)
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    for (room in frozen.inspection.rooms) for (check in room.checks) {
                        for (photo in check.conditionPhotos) copy(photo, target, buffer)
                        for (issue in check.issues) for (photo in issue.photos) copy(photo, target, buffer)
                    }
                    frozen
                } ?: return@withContext DomainResult.Failure(DomainError.NotFound(RecordKind.INSPECTION))
                currentCoroutineContext().ensureActive()
                DomainResult.Success(render(snapshot))
            } catch (_: android.database.sqlite.SQLiteFullException) {
                DomainResult.Failure(DomainError.InsufficientStorage)
            } catch (_: android.database.sqlite.SQLiteException) {
                DomainResult.Failure(DomainError.StorageUnavailable)
            } finally {
                withContext(NonCancellable) { files.removeStaging(stagingId) }
            }
        }

    private suspend fun copy(photo: Photograph, target: PrivateAttachmentFiles, buffer: ByteArray) {
        val sourceFile = originals.resolve(photo.fileId)
        if (!sourceFile.isFile || !sourceFile.canRead()) throw UnreadableReportPhoto()
        val input = try { sourceFile.inputStream() } catch (failure: FileNotFoundException) { throw UnreadableReportPhoto(failure) }
        input.use { source ->
            FileOutputStream(target.resolve(photo.fileId)).use { output ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = source.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
        }
    }

}
