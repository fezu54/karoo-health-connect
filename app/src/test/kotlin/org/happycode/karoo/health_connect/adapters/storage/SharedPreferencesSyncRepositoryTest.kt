package org.happycode.karoo.health_connect.adapters.storage

import android.content.SharedPreferences
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant
import org.happycode.karoo.health_connect.domain.model.RideId
import org.happycode.karoo.health_connect.domain.model.SyncPreferences

class SharedPreferencesSyncRepositoryTest : FreeSpec() {
    init {
        isolationMode = IsolationMode.InstancePerTest

    val fakePreferences = FakeSharedPreferences()
    val repository = SharedPreferencesSyncRepository(fakePreferences)

    "should return default preferences when no preferences stored in SharedPreferences" {
        //given / when
        val preferences = repository.getPreferences()

        //then
        preferences shouldBe SyncPreferences()
    }

    "should persist and retrieve updated preferences when preferences updated" {
        //given
        val customPreferences = SyncPreferences(
            syncHeartRate = false,
            syncPower = false,
            syncCadence = true,
            syncSpeed = true,
            syncCalories = false,
            syncElevation = false,
            syncRoute = false,
        )

        //when
        repository.updatePreferences(customPreferences)
        val loaded = repository.getPreferences()

        //then
        loaded shouldBe customPreferences
    }

    "should return null last sync timestamp when no sync timestamp stored" {
        //given / when
        val lastSync = repository.getLastSyncTimestamp()

        //then
        lastSync.shouldBeNull()
    }

    "should persist and retrieve last sync timestamp and mark ride as synced when markRideSynced called" {
        //given
        val rideId = RideId("ride-abc")
        val timestamp = Instant.parse("2026-11-09T08:30:00Z")

        //when
        repository.markRideSynced(rideId, timestamp)

        //then
        repository.getLastSyncTimestamp() shouldBe timestamp
        repository.isRideSynced(rideId) shouldBe true
        repository.isRideSynced(RideId("other-ride")) shouldBe false
    }

    "should advance sync timestamp when later ride is synced" {
        //given
        val firstRideId = RideId("ride-1")
        val secondRideId = RideId("ride-2")
        val firstTimestamp = Instant.parse("2026-11-09T08:00:00Z")
        val secondTimestamp = Instant.parse("2026-11-09T10:00:00Z")

        //when
        repository.markRideSynced(firstRideId, firstTimestamp)
        repository.markRideSynced(secondRideId, secondTimestamp)

        //then
        repository.getLastSyncTimestamp() shouldBe secondTimestamp
        repository.isRideSynced(firstRideId) shouldBe true
        repository.isRideSynced(secondRideId) shouldBe true
    }

    "should clear synced ride IDs and last sync timestamp when clearSyncHistory executed" {
        //given
        val rideId = RideId("ride-to-clear")
        val timestamp = Instant.parse("2026-11-09T10:00:00Z")
        repository.markRideSynced(rideId, timestamp)
        repository.isRideSynced(rideId) shouldBe true
        repository.getLastSyncTimestamp() shouldBe timestamp

        //when
        repository.clearSyncHistory()

        //then
        repository.isRideSynced(rideId) shouldBe false
        repository.getLastSyncTimestamp() shouldBe null
    }
}
}

private class FakeSharedPreferences : SharedPreferences {
    private val storage = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = storage

    override fun getString(key: String?, defValue: String?): String? =
        (storage[key] as? String) ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (storage[key] as? Set<String>)?.toMutableSet() ?: defValues

    override fun getInt(key: String?, defValue: Int): Int =
        (storage[key] as? Int) ?: defValue

    override fun getLong(key: String?, defValue: Long): Long =
        (storage[key] as? Long) ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float =
        (storage[key] as? Float) ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        (storage[key] as? Boolean) ?: defValue

    override fun contains(key: String?): Boolean = storage.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor(storage)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private class FakeEditor(private val backingStorage: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val tempStorage = mutableMapOf<String, Any?>()
        private val removeKeys = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
            key?.let { tempStorage[it] = value }
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
            key?.let { tempStorage[it] = values?.toSet() }
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
            key?.let { tempStorage[it] = value }
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
            key?.let { tempStorage[it] = value }
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
            key?.let { tempStorage[it] = value }
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
            key?.let { tempStorage[it] = value }
        }

        override fun remove(key: String?): SharedPreferences.Editor = apply {
            key?.let { removeKeys.add(it) }
        }

        override fun clear(): SharedPreferences.Editor = apply {
            clearAll = true
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clearAll) {
                backingStorage.clear()
            }
            removeKeys.forEach { backingStorage.remove(it) }
            backingStorage.putAll(tempStorage)
        }
    }
}
