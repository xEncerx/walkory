package com.walkory.app.platform.files

import com.walkory.app.domain.LocalFileId
import java.io.File
import java.io.IOException
import java.util.Base64

/** Only this directory is eligible for attachment cleanup; no gallery paths or exported PDFs. */
internal class PrivateAttachmentFiles(private val directory: File) {
    fun resolve(id: LocalFileId): File = File(
        directory,
        Base64.getUrlEncoder().withoutPadding().encodeToString(id.value.toByteArray(Charsets.UTF_8)),
    )

    fun delete(id: LocalFileId) {
        val file = resolve(id)
        // Never recursively delete directories, even if a damaged storage entry has this name.
        if (file.exists() && (!file.isFile || !file.delete())) throw IOException("Cannot remove private attachment")
    }
}
