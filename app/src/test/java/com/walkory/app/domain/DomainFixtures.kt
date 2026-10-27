package com.walkory.app.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

internal object DomainFixtures {
    val clock: Clock = Clock.fixed(Instant.parse("2026-01-15T23:30:00Z"), ZoneId.of("Europe/Moscow"))
    val operations = InspectionOperations(clock)

    fun inspection(propertyId: PropertyId? = null): Inspection = operations.create(
        name = "Rental inspection", address = "Sample Street 10", role = InspectionRole.TENANT,
        inspector = "Inspector", propertyId = propertyId,
    ).value()

    fun template(): ChecklistTemplate = ChecklistTemplate(
        TemplateId.new(), "Room checklist", TemplateOrigin.BuiltIn("bedroom.tenant", InspectionRole.TENANT, RoomType.BEDROOM),
        listOf(TemplateCheck("Floor", "Record visible condition"), TemplateCheck("Window", "Check ordinary operation")),
    )

    fun room(name: String = "Bedroom"): InspectionRoom =
        TemplateOperations.prepareRoom(RoomType.BEDROOM, name, template()).value()

    fun withRoom(room: InspectionRoom = room(), inspection: Inspection = inspection()): Inspection =
        operations.addRoom(inspection, room).value()

    fun photo(caption: String? = "Visible condition") = Photograph(PhotographId.new(), LocalFileId.new(), caption)
}

internal fun <T> DomainResult<T>.value(): T = when (this) {
    is DomainResult.Success -> value
    is DomainResult.Failure -> error("Unexpected domain failure: $error")
}

internal fun Inspection.firstCheck(): InspectionCheck = rooms.first().checks.first()
