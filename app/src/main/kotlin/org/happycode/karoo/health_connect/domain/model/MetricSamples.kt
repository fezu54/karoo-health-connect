package org.happycode.karoo.health_connect.domain.model

import java.time.Instant

data class HeartRateSample(
    val timestamp: Instant,
    val beatsPerMinute: Int,
)

data class PowerSample(
    val timestamp: Instant,
    val watts: Int,
)

data class CadenceSample(
    val timestamp: Instant,
    val revolutionsPerMinute: Int,
)

data class SpeedSample(
    val timestamp: Instant,
    val metersPerSecond: Double,
)
