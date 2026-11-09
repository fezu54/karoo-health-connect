---
name: sram-dashboard
description: Guidance and specifications for integrating with the SRAM ID authentication and Hammerhead Dashboard API to list rides and stream raw .fit files without disk persistence.
---

# SRAM ID & Hammerhead Dashboard Integration

## Overview
Acts as the `RideSourceGateway` adapter in the **Adapters** ring (`adapters.sram`). Communicates with SRAM ID / Hammerhead OAuth 2.0 services (`dashboard.hammerhead.io` for authorization consent, and `api.hammerhead.io` for token exchange and data APIs) to authenticate via OpenID Connect / OAuth 2.0 Authorization Code Flow with PKCE, list user activities, and stream binary `.fit` files.

---

## Architecture Placement
- **Domain Interface**: `org.happycode.karoo.health_connect.domain.gateway.RideSourceGateway`
- **Adapter Implementation**: `org.happycode.karoo.health_connect.adapters.sram.SramRideSourceAdapter`
- **Token Storage**: `org.happycode.karoo.health_connect.adapters.sram.TokenStorage` (backed by `SharedPreferencesTokenStorage` / `EncryptedSharedPreferences`)
- **HTTP Client**: `okhttp3.OkHttpClient` with `kotlinx.serialization` for JSON responses.

---

## Authentication Flow (SRAM ID OAuth 2.0 / OpenID Connect)
The integration uses the standard OAuth 2.0 Authorization Code Grant with PKCE (RFC 7636) and deep linking. User passwords are never requested or stored.

### 1. Authorization Request (Browser / Custom Tabs)
- **Endpoint**: `GET https://api.hammerhead.io/v1/auth/oauth/authorize`
- **Parameters**:
  - `response_type=code`
  - `client_id`: Registered client ID from Hammerhead Developer Settings.
  - `redirect_uri`: Custom scheme redirect (e.g. `karoo-health-connect://oauth2callback`).
  - `scope`: `activity:read` (and other requested scopes like `route:read`).
  - `state`: Cryptographically random CSRF token generated for the session.
  - `code_challenge`: SHA-256 hash of the generated PKCE verifier, base64url-encoded.
  - `code_challenge_method`: `S256`.

### 2. Authorization Callback & Token Exchange
- **Deep Link Filter**: `karoo-health-connect://oauth2callback?code={authCode}&state={state}`
- **Token Endpoint**: `POST https://api.hammerhead.io/v1/auth/oauth/token`
- **Form Payload**:
  ```http
  grant_type=authorization_code&client_id={clientId}&code={code}&redirect_uri={redirectUri}&code_verifier={codeVerifier}
  ```
- **Response**:
  ```json
  {
    "access_token": "hh_at_...",
    "refresh_token": "hh_rt_...",
    "expires_in": 7200,
    "token_type": "Bearer"
  }
  ```

### 3. Session & Token Refresh Handling
- Store `OAuthToken(accessToken, refreshToken, expiresAt)` securely in `TokenStorage`.
- When access token expires or on receiving `401 Unauthorized`, automatically exchange `refresh_token` at `POST https://api.hammerhead.io/v1/auth/oauth/token`:
  ```http
  grant_type=refresh_token&client_id={clientId}&refresh_token={refreshToken}
  ```
- Retry the failed request once with the new access token.

---

## Activity Discovery & FIT Streaming

### 1. Fetching Activities
- **Endpoint**: `GET https://api.hammerhead.io/v1/api/activities?perPage=100`
- **Header**: `Authorization: Bearer <access_token>`
- **Mapping**: Map JSON response items (from root array or `data` property) into `domain.model.RideSummary(id = RideId(item.id), title = item.name, startTime = item.startTime)`.

### 2. Streaming FIT Files
- **Endpoint**: `GET https://api.hammerhead.io/v1/api/activities/{activityId}/file`
- **Header**: `Authorization: Bearer <access_token>`
- **Zero Disk Retention Rule**:
  - Stream the response body directly as an `InputStream`:
  ```kotlin
  override suspend fun fetchRawFitStream(rideId: RideId): InputStream =
      httpClient.newCall(buildFitRequest(rideId)).execute().let { response ->
          when {
              response.isSuccessful -> response.body.byteStream()
              else -> throw IOException("Failed to download FIT: HTTP ${response.code}")
          }
      }
  ```
  - Never write the byte stream to local disk storage (`Context.filesDir` or cache). Keep in memory, pass to parser, and close immediately.

---

## Error Handling
- Network and parse errors are caught in interactors and mapped to domain models (`SyncResult.Failure(SyncError.NetworkError(...))`) so outer frameworks handle user messaging appropriately.
