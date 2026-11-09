package org.happycode.karoo.health_connect.adapters.sram

import android.content.SharedPreferences
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class TokenStorageTest : FreeSpec() {
    init {
        isolationMode = IsolationMode.InstancePerTest

        "InMemoryTokenStorage" - {
            val storage = InMemoryTokenStorage()

            "should store and retrieve OAuthToken" {
                //given
                val token = OAuthToken(
                    accessToken = "access_123",
                    refreshToken = "refresh_456",
                    expiresAt = 1000L,
                )

                //when
                storage.saveToken(token)

                //then
                storage.getToken() shouldBe "access_123"
                storage.getOAuthToken() shouldBe token
            }

            "should clear token" {
                //given
                storage.saveToken("plain_token")

                //when
                storage.clearToken()

                //then
                storage.getToken().shouldBeNull()
                storage.getOAuthToken().shouldBeNull()
            }
        }

        "SharedPreferencesTokenStorage" - {
            val fakePreferences = FakeStoragePreferences()
            val storage = SharedPreferencesTokenStorage(fakePreferences)

            "should store and retrieve OAuthToken with refresh token and expiresAt" {
                //given
                val token = OAuthToken(
                    accessToken = "oauth_access",
                    refreshToken = "oauth_refresh",
                    expiresAt = 1794218400000L,
                )

                //when
                storage.saveToken(token)

                //then
                storage.getToken() shouldBe "oauth_access"
                val retrieved = storage.getOAuthToken()
                retrieved shouldNotBe null
                retrieved?.accessToken shouldBe "oauth_access"
                retrieved?.refreshToken shouldBe "oauth_refresh"
                retrieved?.expiresAt shouldBe 1794218400000L
            }

            "should clear all token fields" {
                //given
                storage.saveToken(
                    OAuthToken(
                        accessToken = "token_to_clear",
                        refreshToken = "refresh_to_clear",
                        expiresAt = 9999L,
                    ),
                )

                //when
                storage.clearToken()

                //then
                storage.getToken().shouldBeNull()
                storage.getOAuthToken().shouldBeNull()
            }
        }
    }

    private class FakeStoragePreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = (data[key] as? String) ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST") (data[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = (data[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (data[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (data[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (data[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(data)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private class FakeEditor(private val backing: MutableMap<String, Any?>) : SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any?>()
            private val removals = mutableSetOf<String>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
                key?.let { temp[it] = value }
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
                key?.let { temp[it] = values?.toSet() }
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
                key?.let { temp[it] = value }
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
                key?.let { temp[it] = value }
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
                key?.let { temp[it] = value }
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
                key?.let { temp[it] = value }
            }
            override fun remove(key: String?): SharedPreferences.Editor = apply {
                key?.let { removals.add(it) }
            }
            override fun clear(): SharedPreferences.Editor = apply { clearAll = true }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) backing.clear()
                removals.forEach { backing.remove(it) }
                backing.putAll(temp)
            }
        }
    }
}
