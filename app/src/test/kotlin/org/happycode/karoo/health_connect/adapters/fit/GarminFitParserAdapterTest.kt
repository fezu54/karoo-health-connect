package org.happycode.karoo.health_connect.adapters.fit

import com.garmin.fit.BufferEncoder
import com.garmin.fit.DateTime
import com.garmin.fit.File
import com.garmin.fit.FileIdMesg
import com.garmin.fit.Fit
import com.garmin.fit.Manufacturer
import com.garmin.fit.RecordMesg
import com.garmin.fit.SessionMesg
import com.garmin.fit.Sport
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.Date

class GarminFitParserAdapterTest : FreeSpec() {
    init {
        val adapter = GarminFitParserAdapter()

    "should parse ride aggregate with telemetry from binary fit stream when valid stream provided" {
        //given
        val startTime = Instant.parse("2026-11-09T08:00:00Z")
        val encoder = BufferEncoder(Fit.ProtocolVersion.V2_0)

        val fileIdMesg = FileIdMesg().apply {
            type = File.ACTIVITY
            manufacturer = Manufacturer.DEVELOPMENT
            serialNumber = 987654L
            timeCreated = DateTime(Date.from(startTime))
        }
        encoder.write(fileIdMesg)

        val sessionMesg = SessionMesg().apply {
            this.startTime = DateTime(Date.from(startTime))
            totalElapsedTime = 3600f
            totalTimerTime = 3600f
            totalDistance = 30000f
            totalCalories = 750
            totalAscent = 250
            sport = Sport.CYCLING
        }
        encoder.write(sessionMesg)

        val recordMesg = RecordMesg().apply {
            timestamp = DateTime(Date.from(startTime))
            heartRate = 150
            power = 240
            cadence = 90
            speed = 8.33f
            positionLat = 565257912
            positionLong = 101869824
            altitude = 410.5f
        }
        encoder.write(recordMesg)

        val fitBytes = encoder.close()
        val fitInputStream = ByteArrayInputStream(fitBytes)

        //when
        val (_, title, parsedStartTime, endTime, _, metrics) = adapter.parse(fitInputStream)

        //then
        parsedStartTime shouldBe startTime
        endTime shouldBe startTime.plusSeconds(3600)
        title shouldBe "Cycling"
        metrics.totalDistanceMeters shouldBe 30000.0
        metrics.totalCalories shouldBe 750
        metrics.totalElevationGainMeters shouldBe 250.0

        metrics.heartRates.size shouldBe 1
        metrics.heartRates.first().beatsPerMinute shouldBe 150

        metrics.powers.size shouldBe 1
        metrics.powers.first().watts shouldBe 240

        metrics.cadences.size shouldBe 1
        metrics.cadences.first().revolutionsPerMinute shouldBe 90

        metrics.speeds.size shouldBe 1
        metrics.speeds.first().metersPerSecond shouldBe (8.33 plusOrMinus 0.01)

        metrics.route.size shouldBe 1
        val (_, lat, long, alt) = metrics.route.first()
        lat shouldBe (47.3769 plusOrMinus 0.01)
        long shouldBe (8.5417 plusOrMinus 0.01)
        alt shouldBe (410.5 plusOrMinus 0.2)
    }

    "should parse ride gracefully when telemetry records have null or missing sensors" {
        //given
        val startTime = Instant.parse("2026-11-09T09:00:00Z")
        val encoder = BufferEncoder(Fit.ProtocolVersion.V2_0)

        val sessionMesg = SessionMesg().apply {
            this.startTime = DateTime(Date.from(startTime))
            totalElapsedTime = 1800f
            totalDistance = 15000f
            sport = Sport.CYCLING
        }
        encoder.write(sessionMesg)

        val recordMesg = RecordMesg().apply {
            timestamp = DateTime(Date.from(startTime))
            speed = 7.0f
        }
        encoder.write(recordMesg)

        val fitBytes = encoder.close()
        val fitInputStream = ByteArrayInputStream(fitBytes)

        //when
        val (_, _, _, _, _, metrics) = adapter.parse(fitInputStream)

        //then
        metrics.heartRates shouldBe emptyList()
        metrics.powers shouldBe emptyList()
        metrics.cadences shouldBe emptyList()
        metrics.route shouldBe emptyList()
        metrics.speeds.size shouldBe 1
        metrics.speeds.first().metersPerSecond shouldBe (7.0 plusOrMinus 0.01)
    }
}
}
