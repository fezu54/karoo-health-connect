package org.happycode.karoo.health_connect.application

import java.time.Instant
import org.happycode.karoo.health_connect.domain.gateway.FitParser
import org.happycode.karoo.health_connect.domain.gateway.HealthConnectGateway
import org.happycode.karoo.health_connect.domain.gateway.RideSourceGateway
import org.happycode.karoo.health_connect.domain.gateway.SyncPreferencesRepository
import org.happycode.karoo.health_connect.domain.model.SyncError
import org.happycode.karoo.health_connect.domain.model.SyncResult

class SyncRidesUseCase(
    private val rideSourceGateway: RideSourceGateway,
    private val fitParser: FitParser,
    private val healthConnectGateway: HealthConnectGateway,
    private val preferencesRepository: SyncPreferencesRepository,
) {
    suspend fun execute(): SyncResult {
        if (!healthConnectGateway.isAvailable()) {
            return SyncResult.Failure(SyncError.HealthConnectUnavailable)
        }

        val preferences = preferencesRepository.getPreferences()
        val lastSyncTimestamp = preferencesRepository.getLastSyncTimestamp() ?: Instant.EPOCH

        val summaries = runCatching {
            rideSourceGateway.fetchUnsyncedRides(lastSyncTimestamp)
        }.getOrElse { error ->
            return SyncResult.Failure(SyncError.NetworkError(error.message ?: "Failed to fetch rides"))
        }

        var syncedCount = 0

        for ((id, _, startTime) in summaries.sortedBy { it.startTime }) {
            if (preferencesRepository.isRideSynced(id)) {
                continue
            }

            val processResult = runCatching {
                val stream = rideSourceGateway.fetchRawFitStream(id)
                val ride = fitParser.parse(stream)
                healthConnectGateway.upsertRide(ride, preferences)
                preferencesRepository.markRideSynced(id, startTime)
            }

            processResult.onSuccess {
                syncedCount++
            }.onFailure { error ->
                return SyncResult.Failure(SyncError.ParseError(error.message ?: "Failed to process ride ${id.value}"))
            }
        }

        return SyncResult.Success(syncedCount)
    }
}
