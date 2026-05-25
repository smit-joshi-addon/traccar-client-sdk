package org.traccar.client

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class LocationFilter(
    private val config: LocationConfig,
) : PositionFilter {

    private var lastAccepted: Position? = null

    override fun accept(position: Position): Boolean {
        val previous = lastAccepted
        if (previous == null) {
            lastAccepted = position
            return true
        }
        val timeOk = (position.time - previous.time) >= config.intervalSeconds * 1000L
        val distanceOk = distance(previous, position) >= config.distanceMeters
        if (timeOk && distanceOk) {
            lastAccepted = position
            return true
        }
        return false
    }

    private fun distance(a: Position, b: Position): Double {
        val lat1 = a.latitude * PI / 180
        val lat2 = b.latitude * PI / 180
        val dLat = (b.latitude - a.latitude) * PI / 180
        val dLon = (b.longitude - a.longitude) * PI / 180
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(h))
    }

    private companion object {
        const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}
