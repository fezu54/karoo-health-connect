package org.happycode.karoo.health_connect.domain.gateway

import org.happycode.karoo.health_connect.domain.model.Ride
import org.happycode.karoo.health_connect.domain.model.SyncPreferences

interface HealthConnectGateway {
    suspend fun isAvailable(): Boolean
    suspend fun upsertRide(ride: Ride, preferences: SyncPreferences)
}
