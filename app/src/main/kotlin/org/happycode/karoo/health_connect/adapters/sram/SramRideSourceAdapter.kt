package org.happycode.karoo.health_connect.adapters.sram

import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.happycode.karoo.health_connect.domain.gateway.RideSourceGateway
import org.happycode.karoo.health_connect.domain.model.RideId
import org.happycode.karoo.health_connect.domain.model.RideSummary

class SramRideSourceAdapter(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val tokenStorage: TokenStorage = InMemoryTokenStorage(),
    private val authorizeUrl: String = DEFAULT_AUTHORIZE_URL,
    private val tokenUrl: String = DEFAULT_TOKEN_URL,
    private val apiBaseUrl: String = DEFAULT_API_BASE_URL,
    private val clientId: String = DEFAULT_CLIENT_ID,
    private val clientSecret: String? = null,
    private val redirectUri: String = DEFAULT_REDIRECT_URI,
    private val scope: String = DEFAULT_SCOPE,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : RideSourceGateway {

    override fun getAuthorizationUrl(state: String, codeChallenge: String?): String {
        val encodedRedirect = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8.name())
        val encodedScope = URLEncoder.encode(scope, StandardCharsets.UTF_8.name())
        val base = "$authorizeUrl?response_type=code&client_id=$clientId&redirect_uri=$encodedRedirect&scope=$encodedScope&state=$state"
        return codeChallenge?.let {
            "$base&code_challenge=$it&code_challenge_method=S256"
        } ?: base
    }

    override suspend fun exchangeAuthorizationCode(code: String, codeVerifier: String?): Boolean =
        withContext(Dispatchers.IO) {
            val formBody = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("client_id", clientId)
                .add("code", code)
                .add("redirect_uri", redirectUri)
                .apply {
                    clientSecret?.let { add("client_secret", it) }
                    codeVerifier?.let { add("code_verifier", it) }
                }
                .build()

            Log.d(TAG, "Requesting token exchange from $tokenUrl for redirectUri: $redirectUri (codeVerifierPresent=${codeVerifier != null})")

            val request = Request.Builder()
                .url(tokenUrl)
                .post(formBody)
                .build()

            runCatching {
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body.string()
                    Log.d(TAG, "Token exchange HTTP ${response.code}: $body")
                    when {
                        response.isSuccessful -> {
                            val tokenResponse = json.decodeFromString(SramOAuthTokenResponse.serializer(), body)
                            val expiresAt = tokenResponse.expiresIn?.let {
                                Instant.now().plusSeconds(it).toEpochMilli()
                            }
                            tokenStorage.saveToken(
                                OAuthToken(
                                    accessToken = tokenResponse.accessToken,
                                    refreshToken = tokenResponse.refreshToken,
                                    expiresAt = expiresAt,
                                ),
                            )
                            Log.d(TAG, "OAuth tokens saved successfully")
                            true
                        }
                        else -> {
                            Log.w(TAG, "Token exchange failed with HTTP ${response.code}: $body")
                            false
                        }
                    }
                }
            }.onFailure { error ->
                Log.e(TAG, "Token exchange threw exception: ${error.message}", error)
            }.getOrDefault(defaultValue = false)
        }

    suspend fun refreshAccessToken(): Boolean =
        withContext(Dispatchers.IO) {
            val refreshToken = tokenStorage.getOAuthToken()?.refreshToken
                ?: return@withContext false

            val formBody = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", clientId)
                .add("refresh_token", refreshToken)
                .apply {
                    clientSecret?.let { add("client_secret", it) }
                }
                .build()

            Log.d(TAG, "Refreshing access token from $tokenUrl")

            val request = Request.Builder()
                .url(tokenUrl)
                .post(formBody)
                .build()

            runCatching {
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body.string()
                    Log.d(TAG, "Token refresh HTTP ${response.code}: $body")
                    when {
                        response.isSuccessful -> {
                            val tokenResponse = json.decodeFromString(SramOAuthTokenResponse.serializer(), body)
                            val expiresAt = tokenResponse.expiresIn?.let {
                                Instant.now().plusSeconds(it).toEpochMilli()
                            }
                            tokenStorage.saveToken(
                                OAuthToken(
                                    accessToken = tokenResponse.accessToken,
                                    refreshToken = tokenResponse.refreshToken ?: refreshToken,
                                    expiresAt = expiresAt,
                                ),
                            )
                            Log.d(TAG, "Token refreshed successfully")
                            true
                        }
                        else -> {
                            Log.w(TAG, "Token refresh failed with HTTP ${response.code}: $body")
                            false
                        }
                    }
                }
            }.onFailure { error ->
                Log.e(TAG, "Token refresh threw exception: ${error.message}", error)
            }.getOrDefault(defaultValue = false)
        }

    override suspend fun fetchUnsyncedRides(since: Instant): List<RideSummary> =
        withContext(Dispatchers.IO) {
            val token = getValidAccessToken()
            val url = "$apiBaseUrl/activities?perPage=100"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            executeWithAuthRetry(request) { response ->
                when {
                    response.isSuccessful -> {
                        val body = response.body.string()
                        parseActivities(body, since)
                    }
                    else -> throw IOException("Failed to fetch rides: HTTP ${response.code}")
                }
            }
        }

    override suspend fun fetchRawFitStream(rideId: RideId): InputStream =
        withContext(Dispatchers.IO) {
            val token = getValidAccessToken()
            val url = "$apiBaseUrl/activities/${rideId.value}/file"
            var request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            var response = httpClient.newCall(request).execute()
            if ((response.code == 401) && refreshAccessToken()) {
                response.close()
                val newToken = tokenStorage.getToken()
                request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $newToken")
                    .get()
                    .build()
                response = httpClient.newCall(request).execute()
            }

            when {
                response.isSuccessful -> response.body.byteStream()
                else -> {
                    response.close()
                    throw IOException("Failed to download FIT for ride ${rideId.value}: HTTP ${response.code}")
                }
            }
        }

    private fun parseActivities(body: String, since: Instant): List<RideSummary> {
        val activities = when (val root = json.parseToJsonElement(body)) {
            is JsonArray -> json.decodeFromJsonElement(ListSerializer(SramActivityDto.serializer()), root)
            is JsonObject -> root["data"]?.let {
                json.decodeFromJsonElement(ListSerializer(SramActivityDto.serializer()), it)
            } ?: emptyList()
            else -> emptyList()
        }

        return activities.mapNotNull { item ->
            val startTime = when {
                item.startedAt != null -> runCatching { Instant.parse(item.startedAt) }.getOrNull()
                item.startTime != null -> runCatching { Instant.parse(item.startTime) }.getOrNull()
                item.createdAt != null -> runCatching { Instant.parse(item.createdAt) }.getOrNull()
                item.startTimestamp != null -> Instant.ofEpochMilli(item.startTimestamp)
                else -> null
            } ?: Instant.now()

            if (startTime.isBefore(since)) {
                null
            } else {
                RideSummary(
                    id = RideId(item.id),
                    title = item.name ?: item.title ?: "Karoo Ride",
                    startTime = startTime,
                )
            }
        }
    }

    private suspend fun getValidAccessToken(): String {
        val current = tokenStorage.getOAuthToken()
            ?: throw IllegalStateException("Not authenticated with SRAM ID")

        val isExpired = current.expiresAt?.let {
            Instant.now().toEpochMilli() >= (it - 60_000)
        } ?: false

        if (isExpired && (current.refreshToken != null)) {
            if (refreshAccessToken()) {
                return tokenStorage.getToken()
                    ?: throw IllegalStateException("Failed to retrieve token after refresh")
            }
        }

        return current.accessToken
    }

    private suspend fun <T> executeWithAuthRetry(
        initialRequest: Request,
        block: (Response) -> T,
    ): T {
        var response = httpClient.newCall(initialRequest).execute()
        if ((response.code == 401) && refreshAccessToken()) {
            response.close()
            val newToken = tokenStorage.getToken()
            val retriedRequest = initialRequest.newBuilder()
                .header("Authorization", "Bearer $newToken")
                .build()
            response = httpClient.newCall(retriedRequest).execute()
        }
        return response.use(block)
    }

    @Serializable
    private data class SramOAuthTokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresIn: Long? = null,
        @SerialName("token_type") val tokenType: String? = null,
    )

    @Serializable
    private data class SramActivityDto(
        val id: String,
        val name: String? = null,
        val title: String? = null,
        val createdAt: String? = null,
        val startedAt: String? = null,
        val startTime: String? = null,
        val startTimestamp: Long? = null,
    )

    companion object {
        private const val TAG = "KarooAuth"
        const val DEFAULT_AUTHORIZE_URL = "https://api.hammerhead.io/v1/auth/oauth/authorize"
        const val DEFAULT_TOKEN_URL = "https://api.hammerhead.io/v1/auth/oauth/token"
        const val DEFAULT_API_BASE_URL = "https://api.hammerhead.io/v1/api"
        const val DEFAULT_CLIENT_ID = "d32fbef7-50c0-4551-a1f1-c4e6cbc51d06"
        const val DEFAULT_REDIRECT_URI = "karoo-health-connect://oauth2callback"
        const val DEFAULT_SCOPE = "activity:read"
    }
}
