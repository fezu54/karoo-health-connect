package org.happycode.karoo.health_connect.adapters.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.happycode.karoo.health_connect.domain.model.SyncPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncDashboardScreen(
    uiState: SyncDashboardUiState,
    onConnectSram: () -> Unit,
    onDisconnect: () -> Unit,
    onUpdatePreferences: (SyncPreferences) -> Unit,
    onRequestHealthPermissions: () -> Unit,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Karoo Health Connect", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SramConnectionCard(
                isAuthenticated = uiState.isAuthenticated,
                isSyncing = uiState.isSyncing,
                onConnectSram = onConnectSram,
                onDisconnect = onDisconnect,
            )

            HealthConnectStatusCard(
                isAvailable = uiState.isHealthConnectAvailable,
                onRequestPermissions = onRequestHealthPermissions,
            )

            SyncMetricsCard(
                preferences = uiState.preferences,
                onUpdatePreferences = onUpdatePreferences,
            )

            SyncActionCard(
                isSyncing = uiState.isSyncing,
                lastSyncTimestamp = uiState.lastSyncTimestamp?.let {
                    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                        .withZone(ZoneId.systemDefault())
                        .format(it)
                },
                statusMessage = uiState.statusMessage,
                errorMessage = uiState.errorMessage,
                onSyncNow = onSyncNow,
            )
        }
    }
}

@Composable
private fun SramConnectionCard(
    isAuthenticated: Boolean,
    isSyncing: Boolean,
    onConnectSram: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "SRAM / Hammerhead Account",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (isAuthenticated) {
                Text("Connected with SRAM ID", color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = onDisconnect) {
                    Text("Disconnect")
                }
            } else {
                Text(
                    text = "Sign in using your SRAM ID via the secure OpenID Connect / OAuth flow.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onConnectSram,
                    enabled = !isSyncing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Connect SRAM Account")
                }
            }
        }
    }
}

@Composable
private fun HealthConnectStatusCard(
    isAvailable: Boolean,
    onRequestPermissions: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Google Health Connect", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            val statusText = when {
                isAvailable -> "Health Connect is installed and available."
                else -> "Health Connect is not available on this device."
            }
            Text(statusText, style = MaterialTheme.typography.bodyMedium)

            if (isAvailable) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = onRequestPermissions) {
                    Text("Check / Grant Permissions")
                }
            }
        }
    }
}

@Composable
private fun SyncMetricsCard(
    preferences: SyncPreferences,
    onUpdatePreferences: (SyncPreferences) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Metrics to Sync", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            PreferenceToggle("Heart Rate", preferences.syncHeartRate) {
                onUpdatePreferences(preferences.copy(syncHeartRate = it))
            }
            PreferenceToggle("Power", preferences.syncPower) {
                onUpdatePreferences(preferences.copy(syncPower = it))
            }
            PreferenceToggle("Cadence", preferences.syncCadence) {
                onUpdatePreferences(preferences.copy(syncCadence = it))
            }
            PreferenceToggle("Speed", preferences.syncSpeed) {
                onUpdatePreferences(preferences.copy(syncSpeed = it))
            }
            PreferenceToggle("Calories", preferences.syncCalories) {
                onUpdatePreferences(preferences.copy(syncCalories = it))
            }
            PreferenceToggle("Elevation Gain", preferences.syncElevation) {
                onUpdatePreferences(preferences.copy(syncElevation = it))
            }
            PreferenceToggle("GPS Exercise Route", preferences.syncRoute) {
                onUpdatePreferences(preferences.copy(syncRoute = it))
            }
        }
    }
}

@Composable
private fun PreferenceToggle(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SyncActionCard(
    isSyncing: Boolean,
    lastSyncTimestamp: String?,
    statusMessage: String?,
    errorMessage: String?,
    onSyncNow: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Sync Activities", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            Text("Last synced: ${lastSyncTimestamp ?: "Never"}", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onSyncNow,
                enabled = !isSyncing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Sync Now")
                }
            }

            statusMessage?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            }

            errorMessage?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
