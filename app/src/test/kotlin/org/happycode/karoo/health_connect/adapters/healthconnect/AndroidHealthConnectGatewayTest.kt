package org.happycode.karoo.health_connect.adapters.healthconnect

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.CyclingPedalingCadenceRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.response.InsertRecordsResponse
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import java.time.Instant
import java.time.ZoneOffset
import org.happycode.karoo.health_connect.domain.model.CadenceSample
import org.happycode.karoo.health_connect.domain.model.GpsPoint
import org.happycode.karoo.health_connect.domain.model.HeartRateSample
import org.happycode.karoo.health_connect.domain.model.PowerSample
import org.happycode.karoo.health_connect.domain.model.Ride
import org.happycode.karoo.health_connect.domain.model.RideId
import org.happycode.karoo.health_connect.domain.model.RideMetrics
import org.happycode.karoo.health_connect.domain.model.SpeedSample
import org.happycode.karoo.health_connect.domain.model.SyncPreferences

class AndroidHealthConnectGatewayTest : FreeSpec() {
    init {
        isolationMode = IsolationMode.InstancePerTest

    val client = mockk<HealthConnectClient>()
    val insertedRecordsSlot = slot<List<Record>>()

    coEvery { client.insertRecords(capture(insertedRecordsSlot)) } returns InsertRecordsResponse(emptyList())

    val startTime = Instant.parse("2026-11-09T08:00:00Z")
    val endTime = startTime.plusSeconds(3600)

    val sampleRide = Ride(
        id = RideId("ride-99"),
        title = "Morning Ride",
        startTime = startTime,
        endTime = endTime,
        zoneOffset = ZoneOffset.UTC,
        metrics = RideMetrics(
            heartRates = listOf(HeartRateSample(startTime.plusSeconds(10), 140)),
            powers = listOf(PowerSample(startTime.plusSeconds(10), 200)),
            cadences = listOf(CadenceSample(startTime.plusSeconds(10), 85)),
            speeds = listOf(SpeedSample(startTime.plusSeconds(10), 8.0)),
            route = listOf(
                GpsPoint(
                    latitude = 47.3769,
                    longitude = 8.5417,
                    altitudeMeters = 400.0,
                    timestamp = startTime.plusSeconds(10),
                ),
            ),
            totalDistanceMeters = 25000.0,
            totalElevationGainMeters = 300.0,
            totalCalories = 600,
        ),
    )

    "should return availability from availabilityChecker when availability checked" {
        //given
        val availableGateway = AndroidHealthConnectGateway(client) { true }
        val unavailableGateway = AndroidHealthConnectGateway(client) { false }

        //when & then
        availableGateway.isAvailable() shouldBe true
        unavailableGateway.isAvailable() shouldBe false
    }

    "should map and insert all records to Health Connect when ride with full telemetry provided" {
        //given
        val gateway = AndroidHealthConnectGateway(client)
        val preferences = SyncPreferences()

        //when
        gateway.upsertRide(sampleRide, preferences)

        //then
        val records = insertedRecordsSlot.captured
        records shouldHaveSize 8

        val session = records.asSequence().filterIsInstance<ExerciseSessionRecord>().single()
        session.title shouldBe "Morning Ride"
        session.exerciseType shouldBe ExerciseSessionRecord.EXERCISE_TYPE_BIKING
        session.metadata.clientRecordId shouldBe "karoo_session_ride-99"

        val routeData = session.exerciseRouteResult.shouldBeInstanceOf<ExerciseRouteResult.Data>()
        routeData.exerciseRoute.route shouldHaveSize 1

        val distance = records.asSequence().filterIsInstance<DistanceRecord>().single()
        distance.metadata.clientRecordId shouldBe "karoo_dist_ride-99"
        distance.distance.inMeters shouldBe 25000.0

        val calories = records.asSequence().filterIsInstance<TotalCaloriesBurnedRecord>().single()
        calories.metadata.clientRecordId shouldBe "karoo_cal_ride-99"
        calories.energy.inKilocalories shouldBe 600.0

        val elevation = records.asSequence().filterIsInstance<ElevationGainedRecord>().single()
        elevation.metadata.clientRecordId shouldBe "karoo_elev_ride-99"
        elevation.elevation.inMeters shouldBe 300.0

        val heartRate = records.asSequence().filterIsInstance<HeartRateRecord>().single()
        heartRate.metadata.clientRecordId shouldBe "karoo_hr_ride-99"
        heartRate.samples.first().beatsPerMinute shouldBe 140

        val power = records.asSequence().filterIsInstance<PowerRecord>().single()
        power.metadata.clientRecordId shouldBe "karoo_pwr_ride-99"
        power.samples.first().power.inWatts shouldBe 200.0

        val cadence = records.asSequence().filterIsInstance<CyclingPedalingCadenceRecord>().single()
        cadence.metadata.clientRecordId shouldBe "karoo_cad_ride-99"
        cadence.samples.first().revolutionsPerMinute shouldBe 85.0

        val speed = records.asSequence().filterIsInstance<SpeedRecord>().single()
        speed.metadata.clientRecordId shouldBe "karoo_spd_ride-99"
        speed.samples.first().speed.inMetersPerSecond shouldBe 8.0
    }

    "should omit disabled metric records when user preferences disable specific metrics" {
        //given
        val gateway = AndroidHealthConnectGateway(client)
        val preferences = SyncPreferences(
            syncHeartRate = false,
            syncPower = false,
            syncCadence = false,
            syncSpeed = false,
            syncCalories = false,
            syncElevation = false,
            syncRoute = false,
        )

        //when
        gateway.upsertRide(sampleRide, preferences)

        //then
        val records = insertedRecordsSlot.captured
        records shouldHaveSize 2 // Only ExerciseSessionRecord and DistanceRecord

        val session = records.asSequence().filterIsInstance<ExerciseSessionRecord>().single()
        session.exerciseRouteResult shouldBe ExerciseRouteResult.NoData()

        records.filterIsInstance<HeartRateRecord>() shouldBe emptyList()
        records.filterIsInstance<PowerRecord>() shouldBe emptyList()
        records.filterIsInstance<CyclingPedalingCadenceRecord>() shouldBe emptyList()
        records.filterIsInstance<SpeedRecord>() shouldBe emptyList()
        records.filterIsInstance<TotalCaloriesBurnedRecord>() shouldBe emptyList()
        records.filterIsInstance<ElevationGainedRecord>() shouldBe emptyList()
    }
}
}
