package org.traccar.client

import kotlin.math.PI
import kotlin.math.abs
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
        if (position.latitude == null || position.longitude == null) {
            Log.log("Heartbeat accepted")
            return true
        }
        val previous = lastAccepted
        if (previous == null) {
            lastAccepted = position
            Log.log("Location accepted ${position.latitude},${position.longitude}")
            return true
        }
        val timeTrigger = (position.time - previous.time) >= config.intervalSeconds * 1000L
        val distanceTrigger = distance(previous, position) >= config.distanceMeters
        val angleTrigger = config.angleDegrees > 0 &&
            previous.bearing != null && position.bearing != null &&
            bearingChange(previous.bearing, position.bearing) >= config.angleDegrees
        if (timeTrigger || distanceTrigger || angleTrigger) {
            lastAccepted = position
            Log.log("Location accepted ${position.latitude},${position.longitude}")
            return true
        }
        Log.log("Location filtered ${position.latitude},${position.longitude}")
        return false
    }

    private fun distance(a: Position, b: Position): Double {
        val lat1 = a.latitude!! * PI / 180
        val lat2 = b.latitude!! * PI / 180
        val dLat = (b.latitude!! - a.latitude!!) * PI / 180
        val dLon = (b.longitude!! - a.longitude!!) * PI / 180
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(h))
    }

    private fun bearingChange(a: Double, b: Double): Double {
        val diff = abs(a - b)
        return if (diff > 180) 360 - diff else diff
    }

    private companion object {
        const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}
