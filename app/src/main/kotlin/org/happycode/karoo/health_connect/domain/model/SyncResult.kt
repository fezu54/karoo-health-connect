package org.happycode.karoo.health_connect.domain.model

sealed interface SyncResult {
    data class Success(val syncedCount: Int) : SyncResult
    data class Failure(val error: SyncError) : SyncResult
}

sealed interface SyncError {
    data object AuthenticationFailed : SyncError
    data object HealthConnectUnavailable : SyncError
    data class NetworkError(val message: String) : SyncError
    data class ParseError(val message: String) : SyncError
}
