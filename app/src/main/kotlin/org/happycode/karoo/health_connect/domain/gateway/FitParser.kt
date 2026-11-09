package org.happycode.karoo.health_connect.domain.gateway

import java.io.InputStream
import org.happycode.karoo.health_connect.domain.model.Ride

interface FitParser {
    fun parse(stream: InputStream): Ride
}
