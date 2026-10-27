package com.walkory.app.data.local

import androidx.room.Embedded
import androidx.room.Relation
import com.walkory.app.domain.*
import java.time.Instant
import java.time.LocalDate

internal data class InspectionGraph(
    @Embedded val record: InspectionEntity,
    @Relation(parentColumn = "id", entityColumn = "inspectionId", entity = RoomEntity::class) val rooms: List<RoomGraph>,
) {
    fun toDomain() = Inspection(
        InspectionId(record.id),
        InspectionDetails(record.name, PropertySnapshot(record.address), LocalDate.parse(record.date), InspectionRole.valueOf(record.role), record.inspector),
        record.propertyId?.let(::PropertyId), InspectionState.valueOf(record.state),
        rooms.sortedBy { it.record.position }.map { it.toDomain() },
        Instant.ofEpochSecond(record.createdSeconds, record.createdNanos.toLong()),
        Instant.ofEpochSecond(record.modifiedSeconds, record.modifiedNanos.toLong()),
    )
}

internal data class RoomGraph(
    @Embedded val record: RoomEntity,
    @Relation(parentColumn = "id", entityColumn = "roomId", entity = CheckEntity::class) val checks: List<CheckGraph>,
) {
    fun toDomain() = InspectionRoom(RoomId(record.id), RoomType.valueOf(record.type), record.name, checks.sortedBy { it.record.position }.map { it.toDomain() })
}

internal data class CheckGraph(
    @Embedded val record: CheckEntity,
    @Relation(parentColumn = "id", entityColumn = "checkId") val conditionPhotos: List<PhotographEntity>,
    @Relation(parentColumn = "id", entityColumn = "checkId", entity = IssueEntity::class) val issues: List<IssueGraph>,
) {
    fun toDomain() = InspectionCheck(
        CheckId(record.id), record.title, record.guidance, CheckState.valueOf(record.state), record.comment,
        conditionPhotos.sortedBy { it.position }.map { it.toDomain() },
        issues.sortedBy { it.record.position }.map { it.toDomain() },
    )
}

internal data class IssueGraph(
    @Embedded val record: IssueEntity,
    @Relation(parentColumn = "id", entityColumn = "issueId") val photos: List<PhotographEntity>,
) {
    fun toDomain() = Issue(IssueId(record.id), record.description, photos.sortedBy { it.position }.map { it.toDomain() })
}

internal fun PhotographEntity.toDomain() = Photograph(PhotographId(id), LocalFileId(fileId), caption)
internal fun PropertyEntity.toDomain() = PropertyCard(PropertyId(id), name, address)

internal data class TemplateGraph(
    @Embedded val record: TemplateEntity,
    @Relation(parentColumn = "id", entityColumn = "templateId") val checks: List<TemplateCheckEntity>,
) {
    fun toDomain() = ChecklistTemplate(TemplateId(record.id), record.name, TemplateOrigin.Custom, checks.sortedBy { it.position }.map { TemplateCheck(it.title, it.guidance) })
}

internal fun Inspection.toEntity() = InspectionEntity(
    id.value, details.name, details.property.address, details.date.toString(), details.role.name,
    details.inspector, propertyId?.value, state.name,
    createdAt.epochSecond, createdAt.nano, modifiedAt.epochSecond, modifiedAt.nano,
)

/** Flat row maps permit identity/parent checks and incremental writes instead of replacing a tree. */
internal class InspectionRows(inspection: Inspection) {
    val rooms = LinkedHashMap<String, RoomEntity>()
    val checks = LinkedHashMap<String, CheckEntity>()
    val issues = LinkedHashMap<String, IssueEntity>()
    val photos = LinkedHashMap<String, PhotographEntity>()

    init {
        inspection.rooms.forEachIndexed { roomPosition, room ->
            rooms[room.id.value] = RoomEntity(room.id.value, inspection.id.value, room.type.name, room.name, roomPosition)
            room.checks.forEachIndexed { checkPosition, check ->
                checks[check.id.value] = CheckEntity(check.id.value, room.id.value, check.title, check.guidance, check.state.name, check.comment, checkPosition)
                check.conditionPhotos.forEachIndexed { position, photo ->
                    photos[photo.id.value] = PhotographEntity(photo.id.value, photo.fileId.value, check.id.value, null, photo.caption, position)
                }
                check.issues.forEachIndexed { issuePosition, issue ->
                    issues[issue.id.value] = IssueEntity(issue.id.value, check.id.value, issue.description, issuePosition)
                    issue.photos.forEachIndexed { position, photo ->
                        photos[photo.id.value] = PhotographEntity(photo.id.value, photo.fileId.value, null, issue.id.value, photo.caption, position)
                    }
                }
            }
        }
    }
}
