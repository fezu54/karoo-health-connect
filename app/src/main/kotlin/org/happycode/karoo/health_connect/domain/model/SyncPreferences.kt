package org.happycode.karoo.health_connect.domain.model

data class SyncPreferences(
    val syncHeartRate: Boolean = true,
    val syncPower: Boolean = true,
    val syncCadence: Boolean = true,
    val syncSpeed: Boolean = true,
    val syncCalories: Boolean = true,
    val syncElevation: Boolean = true,
    val syncRoute: Boolean = true,
)
