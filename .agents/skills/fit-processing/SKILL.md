---
name: fit-processing
description: Instructions for decoding Garmin FIT files using the official SDK directly from an in-memory stream into pure Kotlin domain entities without saving to disk.
---

# FIT File Stream Processing

## Overview
Acts as the `FitParser` adapter in the **Adapters** ring (`adapters.fit`). Parses binary `.fit` byte streams into the pure Kotlin `domain.model.Ride` aggregate using `com.garmin:fit`.

---

## Architecture Placement
- **Domain Interface**: `org.happycode.karoo.health_connect.domain.gateway.FitParser`
- **Adapter Implementation**: `org.happycode.karoo.health_connect.adapters.fit.GarminFitParserAdapter`
- **Dependency**: `com.garmin:fit` (managed in `../../../gradle/libs.versions.toml`).

---

## Parsing Strategy

1. **In-Memory Streaming**:
   Decode directly from `java.io.InputStream`. The parser must automatically close the stream in a `use` block to guarantee memory reclamation.

   ```kotlin
   class GarminFitParserAdapter : FitParser {
       override fun parse(stream: InputStream): Ride =
           stream.use { input ->
               val decode = Decode()
               val mesgBroadcaster = MesgBroadcaster()
               val builder = RideDomainBuilder()

               mesgBroadcaster.addListener(builder as SessionMesgListener)
               mesgBroadcaster.addListener(builder as RecordMesgListener)

               decode.read(input, mesgBroadcaster)
               builder.build()
           }
   }
   ```

2. **Garmin Message Mapping to Domain**:
   - `SessionMesg`:
     - `startTime`, `totalElapsedTime`, `totalTimerTime` -> `startTime`, `endTime`, `duration`.
     - `totalDistance` -> `Distance`.
     - `totalCalories` -> `TotalCalories`.
     - `totalAscent` -> `ElevationGain`.
   - `RecordMesg` (Time-series data points):
     - `timestamp` + `heartRate` -> `HeartRateSample(timestamp, bpm)`.
     - `timestamp` + `power` -> `PowerSample(timestamp, watts)`.
     - `timestamp` + `cadence` -> `CadenceSample(timestamp, rpm)`.
     - `timestamp` + `speed` -> `SpeedSample(timestamp, metersPerSecond)`.
     - `positionLat` & `positionLong` (semicircles converted to degrees: `semicircles * (180.0 / 2^31)`) + `altitude` -> `GpsPoint(latitude, longitude, altitude, timestamp)`.

3. **Coordinate Conversion Formula**:
   ```kotlin
   fun semicirclesToDegrees(semicircles: Int): Double =
       semicircles * (180.0 / 2147483648.0)
   ```

---

## Defensive Rules
- If any time-series metric (e.g. power meter or heart rate strap) was not connected during the Karoo ride, the corresponding record fields will be null. Filter out null samples gracefully; do not fail the ride parse.
- Never write intermediate `.fit` data to temporary files or Android external/cache storage.
