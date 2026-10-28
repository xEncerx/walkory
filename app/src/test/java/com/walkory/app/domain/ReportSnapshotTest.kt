package com.walkory.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportSnapshotTest {
    @Test fun sourceListMutationsCannotChangeAnySnapshotEvidenceOrOrder() {
        val condition = mutableListOf(Photograph(PhotographId.new(), LocalFileId.new(), "Condition"))
        val issuePhotos = mutableListOf(Photograph(PhotographId.new(), LocalFileId.new(), "Detail"))
        val issues = mutableListOf(Issue(IssueId.new(), "Independent issue", issuePhotos))
        val checks = mutableListOf(InspectionCheck(CheckId.new(), "Floor", state = CheckState.HAS_ISSUE, conditionPhotos = condition, issues = issues))
        val rooms = mutableListOf(InspectionRoom(RoomId.new(), RoomType.BEDROOM, "Bedroom", checks))
        val record = (InspectionOperations().create("Inspection", "Sample Street 5", InspectionRole.TENANT) as DomainResult.Success).value.copy(rooms = rooms)
        val snapshot = ReportSnapshot(record, LocalFileId.new())
        condition.clear(); issuePhotos.clear(); issues.clear(); checks.clear(); rooms.clear()
        val frozen = snapshot.inspection.rooms.single().checks.single()
        assertEquals("Condition", frozen.conditionPhotos.single().caption)
        assertEquals("Independent issue", frozen.issues.single().description)
        assertEquals("Detail", frozen.issues.single().photos.single().caption)
        assertTrue(record.rooms.isEmpty())
    }
}
