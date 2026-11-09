package org.happycode.karoo.health_connect.adapters.fit

import com.garmin.fit.Decode
import com.garmin.fit.FileIdMesg
import com.garmin.fit.FileIdMesgListener
import com.garmin.fit.MesgBroadcaster
import com.garmin.fit.RecordMesg
import com.garmin.fit.RecordMesgListener
import com.garmin.fit.SessionMesg
import com.garmin.fit.SessionMesgListener
import java.io.InputStream
import java.time.Instant
import java.time.ZoneOffset
import org.happycode.karoo.health_connect.domain.gateway.FitParser
import org.happycode.karoo.health_connect.domain.model.CadenceSample
import org.happycode.karoo.health_connect.domain.model.GpsPoint
import org.happycode.karoo.health_connect.domain.model.HeartRateSample
import org.happycode.karoo.health_connect.domain.model.PowerSample
import org.happycode.karoo.health_connect.domain.model.Ride
import org.happycode.karoo.health_connect.domain.model.RideId
import org.happycode.karoo.health_connect.domain.model.RideMetrics
import org.happycode.karoo.health_connect.domain.model.SpeedSample

class GarminFitParserAdapter : FitParser {

    override fun parse(stream: InputStream): Ride = stream.use { input ->
        val decode = Decode()
        val broadcaster = MesgBroadcaster()
        val collector = FitMessageCollector()

        broadcaster.addListener(collector as FileIdMesgListener)
        broadcaster.addListener(collector as SessionMesgListener)
        broadcaster.addListener(collector as RecordMesgListener)

        decode.read(input, broadcaster)
        collector.buildRide()
    }

    private class FitMessageCollector : FileIdMesgListener, SessionMesgListener, RecordMesgListener {
        private var fileIdMesg: FileIdMesg? = null
        private var sessionMesg: SessionMesg? = null

        private val heartRates = mutableListOf<HeartRateSample>()
        private val powers = mutableListOf<PowerSample>()
        private val cadences = mutableListOf<CadenceSample>()
        private val speeds = mutableListOf<SpeedSample>()
        private val route = mutableListOf<GpsPoint>()

        override fun onMesg(mesg: FileIdMesg) {
            fileIdMesg = mesg
        }

        override fun onMesg(mesg: SessionMesg) {
            sessionMesg = mesg
        }

        override fun onMesg(mesg: RecordMesg) {
            val timestamp = mesg.timestamp?.date?.toInstant() ?: return

            mesg.heartRate?.toInt()?.takeIf { it > 0 }?.let { bpm ->
                heartRates.add(HeartRateSample(timestamp, bpm))
            }

            mesg.power?.takeIf { it >= 0 }?.let { watts ->
                powers.add(PowerSample(timestamp, watts))
            }

            mesg.cadence?.toInt()?.takeIf { it >= 0 }?.let { rpm ->
                cadences.add(CadenceSample(timestamp, rpm))
            }

            mesg.speed?.toDouble()?.takeIf { it >= 0.0 }?.let { speedMps ->
                speeds.add(SpeedSample(timestamp, speedMps))
            }

            val lat = mesg.positionLat
            val long = mesg.positionLong
            if ((lat != null) && (long != null) && (lat != 0x7FFFFFFF) && (long != 0x7FFFFFFF)) {
                route.add(
                    GpsPoint(
                        latitude = semicirclesToDegrees(lat),
                        longitude = semicirclesToDegrees(long),
                        altitudeMeters = mesg.altitude?.toDouble(),
                        timestamp = timestamp,
                    ),
                )
            }
        }

        fun buildRide(): Ride {
            val session = sessionMesg
            val startTime = session?.startTime?.date?.toInstant()
                ?: heartRates.firstOrNull()?.timestamp
                ?: route.firstOrNull()?.timestamp
                ?: Instant.now()

            val elapsedTimeSeconds = session?.totalElapsedTime?.toLong()
                ?: session?.totalTimerTime?.toLong()
                ?: 0L

            val endTime = when {
                elapsedTimeSeconds > 0 -> startTime.plusSeconds(elapsedTimeSeconds)
                heartRates.isNotEmpty() -> heartRates.last().timestamp
                route.isNotEmpty() -> route.last().timestamp
                else -> startTime
            }

            val idString = fileIdMesg?.timeCreated?.timestamp?.toString()
                ?: startTime.toEpochMilli().toString()

            val metrics = RideMetrics(
                heartRates = heartRates,
                powers = powers,
                cadences = cadences,
                speeds = speeds,
                route = route,
                totalDistanceMeters = session?.totalDistance?.toDouble(),
                totalElevationGainMeters = session?.totalAscent?.toDouble(),
                totalCalories = session?.totalCalories,
            )

            return Ride(
                id = RideId(idString),
                title = session?.sport?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Karoo Ride",
                startTime = startTime,
                endTime = endTime,
                zoneOffset = ZoneOffset.UTC,
                metrics = metrics,
            )
        }

        private fun semicirclesToDegrees(semicircles: Int): Double =
            semicircles * (180.0 / 2147483648.0)
    }
}
