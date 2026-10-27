package com.walkory.app.domain

/** Presentation localizes these values; no user-facing strings or resource IDs cross this boundary. */
sealed interface DomainError {
    data class BlankField(val field: RequiredField) : DomainError
    data object EmptyIssue : DomainError
    data class IssuesPreventStateChange(val issueCount: Int) : DomainError
    data class UncheckedConfirmationRequired(val uncheckedCount: Int) : DomainError
    data object AttachmentAlreadyOwned : DomainError
    data object ReadOnlyTemplate : DomainError
    data class NotFound(val record: RecordKind) : DomainError
    data object AlreadyExists : DomainError
    data object InvalidMutation : DomainError
    /** Record deletion committed; private file cleanup is durably queued and can be retried. */
    data object AttachmentCleanupPending : DomainError
    data object StorageUnavailable : DomainError
    data object InsufficientStorage : DomainError
    data object InvalidImage : DomainError
    data object ReportGenerationFailed : DomainError
}

enum class RequiredField { INSPECTION_NAME, ADDRESS, ROOM_NAME, CHECK_TITLE, TEMPLATE_NAME }
enum class RecordKind { INSPECTION, PROPERTY, ROOM, CHECK, ISSUE, PHOTOGRAPH, TEMPLATE }

sealed interface DomainResult<out T> {
    data class Success<T>(val value: T) : DomainResult<T>
    data class Failure(val error: DomainError) : DomainResult<Nothing>
}

inline fun <T, R> DomainResult<T>.map(transform: (T) -> R): DomainResult<R> = when (this) {
    is DomainResult.Success -> DomainResult.Success(transform(value))
    is DomainResult.Failure -> this
}
