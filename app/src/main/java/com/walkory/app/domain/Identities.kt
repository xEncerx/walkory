package com.walkory.app.domain

import java.util.UUID

@JvmInline value class InspectionId(val value: String) {
    init { require(value.isNotBlank()) }
    companion object { fun new() = InspectionId(UUID.randomUUID().toString()) }
}

@JvmInline value class PropertyId(val value: String) {
    init { require(value.isNotBlank()) }
    companion object { fun new() = PropertyId(UUID.randomUUID().toString()) }
}

@JvmInline value class RoomId(val value: String) {
    init { require(value.isNotBlank()) }
    companion object { fun new() = RoomId(UUID.randomUUID().toString()) }
}

@JvmInline value class CheckId(val value: String) {
    init { require(value.isNotBlank()) }
    companion object { fun new() = CheckId(UUID.randomUUID().toString()) }
}

@JvmInline value class IssueId(val value: String) {
    init { require(value.isNotBlank()) }
    companion object { fun new() = IssueId(UUID.randomUUID().toString()) }
}

@JvmInline value class TemplateId(val value: String) {
    init { require(value.isNotBlank()) }
    companion object { fun new() = TemplateId(UUID.randomUUID().toString()) }
}

@JvmInline value class PhotographId(val value: String) {
    init { require(value.isNotBlank()) }
    companion object { fun new() = PhotographId(UUID.randomUUID().toString()) }
}

/** Opaque app-private file identity, never a path, Android Uri or gallery reference. */
@JvmInline value class LocalFileId(val value: String) {
    init { require(value.isNotBlank()) }
    companion object { fun new() = LocalFileId(UUID.randomUUID().toString()) }
}
