package org.happycode.karoo.health_connect.adapters.ui

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.happycode.karoo.health_connect.R
import org.happycode.karoo.health_connect.adapters.sram.TokenStorage
import org.happycode.karoo.health_connect.application.SyncRidesUseCase
import org.happycode.karoo.health_connect.domain.gateway.HealthConnectGateway
import org.happycode.karoo.health_connect.domain.gateway.RideSourceGateway
import org.happycode.karoo.health_connect.domain.gateway.SyncPreferencesRepository
import org.happycode.karoo.health_connect.domain.model.SyncError
import org.happycode.karoo.health_connect.domain.model.SyncPreferences
import org.happycode.karoo.health_connect.domain.model.SyncResult

@OptIn(ExperimentalCoroutinesApi::class)
class SyncDashboardViewModelTest : FreeSpec() {
    init {
        isolationMode = IsolationMode.InstancePerTest

        val testDispatcher = StandardTestDispatcher()

        beforeSpec {
            Dispatchers.setMain(testDispatcher)
        }

        afterSpec {
            Dispatchers.resetMain()
        }

        val rideSourceGateway = mockk<RideSourceGateway>()
        val tokenStorage = mockk<TokenStorage>(relaxed = true)
        val healthConnectGateway = mockk<HealthConnectGateway>()
        val preferencesRepository = mockk<SyncPreferencesRepository>(relaxed = true)
        val syncRidesUseCase = mockk<SyncRidesUseCase>()

        fun createViewModel(): SyncDashboardViewModel {
            coEvery { healthConnectGateway.isAvailable() } returns true
            every { tokenStorage.getToken() } returns null
            every { preferencesRepository.getPreferences() } returns SyncPreferences()
            every { preferencesRepository.getLastSyncTimestamp() } returns null
            coEvery { syncRidesUseCase.execute() } returns SyncResult.Success(syncedCount = 0)

            val viewModel = SyncDashboardViewModel(
                rideSourceGateway = rideSourceGateway,
                tokenStorage = tokenStorage,
                healthConnectGateway = healthConnectGateway,
                preferencesRepository = preferencesRepository,
                syncRidesUseCase = syncRidesUseCase,
            )
            testDispatcher.scheduler.advanceUntilIdle()
            return viewModel
        }

        "should load initial state correctly on initialization" {
            //given / when
            val viewModel = createViewModel()

            //then
            viewModel.uiState.value.isAuthenticated shouldBe false
            viewModel.uiState.value.isHealthConnectAvailable shouldBe true
            viewModel.uiState.value.preferences shouldBe SyncPreferences()
        }

        "should prepare authorization and return authorization url when startAuthorization called" {
            //given
            val viewModel = createViewModel()
            every { rideSourceGateway.getAuthorizationUrl(any(), any()) } returns "https://api.hammerhead.io/oauth/authorize?state=123"

            //when
            val url = viewModel.startAuthorization()

            //then
            url shouldBe "https://api.hammerhead.io/oauth/authorize?state=123"
            viewModel.pendingAuthState shouldNotBe null
            viewModel.pendingCodeVerifier shouldNotBe null
        }

        "should exchange authorization code and update state and trigger sync on handleAuthCallback" {
            //given
            val viewModel = createViewModel()
            viewModel.pendingAuthState = "expected_state"
            viewModel.pendingCodeVerifier = "expected_verifier"
            coEvery { rideSourceGateway.exchangeAuthorizationCode("auth_code_123", "expected_verifier") } returns true
            coEvery { syncRidesUseCase.execute() } returns SyncResult.Success(syncedCount = 1)

            //when
            viewModel.handleAuthCallback(code = "auth_code_123", state = "expected_state")
            testDispatcher.scheduler.advanceUntilIdle()

            //then
            viewModel.uiState.value.isAuthenticated shouldBe true
            coVerify(atLeast = 1) { syncRidesUseCase.execute() }
            viewModel.uiState.value.statusMessage shouldBe UiText.StringResource(R.string.status_synced_rides, 1)
            viewModel.uiState.value.errorMessage shouldBe null
        }

        "should update state to error when handleAuthCallback has mismatched state" {
            //given
            val viewModel = createViewModel()
            viewModel.pendingAuthState = "expected_state"
            viewModel.pendingCodeVerifier = "expected_verifier"

            //when
            viewModel.handleAuthCallback(code = "auth_code_123", state = "different_state")
            testDispatcher.scheduler.advanceUntilIdle()

            //then
            viewModel.uiState.value.isAuthenticated shouldBe false
            viewModel.uiState.value.errorMessage shouldBe UiText.StringResource(R.string.error_oauth_state_mismatch)
        }

        "should update state to error when code exchange fails on handleAuthCallback" {
            //given
            val viewModel = createViewModel()
            viewModel.pendingAuthState = "expected_state"
            viewModel.pendingCodeVerifier = "expected_verifier"
            coEvery { rideSourceGateway.exchangeAuthorizationCode("bad_code", "expected_verifier") } returns false

            //when
            viewModel.handleAuthCallback(code = "bad_code", state = "expected_state")
            testDispatcher.scheduler.advanceUntilIdle()

            //then
            viewModel.uiState.value.isAuthenticated shouldBe false
            viewModel.uiState.value.errorMessage shouldBe UiText.StringResource(R.string.error_sram_auth_failed)
        }

        "should update error message when handleAuthError called" {
            //given
            val viewModel = createViewModel()

            //when
            viewModel.handleAuthError("access_denied")

            //then
            viewModel.uiState.value.isAuthenticated shouldBe false
            viewModel.uiState.value.errorMessage shouldBe UiText.StringResource(R.string.error_sram_auth_failed_with_reason, "access_denied")
        }

        "should clear token and update state when disconnect called" {
            //given
            val viewModel = createViewModel()

            //when
            viewModel.disconnect()

            //then
            coVerify(exactly = 1) { tokenStorage.clearToken() }
            viewModel.uiState.value.isAuthenticated shouldBe false
            viewModel.uiState.value.statusMessage shouldBe UiText.StringResource(R.string.status_disconnected_sram)
        }

        "should update state and persist preferences when updatePreferences called" {
            //given
            val viewModel = createViewModel()
            val updated = SyncPreferences(syncHeartRate = false)

            //when
            viewModel.updatePreferences(updated)

            //then
            coVerify(exactly = 1) { preferencesRepository.updatePreferences(updated) }
            viewModel.uiState.value.preferences shouldBe updated
        }

        "should update status message with synced count when syncNow succeeds" {
            //given
            val viewModel = createViewModel()
            coEvery { syncRidesUseCase.execute() } returns SyncResult.Success(syncedCount = 3)
            every { preferencesRepository.getLastSyncTimestamp() } returns Instant.parse("2026-11-09T08:00:00Z")

            //when
            viewModel.syncNow()
            testDispatcher.scheduler.advanceUntilIdle()

            //then
            viewModel.uiState.value.isSyncing shouldBe false
            viewModel.uiState.value.statusMessage shouldBe UiText.StringResource(R.string.status_synced_rides, 3)
            viewModel.uiState.value.errorMessage shouldBe null
        }

        "should update error message when syncNow fails" {
            //given
            val viewModel = createViewModel()
            coEvery { syncRidesUseCase.execute() } returns SyncResult.Failure(SyncError.NetworkError("Timeout"))

            //when
            viewModel.syncNow()
            testDispatcher.scheduler.advanceUntilIdle()

            //then
            viewModel.uiState.value.isSyncing shouldBe false
            viewModel.uiState.value.errorMessage shouldBe UiText.StringResource(R.string.error_network, "Timeout")
        }

        "should clear sync history and trigger syncNow when resetSyncHistoryAndSync called" {
            //given
            val viewModel = createViewModel()
            coEvery { syncRidesUseCase.execute() } returns SyncResult.Success(syncedCount = 2)

            //when
            viewModel.resetSyncHistoryAndSync()
            testDispatcher.scheduler.advanceUntilIdle()

            //then
            coVerify(exactly = 1) { preferencesRepository.clearSyncHistory() }
            coVerify(atLeast = 1) { syncRidesUseCase.execute() }
            viewModel.uiState.value.statusMessage shouldBe UiText.StringResource(R.string.status_synced_rides, 2)
        }
    }
}
