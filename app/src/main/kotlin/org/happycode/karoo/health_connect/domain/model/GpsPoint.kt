package org.happycode.karoo.health_connect.domain.model

import java.time.Instant

data class GpsPoint(
    val timestamp: Instant,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
)
