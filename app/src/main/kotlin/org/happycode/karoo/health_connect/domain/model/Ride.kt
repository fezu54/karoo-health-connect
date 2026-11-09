package org.happycode.karoo.health_connect.domain.model

import java.time.Instant
import java.time.ZoneOffset

data class Ride(
    val id: RideId,
    val title: String,
    val startTime: Instant,
    val endTime: Instant,
    val zoneOffset: ZoneOffset,
    val metrics: RideMetrics,
)
