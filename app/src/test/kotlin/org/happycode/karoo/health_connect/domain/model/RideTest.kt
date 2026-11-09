package org.happycode.karoo.health_connect.domain.model

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.ZoneOffset

class RideTest : FreeSpec() {
    init {
        "should initialize ride aggregate with correct values when valid data provided" {
            //given
            val rideId = RideId("activity-100")
            val startTime = Instant.parse("2026-11-09T08:00:00Z")
            val endTime = Instant.parse("2026-11-09T10:00:00Z")
            val zoneOffset = ZoneOffset.UTC
            val heartRate = HeartRateSample(timestamp = startTime, beatsPerMinute = 145)
            val power = PowerSample(timestamp = startTime, watts = 220)
            val cadence = CadenceSample(timestamp = startTime, revolutionsPerMinute = 88)
            val speed = SpeedSample(timestamp = startTime, metersPerSecond = 8.5)
            val gps = GpsPoint(latitude = 47.3769, longitude = 8.5417, altitudeMeters = 408.0, timestamp = startTime)

            val metrics = RideMetrics(
                heartRates = listOf(heartRate),
                powers = listOf(power),
                cadences = listOf(cadence),
                speeds = listOf(speed),
                route = listOf(gps),
                totalDistanceMeters = 52000.0,
                totalElevationGainMeters = 650.0,
                totalCalories = 1200,
            )

            //when
            val (id, title, _, _, _, rideMetrics) = Ride(
                id = rideId,
                title = "Morning Gravel Ride",
                startTime = startTime,
                endTime = endTime,
                zoneOffset = zoneOffset,
                metrics = metrics,
            )

            //then
            id.value shouldBe "activity-100"
            title shouldBe "Morning Gravel Ride"
            rideMetrics.heartRates.size shouldBe 1
            rideMetrics.powers.first().watts shouldBe 220
            rideMetrics.totalDistanceMeters shouldBe 52000.0
        }

        "should default all metric sync flags to true when sync preferences initialized" {
            //given / when
            val (hr, pwr, cad, spd, cal, elev, rte) = SyncPreferences()

            //then
            hr shouldBe true
            pwr shouldBe true
            cad shouldBe true
            spd shouldBe true
            cal shouldBe true
            elev shouldBe true
            rte shouldBe true
        }
    }
}
