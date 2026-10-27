package com.walkory.app.domain

import java.time.Instant
import java.time.LocalDate

enum class InspectionRole { TENANT, LANDLORD }
enum class InspectionState { IN_PROGRESS, COMPLETED }
enum class CheckState { UNCHECKED, OK, HAS_ISSUE, NOT_APPLICABLE }
enum class RoomType {
    KITCHEN, BEDROOM, LIVING_ROOM, COMBINED_BATHROOM, BATHROOM, TOILET,
    ENTRANCE_HALL, BALCONY, WALK_IN_WARDROBE, STORAGE_ROOM, STUDY,
    LAUNDRY_UTILITY_ROOM, DINING_ROOM, CUSTOM,
}

/** Copied values; changing or deleting the property card cannot change this snapshot. */
data class PropertySnapshot(val address: String) {
    init { require(address.isNotBlank()) }
}

data class PropertyCard(val id: PropertyId, val name: String, val address: String) {
    init { require(name.isNotBlank()); require(address.isNotBlank()) }
}

data class InspectionDetails(
    val name: String,
    val property: PropertySnapshot,
    val date: LocalDate,
    val role: InspectionRole,
    val inspector: String? = null,
) {
    init { require(name.isNotBlank()) }
}

/** Lists are ordered snapshots. Descendants are exclusively owned by their containing parent. */
data class Inspection(
    val id: InspectionId,
    val details: InspectionDetails,
    val propertyId: PropertyId?,
    val state: InspectionState,
    val rooms: List<InspectionRoom>,
    val createdAt: Instant,
    val modifiedAt: Instant,
)

data class InspectionRoom(
    val id: RoomId,
    val type: RoomType,
    val name: String,
    val checks: List<InspectionCheck>,
) {
    init { require(name.isNotBlank()) }
}

data class InspectionCheck(
    val id: CheckId,
    val title: String,
    val guidance: String? = null,
    val state: CheckState = CheckState.UNCHECKED,
    val comment: String? = null,
    val conditionPhotos: List<Photograph> = emptyList(),
    val issues: List<Issue> = emptyList(),
) {
    init {
        require(title.isNotBlank())
        require(issues.isEmpty() || state == CheckState.HAS_ISSUE)
    }
}

data class Issue(
    val id: IssueId,
    val description: String? = null,
    val photos: List<Photograph> = emptyList(),
) {
    init { require(!description.isNullOrBlank() || photos.isNotEmpty()) }
}

/** A photo and its independent file belong to exactly one condition list or issue list. */
data class Photograph(val id: PhotographId, val fileId: LocalFileId, val caption: String? = null)

sealed interface PhotographOwner {
    data class Condition(val checkId: CheckId) : PhotographOwner
    data class IssueEvidence(val checkId: CheckId, val issueId: IssueId) : PhotographOwner
}

data class InspectionProgress(
    val totalChecks: Int,
    val checkedChecks: Int,
    val uncheckedChecks: Int,
    val problematicChecks: Int,
    val issueCount: Int,
) {
    /** Empty inspections have no completed checks, rather than NaN or artificial 100% progress. */
    val fraction: Double get() = if (totalChecks == 0) 0.0 else checkedChecks.toDouble() / totalChecks
}

fun Inspection.progress(): InspectionProgress {
    var total = 0
    var unchecked = 0
    var problematic = 0
    var issues = 0
    for (room in rooms) for (check in room.checks) {
        total++
        if (check.state == CheckState.UNCHECKED) unchecked++
        if (check.state == CheckState.HAS_ISSUE) problematic++
        issues += check.issues.size
    }
    return InspectionProgress(total, total - unchecked, unchecked, problematic, issues)
}
