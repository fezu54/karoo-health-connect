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
    val statusMessage: String? = null,
    val errorMessage: String? = null,
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
                        errorMessage = "OAuth state mismatch. Authentication aborted.",
                    )
                }
                return@launch
            }

            Log.d("KarooAuth", "Exchanging authorization code...")
            val success = rideSourceGateway.exchangeAuthorizationCode(code, verifier)
            Log.d("KarooAuth", "Exchange authorization code result: $success")
            _uiState.update { current ->
                if (success) {
                    current.copy(
                        isAuthenticated = true,
                        isSyncing = false,
                        statusMessage = "Connected to SRAM ID successfully",
                    )
                } else {
                    current.copy(
                        isAuthenticated = false,
                        isSyncing = false,
                        errorMessage = "Failed to authenticate with SRAM ID",
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
                errorMessage = "SRAM authentication failed: $error",
            )
        }
    }

    fun disconnect() {
        tokenStorage.clearToken()
        _uiState.update { it.copy(isAuthenticated = false, statusMessage = "Disconnected from SRAM") }
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
                            statusMessage = "Synced ${result.syncedCount} ride(s) successfully",
                        )
                    }
                }
                is SyncResult.Failure -> {
                    val message = when (val error = result.error) {
                        is SyncError.AuthenticationFailed -> "Authentication failed. Please re-login."
                        is SyncError.HealthConnectUnavailable -> "Health Connect is not available on this device."
                        is SyncError.NetworkError -> "Network error: ${error.message}"
                        is SyncError.ParseError -> "Failed to process ride: ${error.message}"
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
}
