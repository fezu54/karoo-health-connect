package org.happycode.karoo.health_connect.adapters.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.happycode.karoo.health_connect.R
import org.happycode.karoo.health_connect.adapters.sram.PkceGenerator
import org.happycode.karoo.health_connect.adapters.sram.TokenStorage
import org.happycode.karoo.health_connect.application.SyncRidesUseCase
import org.happycode.karoo.health_connect.domain.gateway.HealthConnectGateway
import org.happycode.karoo.health_connect.domain.gateway.RideSourceGateway
import org.happycode.karoo.health_connect.domain.gateway.SyncPreferencesRepository
import org.happycode.karoo.health_connect.domain.model.SyncError
import org.happycode.karoo.health_connect.domain.model.SyncPreferences
import org.happycode.karoo.health_connect.domain.model.SyncResult

data class SyncDashboardUiState(
    val isAuthenticated: Boolean = false,
    val isHealthConnectAvailable: Boolean = false,
    val isSyncing: Boolean = false,
    val preferences: SyncPreferences = SyncPreferences(),
    val lastSyncTimestamp: Instant? = null,
    val statusMessage: UiText? = null,
    val errorMessage: UiText? = null,
)

class SyncDashboardViewModel(
    private val rideSourceGateway: RideSourceGateway,
    private val tokenStorage: TokenStorage,
    private val healthConnectGateway: HealthConnectGateway,
    private val preferencesRepository: SyncPreferencesRepository,
    private val syncRidesUseCase: SyncRidesUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SyncDashboardUiState())
    val uiState: StateFlow<SyncDashboardUiState> = _uiState.asStateFlow()

    internal var pendingAuthState: String? = null
    internal var pendingCodeVerifier: String? = null

    init {
        loadInitialState()
    }

    fun loadInitialState() {
        viewModelScope.launch {
            val isAvailable = healthConnectGateway.isAvailable()
            val hasToken = tokenStorage.getToken() != null
            val preferences = preferencesRepository.getPreferences()
            val lastSync = preferencesRepository.getLastSyncTimestamp()

            _uiState.update { current ->
                current.copy(
                    isAuthenticated = hasToken,
                    isHealthConnectAvailable = isAvailable,
                    preferences = preferences,
                    lastSyncTimestamp = lastSync,
                )
            }
        }
    }

    fun startAuthorization(): String {
        val verifier = PkceGenerator.generateCodeVerifier()
        val challenge = PkceGenerator.generateCodeChallenge(verifier)
        val state = UUID.randomUUID().toString()
        pendingAuthState = state
        pendingCodeVerifier = verifier
        Log.d("KarooAuth", "startAuthorization generated state=$state, verifierPresent=true")
        return rideSourceGateway.getAuthorizationUrl(state, challenge)
    }

    fun handleAuthCallback(code: String, state: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, errorMessage = null) }
            val expectedState = pendingAuthState
            val verifier = pendingCodeVerifier
            Log.d("KarooAuth", "handleAuthCallback: state=$state, expectedState=$expectedState, verifierPresent=${verifier != null}")
            pendingAuthState = null
            pendingCodeVerifier = null

            if ((expectedState != null) && (state != null) && (expectedState != state)) {
                Log.w("KarooAuth", "OAuth state mismatch: expected $expectedState but got $state")
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        errorMessage = UiText.StringResource(R.string.error_oauth_state_mismatch),
                    )
                }
                return@launch
            }

            Log.d("KarooAuth", "Exchanging authorization code...")
            val success = rideSourceGateway.exchangeAuthorizationCode(code, verifier)
            Log.d("KarooAuth", "Exchange authorization code result: $success")
            if (success) {
                _uiState.update { current ->
                    current.copy(
                        isAuthenticated = true,
                        isSyncing = false,
                        statusMessage = UiText.StringResource(R.string.status_sram_connected),
                    )
                }
                syncNow()
            } else {
                _uiState.update { current ->
                    current.copy(
                        isAuthenticated = false,
                        isSyncing = false,
                        errorMessage = UiText.StringResource(R.string.error_sram_auth_failed),
                    )
                }
            }
        }
    }

    fun handleAuthError(error: String) {
        pendingAuthState = null
        pendingCodeVerifier = null
        _uiState.update {
            it.copy(
                isSyncing = false,
                errorMessage = UiText.StringResource(R.string.error_sram_auth_failed_with_reason, error),
            )
        }
    }

    fun disconnect() {
        tokenStorage.clearToken()
        _uiState.update { it.copy(isAuthenticated = false, statusMessage = UiText.StringResource(R.string.status_disconnected_sram)) }
    }

    fun updatePreferences(newPreferences: SyncPreferences) {
        preferencesRepository.updatePreferences(newPreferences)
        _uiState.update { it.copy(preferences = newPreferences) }
    }

    fun syncNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, statusMessage = null, errorMessage = null) }
            when (val result = syncRidesUseCase.execute()) {
                is SyncResult.Success -> {
                    _uiState.update { current ->
                        current.copy(
                            isSyncing = false,
                            lastSyncTimestamp = preferencesRepository.getLastSyncTimestamp(),
                            statusMessage = UiText.StringResource(R.string.status_synced_rides, result.syncedCount),
                        )
                    }
                }
                is SyncResult.Failure -> {
                    val message = when (val error = result.error) {
                        is SyncError.AuthenticationFailed -> UiText.StringResource(R.string.error_auth_failed_relogin)
                        is SyncError.HealthConnectUnavailable -> UiText.StringResource(R.string.health_connect_not_available)
                        is SyncError.NetworkError -> UiText.StringResource(R.string.error_network, error.message)
                        is SyncError.ParseError -> UiText.StringResource(R.string.error_process_ride, error.message)
                    }
                    _uiState.update { current ->
                        current.copy(
                            isSyncing = false,
                            errorMessage = message,
                        )
                    }
                }
            }
        }
    }

    fun resetSyncHistoryAndSync() {
        preferencesRepository.clearSyncHistory()
        _uiState.update { it.copy(lastSyncTimestamp = null) }
        syncNow()
    }
}
