package org.happycode.karoo.health_connect.adapters.healthconnect

import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.CyclingPedalingCadenceRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Power
import androidx.health.connect.client.units.Velocity
import org.happycode.karoo.health_connect.domain.gateway.HealthConnectGateway
import org.happycode.karoo.health_connect.domain.model.Ride
import org.happycode.karoo.health_connect.domain.model.SyncPreferences

class AndroidHealthConnectGateway(
    private val healthConnectClient: HealthConnectClient,
    private val availabilityChecker: () -> Boolean = { true },
) : HealthConnectGateway {

    override suspend fun isAvailable(): Boolean = availabilityChecker()

    override suspend fun upsertRide(ride: Ride, preferences: SyncPreferences) {
        val route = when {
            preferences.syncRoute && ride.metrics.route.isNotEmpty() -> createExerciseRoute(ride)
            else -> null
        }

        insertSessionRecord(ride, route)
        insertOptionalMetrics(ride, preferences)
    }

    private suspend fun insertSessionRecord(ride: Ride, route: ExerciseRoute?) {
        val sessionRecord = buildSessionRecord(ride, route)
        runCatching {
            healthConnectClient.insertRecords(listOf(sessionRecord))
        }.onFailure { error ->
            if ((route != null) && (error is SecurityException)) {
                Log.w(TAG, "Failed to insert session with route, retrying without route: ${error.message}")
                val fallbackRecord = buildSessionRecord(ride, null)
                healthConnectClient.insertRecords(listOf(fallbackRecord))
            } else {
                throw error
            }
        }
    }

    private fun buildSessionRecord(ride: Ride, route: ExerciseRoute?): ExerciseSessionRecord =
        ExerciseSessionRecord(
            startTime = ride.startTime,
            startZoneOffset = ride.zoneOffset,
            endTime = ride.endTime,
            endZoneOffset = ride.zoneOffset,
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
            title = ride.title,
            exerciseRoute = route,
            metadata = Metadata.manualEntry(clientRecordId = "karoo_session_${ride.id.value}"),
        )

    private suspend fun insertOptionalMetrics(ride: Ride, preferences: SyncPreferences) {
        ride.metrics.totalDistanceMeters?.takeIf { it > 0.0 }?.let { distanceMeters ->
            insertMetricSafe(
                DistanceRecord(
                    startTime = ride.startTime,
                    startZoneOffset = ride.zoneOffset,
                    endTime = ride.endTime,
                    endZoneOffset = ride.zoneOffset,
                    distance = Length.meters(distanceMeters),
                    metadata = Metadata.manualEntry(clientRecordId = "karoo_dist_${ride.id.value}"),
                ),
                "distance",
            )
        }

        if (preferences.syncCalories) {
            ride.metrics.totalCalories?.takeIf { it > 0 }?.let { calories ->
                insertMetricSafe(
                    TotalCaloriesBurnedRecord(
                        startTime = ride.startTime,
                        startZoneOffset = ride.zoneOffset,
                        endTime = ride.endTime,
                        endZoneOffset = ride.zoneOffset,
                        energy = Energy.kilocalories(calories.toDouble()),
                        metadata = Metadata.manualEntry(clientRecordId = "karoo_cal_${ride.id.value}"),
                    ),
                    "calories",
                )
            }
        }

        if (preferences.syncElevation) {
            ride.metrics.totalElevationGainMeters?.takeIf { it > 0.0 }?.let { elevationGain ->
                insertMetricSafe(
                    ElevationGainedRecord(
                        startTime = ride.startTime,
                        startZoneOffset = ride.zoneOffset,
                        endTime = ride.endTime,
                        endZoneOffset = ride.zoneOffset,
                        elevation = Length.meters(elevationGain),
                        metadata = Metadata.manualEntry(clientRecordId = "karoo_elev_${ride.id.value}"),
                    ),
                    "elevation",
                )
            }
        }

        if (preferences.syncHeartRate) {
            ride.metrics.heartRates
                .filter { (timestamp) -> (timestamp in (ride.startTime..ride.endTime)) }
                .takeIf { it.isNotEmpty() }
                ?.let { samples ->
                    insertMetricSafe(
                        HeartRateRecord(
                            startTime = ride.startTime,
                            startZoneOffset = ride.zoneOffset,
                            endTime = ride.endTime,
                            endZoneOffset = ride.zoneOffset,
                            samples = samples.map { (timestamp, bpm) ->
                                HeartRateRecord.Sample(timestamp, bpm.toLong())
                            },
                            metadata = Metadata.manualEntry(clientRecordId = "karoo_hr_${ride.id.value}"),
                        ),
                        "heart rate",
                    )
                }
        }

        if (preferences.syncPower) {
            ride.metrics.powers
                .filter { (timestamp) -> (timestamp in (ride.startTime..ride.endTime)) }
                .takeIf { it.isNotEmpty() }
                ?.let { samples ->
                    insertMetricSafe(
                        PowerRecord(
                            startTime = ride.startTime,
                            startZoneOffset = ride.zoneOffset,
                            endTime = ride.endTime,
                            endZoneOffset = ride.zoneOffset,
                            samples = samples.map { (timestamp, watts) ->
                                PowerRecord.Sample(timestamp, Power.watts(watts.toDouble()))
                            },
                            metadata = Metadata.manualEntry(clientRecordId = "karoo_pwr_${ride.id.value}"),
                        ),
                        "power",
                    )
                }
        }

        if (preferences.syncCadence) {
            ride.metrics.cadences
                .filter { (timestamp) -> (timestamp in (ride.startTime..ride.endTime)) }
                .takeIf { it.isNotEmpty() }
                ?.let { samples ->
                    insertMetricSafe(
                        CyclingPedalingCadenceRecord(
                            startTime = ride.startTime,
                            startZoneOffset = ride.zoneOffset,
                            endTime = ride.endTime,
                            endZoneOffset = ride.zoneOffset,
                            samples = samples.map { (timestamp, rpm) ->
                                CyclingPedalingCadenceRecord.Sample(timestamp, rpm.toDouble())
                            },
                            metadata = Metadata.manualEntry(clientRecordId = "karoo_cad_${ride.id.value}"),
                        ),
                        "cadence",
                    )
                }
        }

        if (preferences.syncSpeed) {
            ride.metrics.speeds
                .filter { (timestamp) -> (timestamp in (ride.startTime..ride.endTime)) }
                .takeIf { it.isNotEmpty() }
                ?.let { samples ->
                    insertMetricSafe(
                        SpeedRecord(
                            startTime = ride.startTime,
                            startZoneOffset = ride.zoneOffset,
                            endTime = ride.endTime,
                            endZoneOffset = ride.zoneOffset,
                            samples = samples.map { (timestamp, speed) ->
                                SpeedRecord.Sample(timestamp, Velocity.metersPerSecond(speed))
                            },
                            metadata = Metadata.manualEntry(clientRecordId = "karoo_spd_${ride.id.value}"),
                        ),
                        "speed",
                    )
                }
        }
    }

    private suspend fun insertMetricSafe(record: Record, metricName: String) {
        runCatching {
            healthConnectClient.insertRecords(listOf(record))
        }.onFailure { error ->
            Log.w(TAG, "Failed to insert $metricName record: ${error.message}")
        }
    }

    private fun createExerciseRoute(ride: Ride): ExerciseRoute? {
        val distinctPoints = ride.metrics.route
            .asSequence()
            .filter { (timestamp) -> (timestamp in (ride.startTime..ride.endTime)) }
            .distinctBy { (timestamp) -> timestamp }
            .sortedBy { (timestamp) -> timestamp }
            .map { (timestamp, latitude, longitude, altitudeMeters) ->
                ExerciseRoute.Location(
                    time = timestamp,
                    latitude = latitude,
                    longitude = longitude,
                    altitude = altitudeMeters?.let { Length.meters(it) },
                )
            }
            .toList()

        if (distinctPoints.isEmpty()) {
            return null
        }

        return ExerciseRoute(route = distinctPoints)
    }

    companion object {
        private const val TAG = "HealthConnectGateway"
    }
}
