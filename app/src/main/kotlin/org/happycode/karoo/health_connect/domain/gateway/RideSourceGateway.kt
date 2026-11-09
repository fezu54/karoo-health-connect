package org.happycode.karoo.health_connect.domain.gateway

import java.io.InputStream
import java.time.Instant
import org.happycode.karoo.health_connect.domain.model.RideId
import org.happycode.karoo.health_connect.domain.model.RideSummary

interface RideSourceGateway {
    fun getAuthorizationUrl(state: String, codeChallenge: String? = null): String
    suspend fun exchangeAuthorizationCode(code: String, codeVerifier: String? = null): Boolean
    suspend fun fetchUnsyncedRides(since: Instant): List<RideSummary>
    suspend fun fetchRawFitStream(rideId: RideId): InputStream
}
