package org.happycode.karoo.health_connect.adapters.healthconnect

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
        val records = mutableListOf<Record>()

        val route = when {
            preferences.syncRoute && ride.metrics.route.isNotEmpty() -> createExerciseRoute(ride)
            else -> null
        }

        records.add(
            ExerciseSessionRecord(
                startTime = ride.startTime,
                startZoneOffset = ride.zoneOffset,
                endTime = ride.endTime,
                endZoneOffset = ride.zoneOffset,
                exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
                title = ride.title,
                exerciseRoute = route,
                metadata = Metadata.manualEntry(clientRecordId = "karoo_session_${ride.id.value}"),
            ),
        )

        ride.metrics.totalDistanceMeters?.takeIf { it > 0.0 }?.let { distanceMeters ->
            records.add(
                DistanceRecord(
                    startTime = ride.startTime,
                    startZoneOffset = ride.zoneOffset,
                    endTime = ride.endTime,
                    endZoneOffset = ride.zoneOffset,
                    distance = Length.meters(distanceMeters),
                    metadata = Metadata.manualEntry(clientRecordId = "karoo_dist_${ride.id.value}"),
                ),
            )
        }

        if (preferences.syncCalories) {
            ride.metrics.totalCalories?.takeIf { it > 0 }?.let { calories ->
                records.add(
                    TotalCaloriesBurnedRecord(
                        startTime = ride.startTime,
                        startZoneOffset = ride.zoneOffset,
                        endTime = ride.endTime,
                        endZoneOffset = ride.zoneOffset,
                        energy = Energy.kilocalories(calories.toDouble()),
                        metadata = Metadata.manualEntry(clientRecordId = "karoo_cal_${ride.id.value}"),
                    ),
                )
            }
        }

        if (preferences.syncElevation) {
            ride.metrics.totalElevationGainMeters?.takeIf { it > 0.0 }?.let { elevationGain ->
                records.add(
                    ElevationGainedRecord(
                        startTime = ride.startTime,
                        startZoneOffset = ride.zoneOffset,
                        endTime = ride.endTime,
                        endZoneOffset = ride.zoneOffset,
                        elevation = Length.meters(elevationGain),
                        metadata = Metadata.manualEntry(clientRecordId = "karoo_elev_${ride.id.value}"),
                    ),
                )
            }
        }

        if (preferences.syncHeartRate) {
            ride.metrics.heartRates
                .filter { (timestamp) -> (timestamp in (ride.startTime..ride.endTime)) }
                .takeIf { it.isNotEmpty() }
                ?.let { samples ->
                    records.add(
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
                    )
                }
        }

        if (preferences.syncPower) {
            ride.metrics.powers
                .filter { (timestamp) -> (timestamp in (ride.startTime..ride.endTime)) }
                .takeIf { it.isNotEmpty() }
                ?.let { samples ->
                    records.add(
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
                    )
                }
        }

        if (preferences.syncCadence) {
            ride.metrics.cadences
                .filter { (timestamp) -> (timestamp in (ride.startTime..ride.endTime)) }
                .takeIf { it.isNotEmpty() }
                ?.let { samples ->
                    records.add(
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
                    )
                }
        }

        if (preferences.syncSpeed) {
            ride.metrics.speeds
                .filter { (timestamp) -> (timestamp in (ride.startTime..ride.endTime)) }
                .takeIf { it.isNotEmpty() }
                ?.let { samples ->
                    records.add(
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
                    )
                }
        }

        healthConnectClient.insertRecords(records)
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
}
