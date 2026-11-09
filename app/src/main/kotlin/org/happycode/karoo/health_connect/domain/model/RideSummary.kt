package org.happycode.karoo.health_connect.domain.model

import java.time.Instant

data class RideSummary(
    val id: RideId,
    val title: String,
    val startTime: Instant,
)
