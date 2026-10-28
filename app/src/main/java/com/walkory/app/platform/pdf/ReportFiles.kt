package com.walkory.app.platform.pdf

import com.walkory.app.domain.LocalFileId
import com.walkory.app.platform.files.PrivateAttachmentFiles
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One application-scoped owner. Recovery runs once, before any new job allocates files. */
internal class ReportFiles(private val stagingRoot: File, private val outputRoot: File) {
    private val initialization = Mutex()
    private var initialized = false

    suspend fun prepare() = initialization.withLock {
        if (!initialized) {
            directory(stagingRoot)
            directory(outputRoot)
            for (entry in stagingRoot.listFiles() ?: throw IOException("Cannot list report staging")) {
                if (ownedId(entry.name) && entry.isDirectory && !entry.deleteRecursively()) {
                    throw IOException("Cannot recover report staging")
                }
            }
            for (entry in outputRoot.listFiles() ?: throw IOException("Cannot list reports")) {
                if (entry.name.endsWith(".partial") && ownedId(entry.name.removeSuffix(".partial")) &&
                    entry.isFile && !entry.delete()) throw IOException("Cannot recover partial report")
            }
            initialized = true
        }
    }

    fun createStaging(id: LocalFileId) { directory(staging(id)) }
    fun photographs(id: LocalFileId) = PrivateAttachmentFiles(staging(id))
    fun partial(id: LocalFileId) = File(outputRoot, "${uuid(id)}.partial")
    fun report(id: LocalFileId) = File(outputRoot, "${uuid(id)}.pdf")
    fun removeStaging(id: LocalFileId) {
        val directory = staging(id)
        if (directory.exists() && !directory.deleteRecursively()) throw IOException("Cannot remove report staging")
    }

    private fun staging(id: LocalFileId) = File(stagingRoot, uuid(id))
    private fun uuid(id: LocalFileId): String = UUID.fromString(id.value).toString()
    private fun ownedId(name: String): Boolean = try { UUID.fromString(name).toString() == name } catch (_: IllegalArgumentException) { false }
    private fun directory(file: File) {
        if (!file.isDirectory && !file.mkdirs()) throw IOException("Cannot create report directory")
    }
}
