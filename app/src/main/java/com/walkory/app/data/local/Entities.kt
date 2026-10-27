package com.walkory.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "properties")
internal data class PropertyEntity(@PrimaryKey val id: String, val name: String, val address: String)

@Entity(
    tableName = "inspections",
    foreignKeys = [ForeignKey(entity = PropertyEntity::class, parentColumns = ["id"], childColumns = ["propertyId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("propertyId")],
)
internal data class InspectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val address: String,
    val date: String,
    val role: String,
    val inspector: String?,
    val propertyId: String?,
    val state: String,
    val createdSeconds: Long,
    val createdNanos: Int,
    val modifiedSeconds: Long,
    val modifiedNanos: Int,
)

@Entity(
    tableName = "rooms",
    foreignKeys = [ForeignKey(entity = InspectionEntity::class, parentColumns = ["id"], childColumns = ["inspectionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("inspectionId")],
)
internal data class RoomEntity(@PrimaryKey val id: String, val inspectionId: String, val type: String, val name: String, val position: Int)

@Entity(
    tableName = "checks",
    foreignKeys = [ForeignKey(entity = RoomEntity::class, parentColumns = ["id"], childColumns = ["roomId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("roomId")],
)
internal data class CheckEntity(
    @PrimaryKey val id: String, val roomId: String, val title: String, val guidance: String?,
    val state: String, val comment: String?, val position: Int,
)

@Entity(
    tableName = "issues",
    foreignKeys = [ForeignKey(entity = CheckEntity::class, parentColumns = ["id"], childColumns = ["checkId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("checkId")],
)
internal data class IssueEntity(@PrimaryKey val id: String, val checkId: String, val description: String?, val position: Int)

/** A deleted photo leaves this row pending until its private file is successfully removed. */
@Entity(tableName = "attachment_files")
internal data class AttachmentFileEntity(@PrimaryKey val id: String, val pendingDeletion: Boolean = false)

@Entity(
    tableName = "photographs",
    foreignKeys = [
        ForeignKey(entity = CheckEntity::class, parentColumns = ["id"], childColumns = ["checkId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = IssueEntity::class, parentColumns = ["id"], childColumns = ["issueId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = AttachmentFileEntity::class, parentColumns = ["id"], childColumns = ["fileId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("checkId"), Index("issueId"), Index(value = ["fileId"], unique = true)],
)
internal data class PhotographEntity(
    @PrimaryKey val id: String, val fileId: String, val checkId: String?, val issueId: String?,
    val caption: String?, val position: Int,
)

@Entity(tableName = "templates")
internal data class TemplateEntity(@PrimaryKey val id: String, val name: String)

@Entity(
    tableName = "template_checks",
    primaryKeys = ["templateId", "position"],
    foreignKeys = [ForeignKey(entity = TemplateEntity::class, parentColumns = ["id"], childColumns = ["templateId"], onDelete = ForeignKey.CASCADE)],
)
internal data class TemplateCheckEntity(val templateId: String, val position: Int, val title: String, val guidance: String?)
