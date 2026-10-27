package com.walkory.app.data.local

import com.walkory.app.domain.DomainError
import com.walkory.app.domain.DomainResult
import com.walkory.app.domain.LocalFileId
import com.walkory.app.platform.files.PrivateAttachmentFiles
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The same gate protects metadata mutation/file removal and consistent report-copy staging. */
internal class AttachmentStorageCoordinator(
    private val dao: WalkoryDao,
    private val files: PrivateAttachmentFiles,
) {
    private val gate = Mutex()

    suspend fun <T> coordinated(action: suspend () -> T): T = gate.withLock { action() }

    /** Caller holds the gate. A crash after file deletion but before forgetting its ID is harmless. */
    suspend fun cleanupPending(): DomainResult<Unit> = withContext(Dispatchers.IO) {
        try {
            for (id in dao.pendingFiles()) {
                files.delete(LocalFileId(id))
                dao.forgetDeletedFile(id)
            }
            DomainResult.Success(Unit)
        } catch (_: IOException) {
            DomainResult.Failure(DomainError.AttachmentCleanupPending)
        } catch (_: android.database.sqlite.SQLiteException) {
            DomainResult.Failure(DomainError.AttachmentCleanupPending)
        }
    }
}
