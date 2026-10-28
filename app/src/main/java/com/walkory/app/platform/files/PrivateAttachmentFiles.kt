package com.walkory.app.platform.files

import com.walkory.app.domain.LocalFileId
import java.io.File
import java.io.IOException
import java.util.Base64
import android.graphics.BitmapFactory
import android.system.ErrnoException
import android.system.OsConstants
import com.walkory.app.domain.DomainError
import com.walkory.app.domain.DomainResult
import java.io.FileOutputStream
import java.io.InputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Only this directory is eligible for attachment cleanup; no gallery paths or exported PDFs. */
internal class PrivateAttachmentFiles(private val directory: File) {
    fun resolve(id: LocalFileId): File = File(
        directory,
        Base64.getUrlEncoder().withoutPadding().encodeToString(id.value.toByteArray(Charsets.UTF_8)),
    )

    /** Runs on IO under the coordinator, after the file identity has been durably journaled. */
    suspend fun copyImage(id: LocalFileId, open: () -> InputStream): DomainResult<Unit> {
        try {
            val input = try { open() } catch (_: IOException) {
                return DomainResult.Failure(DomainError.InvalidImage)
            }
            input.use { source ->
                if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create attachment directory")
                val destination = resolve(id)
                if (!destination.createNewFile()) throw IOException("Attachment already exists")
                FileOutputStream(destination).use { target ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = source.read(buffer)
                        if (count < 0) break
                        target.write(buffer, 0, count)
                    }
                    target.fd.sync()
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(destination.path, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outMimeType?.startsWith("image/") != true) {
                    return DomainResult.Failure(DomainError.InvalidImage)
                }
                var sample = 1
                while (bounds.outWidth / sample > 256 || bounds.outHeight / sample > 256) sample *= 2
                val bitmap = BitmapFactory.decodeFile(destination.path, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?: return DomainResult.Failure(DomainError.InvalidImage)
                bitmap.recycle()
            }
            return DomainResult.Success(Unit)
        } catch (_: SecurityException) {
            return DomainResult.Failure(DomainError.InvalidImage)
        } catch (failure: IOException) {
            val full = generateSequence<Throwable>(failure) { it.cause }
                .any { it is ErrnoException && (it.errno == OsConstants.ENOSPC || it.errno == OsConstants.EDQUOT) }
            return DomainResult.Failure(if (full) DomainError.InsufficientStorage else DomainError.StorageUnavailable)
        }
    }

    fun delete(id: LocalFileId) {
        val file = resolve(id)
        // Never recursively delete directories, even if a damaged storage entry has this name.
        if (file.exists() && (!file.isFile || !file.delete())) throw IOException("Cannot remove private attachment")
    }
}
