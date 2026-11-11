package org.happycode.karoo.health_connect

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.CyclingPedalingCadenceRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.happycode.karoo.health_connect.adapters.ui.SyncDashboardScreen
import org.happycode.karoo.health_connect.adapters.ui.SyncDashboardViewModel
import org.happycode.karoo.health_connect.frameworks.KarooApplication
import org.happycode.karoo.health_connect.ui.theme.KaroohealthconnectTheme

import androidx.core.net.toUri

class MainActivity : ComponentActivity() {

    private val viewModel: SyncDashboardViewModel by viewModels {
        val appContainer = (application as KarooApplication).appContainer
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SyncDashboardViewModel(
                    appContainer.rideSourceGateway,
                    appContainer.tokenStorage,
                    appContainer.healthConnectGateway,
                    appContainer.preferencesRepository,
                    appContainer.syncRidesUseCase,
                ) as T
        }
    }

    private val requestPermissionsLauncher =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) {
            viewModel.loadInitialState()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleAuthIntent(intent)

        setContent {
            KaroohealthconnectTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                SyncDashboardScreen(
                    uiState = uiState,
                    onConnectSram = ::connectSram,
                    onDisconnect = viewModel::disconnect,
                    onUpdatePreferences = viewModel::updatePreferences,
                    onRequestHealthPermissions = ::requestHealthPermissions,
                    onSyncNow = viewModel::syncNow,
                    onResetSync = viewModel::resetSyncHistoryAndSync,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthIntent(intent)
    }

    private fun connectSram() {
        val authUrl = viewModel.startAuthorization()
        val browserIntent = Intent(Intent.ACTION_VIEW, authUrl.toUri())
        startActivity(browserIntent)
    }

    private fun handleAuthIntent(intent: Intent?) {
        intent?.data?.let { uri ->
            Log.d("KarooAuth", "handleAuthIntent received URI: $uri (scheme=${uri.scheme}, host=${uri.host})")
            if ((uri.scheme == "karoo-health-connect") && (uri.host == "oauth2callback")) {
                val code = uri.getQueryParameter("code")
                val state = uri.getQueryParameter("state")
                val error = uri.getQueryParameter("error")
                Log.d("KarooAuth", "handleAuthIntent params: code=${code != null}, state=$state, error=$error")
                when {
                    code != null -> viewModel.handleAuthCallback(code, state)
                    error != null -> viewModel.handleAuthError(error)
                }
            } else {
                Log.w("KarooAuth", "handleAuthIntent ignored non-matching URI: $uri")
            }
        }
    }

    private fun requestHealthPermissions() {
        val permissions = setOf(
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
            HealthPermission.getWritePermission(DistanceRecord::class),
            HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getWritePermission(ElevationGainedRecord::class),
            HealthPermission.getWritePermission(HeartRateRecord::class),
            HealthPermission.getWritePermission(PowerRecord::class),
            HealthPermission.getWritePermission(CyclingPedalingCadenceRecord::class),
            HealthPermission.getWritePermission(SpeedRecord::class),
            HealthPermission.PERMISSION_WRITE_EXERCISE_ROUTE,
        )
        requestPermissionsLauncher.launch(permissions)
    }
}
