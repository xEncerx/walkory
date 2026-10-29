package com.walkory.app.platform.pdf

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.system.ErrnoException
import android.system.OsConstants
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.walkory.app.domain.LocalFileId
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal interface ReportDelivery {
    fun file(id: LocalFileId): File
    fun share(id: LocalFileId): Intent
    suspend fun save(id: LocalFileId, destination: String, progress: (Long, Long) -> Unit)
}

/** Application context only. Completed artifacts are never rewritten or removed by delivery. */
internal class AndroidReportDelivery(context: Context, private val files: ReportFiles) : ReportDelivery {
    private val context = context.applicationContext
    override fun file(id: LocalFileId): File = files.report(id)

    override fun share(id: LocalFileId): Intent {
        val report = file(id)
        if (!report.isFile) throw FileNotFoundException()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.reports", report)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("PDF", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    override suspend fun save(id: LocalFileId, destination: String, progress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        val uri = destination.toUri()
        var complete = false
        try {
            file(id).inputStream().use { input ->
                val total = file(id).length()
                // CreateDocument grants a new destination; never request broad storage access.
                val descriptor = context.contentResolver.openFileDescriptor(uri, "w") ?: throw IOException("Provider unavailable")
                ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var written = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        written += count
                        progress(written, total)
                    }
                    output.flush()
                    // Detect asynchronous provider-side errors before claiming success.
                    descriptor.checkError()
                }
            }
            currentCoroutineContext().ensureActive()
            complete = true
        } finally {
            if (!complete) {
                // Only the document returned for this CreateDocument request, never earlier exports.
                try { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                catch (_: IOException) { }
                catch (_: SecurityException) { }
                catch (_: IllegalArgumentException) { }
                catch (_: UnsupportedOperationException) { }
            }
        }
    }
}

internal enum class DeliveryError { STORAGE, FULL, MISSING }
internal fun deliveryError(error: Exception): DeliveryError = when {
    generateSequence<Throwable>(error) { it.cause }.any {
        it is ErrnoException && (it.errno == OsConstants.ENOSPC || it.errno == OsConstants.EDQUOT)
    } -> DeliveryError.FULL
    error is FileNotFoundException -> DeliveryError.MISSING
    else -> DeliveryError.STORAGE
}

/** Separate provider prevents a receiver from writing even if it requests a writable mode. */
class ReadOnlyReportProvider : FileProvider() {
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if (mode != "r") throw SecurityException("Reports are read-only")
        return super.openFile(uri, mode)
    }
}
