package com.walkory.app.domain

import java.io.DataOutputStream
import java.io.OutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest

/** Only report inputs, in document order. Length prefixes distinguish nulls and field boundaries. */
fun reportFingerprint(inspection: Inspection, localeTag: String, quality: PdfPhotoQuality): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val sink = object : OutputStream() {
        override fun write(value: Int) = Unit
        override fun write(buffer: ByteArray, offset: Int, length: Int) = Unit
    }
    DataOutputStream(DigestOutputStream(sink, digest)).use { output ->
        fun field(value: String?) {
            if (value == null) output.writeInt(-1)
            else {
                val bytes = value.toByteArray(Charsets.UTF_8)
                output.writeInt(bytes.size)
                output.write(bytes)
            }
        }
        fun photographs(values: List<Photograph>) {
            output.writeInt(values.size)
            for (photo in values) { field(photo.fileId.value); field(photo.caption) }
        }
        field("walkory-report-2")
        field(localeTag); field(quality.name)
        field(inspection.details.name); field(inspection.details.property.address)
        field(inspection.details.date.toString()); field(inspection.details.role.name)
        field(inspection.details.inspector); field(inspection.state.name)
        output.writeInt(inspection.rooms.size)
        for (room in inspection.rooms) {
            field(room.name)
            output.writeInt(room.checks.size)
            for (check in room.checks) {
                field(check.title); field(check.guidance); field(check.state.name); field(check.comment)
                photographs(check.conditionPhotos)
                output.writeInt(check.issues.size)
                for (issue in check.issues) { field(issue.description); photographs(issue.photos) }
            }
        }
    }
    val hex = "0123456789abcdef"
    val bytes = digest.digest()
    return buildString(bytes.size * 2) { for (byte in bytes) { val n = byte.toInt() and 255; append(hex[n ushr 4]); append(hex[n and 15]) } }
}
