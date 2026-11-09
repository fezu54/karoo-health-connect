package org.happycode.karoo.health_connect.domain.model

data class RideMetrics(
    val heartRates: List<HeartRateSample> = emptyList(),
    val powers: List<PowerSample> = emptyList(),
    val cadences: List<CadenceSample> = emptyList(),
    val speeds: List<SpeedSample> = emptyList(),
    val route: List<GpsPoint> = emptyList(),
    val totalDistanceMeters: Double? = null,
    val totalElevationGainMeters: Double? = null,
    val totalCalories: Int? = null,
)
