---
name: health-connect
description: Comprehensive guidelines for integrating Android Health Connect, requesting runtime permissions, mapping domain ride models to Health Connect records, and ensuring idempotent sync.
---

# Health Connect Integration

## Overview
Acts as the `HealthConnectGateway` adapter in the **Adapters** ring (`adapters.healthconnect`). Interfaces with `androidx.health.connect.client.HealthConnectClient` to upsert cycling activities and granular telemetry into Android Health Connect.

---

## Architecture Placement
- **Domain Interface**: `org.happycode.karoo.health_connect.domain.gateway.HealthConnectGateway`
- **Adapter Implementation**: `org.happycode.karoo.health_connect.adapters.healthconnect.AndroidHealthConnectGateway`
- **SDK**: `androidx.health.connect:connect-client`

---

## Permission Matrix
Map permissions to user-configured sync preferences:

| Record Type                    | Health Connect Permission        | User Preference Toggle                          |
|:-------------------------------|:---------------------------------|:------------------------------------------------|
| `ExerciseSessionRecord`        | `WRITE_EXERCISE`                 | Mandatory (Core Ride)                           |
| `DistanceRecord`               | `WRITE_DISTANCE`                 | Core Ride                                       |
| `TotalCaloriesBurnedRecord`    | `WRITE_TOTAL_CALORIES_BURNED`    | `syncCalories`                                  |
| `ElevationGainedRecord`        | `WRITE_ELEVATION_GAINED`         | `syncElevation`                                 |
| `HeartRateRecord`              | `WRITE_HEART_RATE`               | `syncHeartRate`                                 |
| `PowerRecord`                  | `WRITE_POWER`                    | `syncPower`                                     |
| `CyclingPedalingCadenceRecord` | `WRITE_CYCLING_PEDALING_CADENCE` | `syncCadence`                                   |
| `SpeedRecord`                  | `WRITE_SPEED`                    | `syncSpeed`                                     |
| `ExerciseRoute`                | `WRITE_EXERCISE_ROUTE`           | `syncRoute` (requires fine location permission) |

---

## Idempotency & Deduplication
To ensure re-syncs or overlapping queries never duplicate entries in Health Connect:
- Populate `Metadata(clientRecordId = "karoo_${ride.id.value}")` on all primary session records.
- When Health Connect encounters an existing `clientRecordId`, it updates the existing entry rather than creating duplicates.

```kotlin
fun createSessionRecord(ride: Ride, route: ExerciseRoute?): ExerciseSessionRecord =
    ExerciseSessionRecord(
        startTime = ride.startTime,
        startZoneOffset = ride.zoneOffset,
        endTime = ride.endTime,
        endZoneOffset = ride.zoneOffset,
        exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
        title = ride.title,
        exerciseRoute = route,
        metadata = Metadata.manualEntry(clientRecordId = "karoo_${ride.id.value}"),
    )
```

---

## Batch Insertion Strategy
1. Insert primary `ExerciseSessionRecord` first (attaching `ExerciseRoute` if route sync is enabled and points are present).
2. Insert associated time-series series records in chunks (Health Connect has request payload size limits).
3. If an individual non-critical metric fails to write (e.g. cadence permission denied by user), log the failure and allow the rest of the ride session to succeed.
