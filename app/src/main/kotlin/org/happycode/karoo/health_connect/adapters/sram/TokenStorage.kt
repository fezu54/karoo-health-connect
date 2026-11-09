package org.happycode.karoo.health_connect.adapters.sram

import android.content.SharedPreferences
import androidx.core.content.edit

data class OAuthToken(
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresAt: Long? = null,
)

interface TokenStorage {
    fun getToken(): String?
    fun getOAuthToken(): OAuthToken?
    fun saveToken(token: OAuthToken)
    fun saveToken(token: String) = saveToken(OAuthToken(accessToken = token))
    fun clearToken()
}

class InMemoryTokenStorage(
    private var oauthToken: OAuthToken? = null,
) : TokenStorage {
    override fun getToken(): String? = oauthToken?.accessToken

    override fun getOAuthToken(): OAuthToken? = oauthToken

    override fun saveToken(token: OAuthToken) {
        oauthToken = token
    }

    override fun clearToken() {
        oauthToken = null
    }
}

class SharedPreferencesTokenStorage(
    private val preferences: SharedPreferences,
) : TokenStorage {
    override fun getToken(): String? =
        preferences.getString(KEY_ACCESS_TOKEN, null) ?: preferences.getString(KEY_TOKEN, null)

    override fun getOAuthToken(): OAuthToken? {
        val accessToken = getToken() ?: return null
        val refreshToken = preferences.getString(KEY_REFRESH_TOKEN, null)
        val expiresAt = if (preferences.contains(KEY_EXPIRES_AT)) {
            preferences.getLong(KEY_EXPIRES_AT, 0L)
        } else {
            null
        }
        return OAuthToken(accessToken, refreshToken, expiresAt)
    }

    override fun saveToken(token: OAuthToken) {
        preferences.edit {
            putString(KEY_ACCESS_TOKEN, token.accessToken)
            putString(KEY_TOKEN, token.accessToken)
            token.refreshToken?.let { putString(KEY_REFRESH_TOKEN, it) } ?: remove(KEY_REFRESH_TOKEN)
            token.expiresAt?.let { putLong(KEY_EXPIRES_AT, it) } ?: remove(KEY_EXPIRES_AT)
        }
    }

    override fun clearToken() {
        preferences.edit {
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_TOKEN)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_EXPIRES_AT)
        }
    }

    companion object {
        private const val KEY_TOKEN = "sram_auth_bearer_token"
        private const val KEY_ACCESS_TOKEN = "sram_oauth_access_token"
        private const val KEY_REFRESH_TOKEN = "sram_oauth_refresh_token"
        private const val KEY_EXPIRES_AT = "sram_oauth_expires_at"
    }
}
