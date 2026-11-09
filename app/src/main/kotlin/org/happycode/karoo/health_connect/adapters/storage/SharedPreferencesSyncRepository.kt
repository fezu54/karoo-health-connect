package org.happycode.karoo.health_connect.adapters.storage

import android.content.SharedPreferences
import androidx.core.content.edit
import java.time.Instant
import org.happycode.karoo.health_connect.domain.gateway.SyncPreferencesRepository
import org.happycode.karoo.health_connect.domain.model.RideId
import org.happycode.karoo.health_connect.domain.model.SyncPreferences

class SharedPreferencesSyncRepository(
    private val preferences: SharedPreferences,
) : SyncPreferencesRepository {

    override fun getPreferences(): SyncPreferences = SyncPreferences(
        syncHeartRate = preferences.getBoolean(KEY_SYNC_HR, true),
        syncPower = preferences.getBoolean(KEY_SYNC_POWER, true),
        syncCadence = preferences.getBoolean(KEY_SYNC_CADENCE, true),
        syncSpeed = preferences.getBoolean(KEY_SYNC_SPEED, true),
        syncCalories = preferences.getBoolean(KEY_SYNC_CALORIES, true),
        syncElevation = preferences.getBoolean(KEY_SYNC_ELEVATION, true),
        syncRoute = preferences.getBoolean(KEY_SYNC_ROUTE, true),
    )

    override fun updatePreferences(preferences: SyncPreferences) {
        this.preferences.edit {
            putBoolean(KEY_SYNC_HR, preferences.syncHeartRate)
            putBoolean(KEY_SYNC_POWER, preferences.syncPower)
            putBoolean(KEY_SYNC_CADENCE, preferences.syncCadence)
            putBoolean(KEY_SYNC_SPEED, preferences.syncSpeed)
            putBoolean(KEY_SYNC_CALORIES, preferences.syncCalories)
            putBoolean(KEY_SYNC_ELEVATION, preferences.syncElevation)
            putBoolean(KEY_SYNC_ROUTE, preferences.syncRoute)
        }
    }

    override fun getLastSyncTimestamp(): Instant? =
        preferences.getLong(KEY_LAST_SYNC_EPOCH_MS, -1L)
            .takeIf { it > 0 }
            ?.let(Instant::ofEpochMilli)

    override fun markRideSynced(rideId: RideId, timestamp: Instant) {
        val existingIds = preferences.getStringSet(KEY_SYNCED_RIDE_IDS, emptySet())?.toMutableSet()
            ?: mutableSetOf()
        existingIds.add(rideId.value)

        val lastSync = preferences.getLong(KEY_LAST_SYNC_EPOCH_MS, 0L)
        val newSync = maxOf(lastSync, timestamp.toEpochMilli())

        preferences.edit {
            putStringSet(KEY_SYNCED_RIDE_IDS, existingIds)
            putLong(KEY_LAST_SYNC_EPOCH_MS, newSync)
        }
    }

    override fun isRideSynced(rideId: RideId): Boolean =
        preferences.getStringSet(KEY_SYNCED_RIDE_IDS, emptySet())?.contains(rideId.value) == true

    companion object {
        private const val KEY_LAST_SYNC_EPOCH_MS = "pref_last_sync_epoch_ms"
        private const val KEY_SYNCED_RIDE_IDS = "pref_synced_ride_ids"
        private const val KEY_SYNC_HR = "pref_sync_heart_rate"
        private const val KEY_SYNC_POWER = "pref_sync_power"
        private const val KEY_SYNC_CADENCE = "pref_sync_cadence"
        private const val KEY_SYNC_SPEED = "pref_sync_speed"
        private const val KEY_SYNC_CALORIES = "pref_sync_calories"
        private const val KEY_SYNC_ELEVATION = "pref_sync_elevation"
        private const val KEY_SYNC_ROUTE = "pref_sync_route"
    }
}
