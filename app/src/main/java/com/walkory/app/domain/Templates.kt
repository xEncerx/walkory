package com.walkory.app.domain

/** Built-in text is resolved for the chosen locale before instantiation; copies are user-owned text. */
sealed interface TemplateOrigin {
    data object Custom : TemplateOrigin
    data class BuiltIn(val catalogKey: String, val role: InspectionRole, val roomType: RoomType) : TemplateOrigin
}

/** List position is check order. Templates deliberately cannot contain results or media. */
data class TemplateCheck(val title: String, val guidance: String? = null) {
    init { require(title.isNotBlank()) }
}

data class ChecklistTemplate(
    val id: TemplateId,
    val name: String,
    val origin: TemplateOrigin,
    val checks: List<TemplateCheck>,
) {
    init { require(name.isNotBlank()) }
}

object TemplateOperations {
    fun copy(template: ChecklistTemplate, name: String): DomainResult<ChecklistTemplate> {
        if (name.isBlank()) return DomainResult.Failure(DomainError.BlankField(RequiredField.TEMPLATE_NAME))
        return DomainResult.Success(ChecklistTemplate(TemplateId.new(), name, TemplateOrigin.Custom, template.checks.toList()))
    }

    fun edit(
        template: ChecklistTemplate,
        name: String,
        checks: List<TemplateCheck>,
    ): DomainResult<ChecklistTemplate> {
        if (template.origin is TemplateOrigin.BuiltIn) return DomainResult.Failure(DomainError.ReadOnlyTemplate)
        if (name.isBlank()) return DomainResult.Failure(DomainError.BlankField(RequiredField.TEMPLATE_NAME))
        return DomainResult.Success(template.copy(name = name, checks = checks.toList()))
    }

    /** Save only prepared content, even if the room already contains observations and evidence. */
    fun saveRoom(room: InspectionRoom, name: String): DomainResult<ChecklistTemplate> {
        if (name.isBlank()) return DomainResult.Failure(DomainError.BlankField(RequiredField.TEMPLATE_NAME))
        return DomainResult.Success(ChecklistTemplate(
            TemplateId.new(), name, TemplateOrigin.Custom,
            room.checks.map { TemplateCheck(it.title, it.guidance) },
        ))
    }

    /** Exactly one template, or no template. No live source ID, merge, or locale dependency is retained. */
    fun prepareRoom(type: RoomType, name: String, template: ChecklistTemplate?): DomainResult<InspectionRoom> {
        if (name.isBlank()) return DomainResult.Failure(DomainError.BlankField(RequiredField.ROOM_NAME))
        return DomainResult.Success(InspectionRoom(
            RoomId.new(), type, name,
            template?.checks?.map { InspectionCheck(CheckId.new(), it.title, it.guidance) } ?: emptyList(),
        ))
    }
}
