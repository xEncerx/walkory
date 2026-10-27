package com.walkory.app.data.local

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import com.walkory.app.domain.DomainError
import com.walkory.app.domain.DomainResult
import java.io.IOException
import java.time.DateTimeException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Cancellation and programming errors are deliberately not converted to a storage success/failure. */
internal suspend fun <T> storageResult(action: suspend () -> DomainResult<T>): DomainResult<T> = try {
    action()
} catch (_: SQLiteFullException) {
    DomainResult.Failure(DomainError.InsufficientStorage)
} catch (_: SQLiteConstraintException) {
    DomainResult.Failure(DomainError.AlreadyExists)
} catch (_: SQLiteException) {
    DomainResult.Failure(DomainError.StorageUnavailable)
} catch (_: IOException) {
    DomainResult.Failure(DomainError.StorageUnavailable)
}

internal fun <T, R> Flow<T>.durableSnapshots(decode: (T) -> R): Flow<DomainResult<R>> =
    map<T, DomainResult<R>> { value ->
        try {
            DomainResult.Success(decode(value))
        } catch (_: IllegalArgumentException) {
            DomainResult.Failure(DomainError.StorageUnavailable)
        } catch (_: DateTimeException) {
            DomainResult.Failure(DomainError.StorageUnavailable)
        }
    }.catch { error ->
        when (error) {
            is SQLiteFullException -> emit(DomainResult.Failure(DomainError.InsufficientStorage))
            is SQLiteException, is IOException -> emit(DomainResult.Failure(DomainError.StorageUnavailable))
            else -> throw error
        }
    }
