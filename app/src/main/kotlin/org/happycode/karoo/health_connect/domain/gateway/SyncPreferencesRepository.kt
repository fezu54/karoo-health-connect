package org.happycode.karoo.health_connect.domain.gateway

import java.time.Instant
import org.happycode.karoo.health_connect.domain.model.RideId
import org.happycode.karoo.health_connect.domain.model.SyncPreferences

interface SyncPreferencesRepository {
    fun getPreferences(): SyncPreferences
    fun updatePreferences(preferences: SyncPreferences)
    fun getLastSyncTimestamp(): Instant?
    fun markRideSynced(rideId: RideId, timestamp: Instant)
    fun isRideSynced(rideId: RideId): Boolean
}
