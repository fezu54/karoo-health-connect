---
name: karoo-sync-engine
description: Architecture and implementation instructions for background synchronization using Android WorkManager, manual dependency injection composition root, and lightweight key-value state persistence.
---

# Karoo Sync Engine & Application Orchestration

## Overview
Coordinates the execution of ride synchronization across both manual (user-initiated) and automatic (periodic background) modes, managing lightweight local state and pure Kotlin dependency wiring.

---

## 1. Composition Root (Manual Dependency Injection)
Per the YAGNI principle and minimizing dependencies, avoid Dagger/Hilt/Koin. Use a pure Kotlin `AppContainer` initialized in the `Application` subclass:

```kotlin
class AppContainer(context: Context) {
    val preferencesRepository: SyncPreferencesRepository by lazy {
        SharedPreferencesSyncRepository(context)
    }

    val rideSourceGateway: RideSourceGateway by lazy {
        SramRideSourceAdapter(preferencesRepository)
    }

    val fitParser: FitParser by lazy {
        GarminFitParserAdapter()
    }

    val healthConnectGateway: HealthConnectGateway by lazy {
        AndroidHealthConnectGateway(HealthConnectClient.getOrCreate(context))
    }

    val syncRidesUseCase: SyncRidesUseCase by lazy {
        SyncRidesUseCase(
            rideSourceGateway,
            fitParser,
            healthConnectGateway,
            preferencesRepository,
        )
    }
}
```

---

## 2. Minimal Local State Management
Do not use SQLite/Room database. All state is small, flat, and managed via `SharedPreferences`:

- **Credentials & Tokens**: Stored in `EncryptedSharedPreferences` (`auth_token`, `refresh_token`).
- **Sync State**: Stored in standard `SharedPreferences`:
  - `last_sync_timestamp_epoch_ms`: Long (used as query cursor for SRAM activities).
  - `synced_ride_ids`: Set<String> (for fast deduplication check before initiating FIT downloads).
  - `pref_sync_heart_rate`, `pref_sync_power`, `pref_sync_cadence`, `pref_sync_route`, `pref_sync_calories`: Boolean flags.

---

## 3. Background Sync (WorkManager)

- **Worker**: `SyncWorker : CoroutineWorker(context, params)`
  - Retrieves `syncRidesUseCase` from `(applicationContext as KarooApplication).appContainer`.
  - Calls `syncRidesUseCase.execute()`.
  - Returns `Result.success()`, `Result.retry()` (on transient network errors), or `Result.failure()`.

- **Scheduling Constraints**:
  - `NetworkType.CONNECTED` (require active network).
  - `PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)` with flexible interval.
  - Unique work name: `KarooPeriodicSyncWorker` with `ExistingPeriodicWorkPolicy.KEEP`.
