package org.happycode.karoo.health_connect.adapters.sram

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import java.io.IOException
import java.time.Instant
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.happycode.karoo.health_connect.domain.model.RideId

class SramRideSourceAdapterTest : FreeSpec() {
    init {
        isolationMode = IsolationMode.InstancePerTest

        var requestHandler: (Request) -> Response = { request ->
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(404)
                .message("Not Found")
                .body("{}".toResponseBody("application/json".toMediaType()))
                .build()
        }

        val interceptor = Interceptor { chain -> requestHandler(chain.request()) }
        val httpClient = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val tokenStorage = InMemoryTokenStorage()

        val adapter = SramRideSourceAdapter(
            httpClient = httpClient,
            tokenStorage = tokenStorage,
            authorizeUrl = "https://mock.hammerhead.io/oauth/confirm",
            tokenUrl = "https://mock.hammerhead.io/oauth/token",
            apiBaseUrl = "https://mock.hammerhead.io/api",
            clientId = "test-client-id",
            redirectUri = "karoo-health-connect://oauth2callback",
        )

        "should generate valid authorization url with pkce, state, and scopes" {
            //when
            val url = adapter.getAuthorizationUrl(state = "test-state", codeChallenge = "test-challenge")

            //then
            url shouldContain "https://mock.hammerhead.io/oauth/confirm"
            url shouldContain "response_type=code"
            url shouldContain "client_id=test-client-id"
            url shouldContain "redirect_uri=karoo-health-connect%3A%2F%2Foauth2callback"
            url shouldContain "scope=activity%3Aread"
            url shouldContain "state=test-state"
            url shouldContain "code_challenge=test-challenge"
            url shouldContain "code_challenge_method=S256"
        }

        "should exchange authorization code for tokens and save to token storage" {
            //given
            requestHandler = { request ->
                request.url.encodedPath shouldBe "/oauth/token"
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        """
                        {
                            "access_token": "valid_access_token_123",
                            "refresh_token": "valid_refresh_token_456",
                            "expires_in": 3600,
                            "token_type": "Bearer"
                        }
                        """.trimIndent().toResponseBody("application/json".toMediaType()),
                    )
                    .build()
            }

            //when
            val success = adapter.exchangeAuthorizationCode(code = "auth_code_xyz", codeVerifier = "verifier_123")

            //then
            success shouldBe true
            val savedToken = tokenStorage.getOAuthToken()
            savedToken shouldNotBe null
            savedToken?.accessToken shouldBe "valid_access_token_123"
            savedToken?.refreshToken shouldBe "valid_refresh_token_456"
            savedToken?.expiresAt shouldNotBe null
        }

        "should return false when authorization code exchange fails with 400 Bad Request" {
            //given
            requestHandler = { request ->
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(400)
                    .message("Bad Request")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            }

            //when
            val success = adapter.exchangeAuthorizationCode(code = "invalid_code")

            //then
            success shouldBe false
            tokenStorage.getOAuthToken() shouldBe null
        }

        "should throw IllegalStateException when fetching rides without prior authentication" {
            //given
            tokenStorage.clearToken()

            //when & then
            shouldThrow<IllegalStateException> {
                adapter.fetchUnsyncedRides(Instant.EPOCH)
            }
        }

        "should fetch and map ride summaries when authenticated and activities returned in data array" {
            //given
            tokenStorage.saveToken(OAuthToken(accessToken = "mock_bearer_token"))
            requestHandler = { request ->
                request.url.encodedPath shouldBe "/api/activities"
                request.header("Authorization") shouldBe "Bearer mock_bearer_token"

                val jsonBody = """
                    {
                        "data": [
                            {
                                "id": "act-1",
                                "name": "Gravel Adventure",
                                "createdAt": "2026-11-09T08:00:00Z"
                            },
                            {
                                "id": "act-2",
                                "title": "Evening Commute",
                                "startTimestamp": 1794218400000
                            }
                        ]
                    }
                """.trimIndent()

                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(jsonBody.toResponseBody("application/json".toMediaType()))
                    .build()
            }

            //when
            val rides = adapter.fetchUnsyncedRides(Instant.EPOCH)

            //then
            rides.size shouldBe 2
            rides[0].id shouldBe RideId("act-1")
            rides[0].title shouldBe "Gravel Adventure"
            rides[0].startTime shouldBe Instant.parse("2026-11-09T08:00:00Z")

            rides[1].id shouldBe RideId("act-2")
            rides[1].title shouldBe "Evening Commute"
        }

        "should map startedAt ISO string when activity contains startedAt field" {
            //given
            tokenStorage.saveToken(OAuthToken(accessToken = "mock_bearer_token"))
            requestHandler = { request ->
                val jsonBody = """
                    {
                        "data": [
                            {
                                "id": "act-started-at",
                                "name": "Morning Hammerhead Ride",
                                "startedAt": "2026-11-09T09:30:00Z"
                            }
                        ]
                    }
                """.trimIndent()

                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(jsonBody.toResponseBody("application/json".toMediaType()))
                    .build()
            }

            //when
            val rides = adapter.fetchUnsyncedRides(Instant.EPOCH)

            //then
            rides.size shouldBe 1
            rides[0].id shouldBe RideId("act-started-at")
            rides[0].title shouldBe "Morning Hammerhead Ride"
            rides[0].startTime shouldBe Instant.parse("2026-11-09T09:30:00Z")
        }

        "should retry request with refreshed token when API returns 401 Unauthorized" {
            //given
            tokenStorage.saveToken(
                OAuthToken(
                    accessToken = "expired_token",
                    refreshToken = "mock_refresh_token",
                ),
            )

            var attempts = 0
            requestHandler = { request ->
                when (request.url.encodedPath) {
                    "/oauth/token" -> {
                        Response.Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(
                                """
                                {
                                    "access_token": "fresh_access_token",
                                    "refresh_token": "fresh_refresh_token",
                                    "expires_in": 3600
                                }
                                """.trimIndent().toResponseBody("application/json".toMediaType()),
                            )
                            .build()
                    }
                    "/api/activities" -> {
                        attempts++
                        if (attempts == 1) {
                            request.header("Authorization") shouldBe "Bearer expired_token"
                            Response.Builder()
                                .request(request)
                                .protocol(Protocol.HTTP_1_1)
                                .code(401)
                                .message("Unauthorized")
                                .body("{}".toResponseBody("application/json".toMediaType()))
                                .build()
                        } else {
                            request.header("Authorization") shouldBe "Bearer fresh_access_token"
                            Response.Builder()
                                .request(request)
                                .protocol(Protocol.HTTP_1_1)
                                .code(200)
                                .message("OK")
                                .body("[]".toResponseBody("application/json".toMediaType()))
                                .build()
                        }
                    }
                    else -> Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(404)
                        .message("Not Found")
                        .body("".toResponseBody(null))
                        .build()
                }
            }

            //when
            val rides = adapter.fetchUnsyncedRides(Instant.EPOCH)

            //then
            rides shouldBe emptyList()
            attempts shouldBe 2
            tokenStorage.getToken() shouldBe "fresh_access_token"
        }

        "should fetch raw fit input stream when valid ride id requested" {
            //given
            tokenStorage.saveToken(OAuthToken(accessToken = "mock_bearer_token"))
            val fakeBytes = byteArrayOf(10, 20, 30, 40)

            requestHandler = { request ->
                request.url.encodedPath shouldBe "/api/activities/act-1/file"
                request.header("Authorization") shouldBe "Bearer mock_bearer_token"

                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(fakeBytes.toResponseBody("application/octet-stream".toMediaType()))
                    .build()
            }

            //when
            val stream = adapter.fetchRawFitStream(RideId("act-1"))
            val downloadedBytes = stream.use { it.readBytes() }

            //then
            downloadedBytes shouldBe fakeBytes
        }

        "should throw IOException when fit download fails with 404 Not Found" {
            //given
            tokenStorage.saveToken(OAuthToken(accessToken = "mock_bearer_token"))

            requestHandler = { request ->
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(404)
                    .message("Not Found")
                    .body("".toResponseBody(null))
                    .build()
            }

            //when & then
            shouldThrow<IOException> {
                adapter.fetchRawFitStream(RideId("non-existent"))
            }
        }
    }
}
