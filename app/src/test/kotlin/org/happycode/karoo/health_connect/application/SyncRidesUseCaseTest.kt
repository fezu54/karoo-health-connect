package org.happycode.karoo.health_connect.application

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import org.happycode.karoo.health_connect.domain.gateway.FitParser
import org.happycode.karoo.health_connect.domain.gateway.HealthConnectGateway
import org.happycode.karoo.health_connect.domain.gateway.RideSourceGateway
import org.happycode.karoo.health_connect.domain.gateway.SyncPreferencesRepository
import org.happycode.karoo.health_connect.domain.model.Ride
import org.happycode.karoo.health_connect.domain.model.RideId
import org.happycode.karoo.health_connect.domain.model.RideMetrics
import org.happycode.karoo.health_connect.domain.model.RideSummary
import org.happycode.karoo.health_connect.domain.model.SyncError
import org.happycode.karoo.health_connect.domain.model.SyncPreferences
import org.happycode.karoo.health_connect.domain.model.SyncResult

class SyncRidesUseCaseTest : FreeSpec() {
    init {
        isolationMode = IsolationMode.InstancePerTest

    val rideSourceGateway = mockk<RideSourceGateway>()
    val fitParser = mockk<FitParser>()
    val healthConnectGateway = mockk<HealthConnectGateway>()
    val preferencesRepository = mockk<SyncPreferencesRepository>()

    val useCase = SyncRidesUseCase(
        rideSourceGateway,
        fitParser,
        healthConnectGateway,
        preferencesRepository,
    )

    "should return HealthConnectUnavailable when Health Connect is not available on device" {
        //given
        coEvery { healthConnectGateway.isAvailable() } returns false

        //when
        val result = useCase.execute()

        //then
        result shouldBe SyncResult.Failure(SyncError.HealthConnectUnavailable)
    }

    "should return NetworkError when fetching ride summaries fails with network exception" {
        //given
        coEvery { healthConnectGateway.isAvailable() } returns true
        coEvery { preferencesRepository.getPreferences() } returns SyncPreferences()
        coEvery { preferencesRepository.getLastSyncTimestamp() } returns Instant.EPOCH
        coEvery { rideSourceGateway.fetchUnsyncedRides(any()) } throws IOException("Connection refused")

        //when
        val result = useCase.execute()

        //then
        result shouldBe SyncResult.Failure(SyncError.NetworkError("Connection refused"))
    }

    "should stream and upsert rides to Health Connect when new rides are available" {
        //given
        val rideId = RideId("ride-456")
        val timestamp = Instant.parse("2026-11-09T08:00:00Z")
        val summary = RideSummary(rideId, "Sunday Group Ride", timestamp)
        val stream = ByteArrayInputStream(byteArrayOf(1, 2, 3))
        val preferences = SyncPreferences(syncPower = true, syncHeartRate = true)
        val ride = Ride(
            id = rideId,
            title = "Sunday Group Ride",
            startTime = timestamp,
            endTime = timestamp.plusSeconds(3600),
            zoneOffset = ZoneOffset.UTC,
            metrics = RideMetrics(),
        )

        coEvery { healthConnectGateway.isAvailable() } returns true
        coEvery { preferencesRepository.getPreferences() } returns preferences
        coEvery { preferencesRepository.getLastSyncTimestamp() } returns timestamp.minusSeconds(86400)
        coEvery { rideSourceGateway.fetchUnsyncedRides(any()) } returns listOf(summary)
        coEvery { preferencesRepository.isRideSynced(rideId) } returns false
        coEvery { rideSourceGateway.fetchRawFitStream(rideId) } returns stream
        coEvery { fitParser.parse(stream) } returns ride
        coEvery { healthConnectGateway.upsertRide(ride, preferences) } just Runs
        coEvery { preferencesRepository.markRideSynced(rideId, timestamp) } just Runs

        //when
        val result = useCase.execute()

        //then
        result shouldBe SyncResult.Success(syncedCount = 1)
        coVerify(exactly = 1) { healthConnectGateway.upsertRide(ride, preferences) }
        coVerify(exactly = 1) { preferencesRepository.markRideSynced(rideId, timestamp) }
    }

    "should skip already synced rides when checking ride sync state" {
        //given
        val rideId = RideId("ride-already-synced")
        val timestamp = Instant.parse("2026-11-09T08:00:00Z")
        val summary = RideSummary(rideId, "Already Synced Ride", timestamp)

        coEvery { healthConnectGateway.isAvailable() } returns true
        coEvery { preferencesRepository.getPreferences() } returns SyncPreferences()
        coEvery { preferencesRepository.getLastSyncTimestamp() } returns timestamp.minusSeconds(86400)
        coEvery { rideSourceGateway.fetchUnsyncedRides(any()) } returns listOf(summary)
        coEvery { preferencesRepository.isRideSynced(rideId) } returns true

        //when
        val result = useCase.execute()

        //then
        result shouldBe SyncResult.Success(syncedCount = 0)
        coVerify(exactly = 0) { rideSourceGateway.fetchRawFitStream(any()) }
        coVerify(exactly = 0) { healthConnectGateway.upsertRide(any(), any()) }
    }

    "should return ParseError when downloading or parsing a fit file fails" {
        //given
        val rideId = RideId("ride-corrupt")
        val timestamp = Instant.parse("2026-11-09T08:00:00Z")
        val summary = RideSummary(rideId, "Corrupt Ride", timestamp)
        val stream = ByteArrayInputStream(byteArrayOf(9, 9, 9))

        coEvery { healthConnectGateway.isAvailable() } returns true
        coEvery { preferencesRepository.getPreferences() } returns SyncPreferences()
        coEvery { preferencesRepository.getLastSyncTimestamp() } returns timestamp.minusSeconds(86400)
        coEvery { rideSourceGateway.fetchUnsyncedRides(any()) } returns listOf(summary)
        coEvery { preferencesRepository.isRideSynced(rideId) } returns false
        coEvery { rideSourceGateway.fetchRawFitStream(rideId) } returns stream
        coEvery { fitParser.parse(stream) } throws IllegalArgumentException("Invalid FIT header")

        //when
        val result = useCase.execute()

        //then
        result shouldBe SyncResult.Failure(SyncError.ParseError("Invalid FIT header"))
    }

    "should process rides in chronological order ascending when summaries returned out of order" {
        //given
        val olderTime = Instant.parse("2026-11-08T08:00:00Z")
        val newerTime = Instant.parse("2026-11-09T08:00:00Z")
        val olderSummary = RideSummary(RideId("ride-old"), "Old Ride", olderTime)
        val newerSummary = RideSummary(RideId("ride-new"), "New Ride", newerTime)

        val processedOrder = mutableListOf<String>()

        coEvery { healthConnectGateway.isAvailable() } returns true
        coEvery { preferencesRepository.getPreferences() } returns SyncPreferences()
        coEvery { preferencesRepository.getLastSyncTimestamp() } returns Instant.EPOCH
        coEvery { rideSourceGateway.fetchUnsyncedRides(any()) } returns listOf(newerSummary, olderSummary)
        coEvery { preferencesRepository.isRideSynced(any()) } returns false
        coEvery { rideSourceGateway.fetchRawFitStream(any()) } answers { ByteArrayInputStream(byteArrayOf(1)) }
        coEvery { fitParser.parse(any()) } answers {
            Ride(
                id = RideId("mock"),
                title = "Mock",
                startTime = Instant.EPOCH,
                endTime = Instant.EPOCH,
                zoneOffset = ZoneOffset.UTC,
                metrics = RideMetrics(),
            )
        }
        coEvery { healthConnectGateway.upsertRide(any(), any()) } just Runs
        coEvery { preferencesRepository.markRideSynced(RideId("ride-old"), olderTime) } answers {
            processedOrder.add("ride-old")
        }
        coEvery { preferencesRepository.markRideSynced(RideId("ride-new"), newerTime) } answers {
            processedOrder.add("ride-new")
        }

        //when
        val result = useCase.execute()

        //then
        result shouldBe SyncResult.Success(syncedCount = 2)
        processedOrder shouldBe listOf("ride-old", "ride-new")
    }
}
}
