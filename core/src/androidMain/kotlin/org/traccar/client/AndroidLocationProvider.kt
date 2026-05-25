package org.traccar.client

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Looper
import androidx.core.content.getSystemService

class AndroidLocationProvider(
    context: Context,
    private val config: LocationConfig = LocationConfig(),
) : CallbackPositionProvider() {

    private val appContext = context.applicationContext
    private val locationManager: LocationManager = checkNotNull(appContext.getSystemService())
    private val batteryManager: BatteryManager? = appContext.getSystemService()
    private var listener: LocationListener? = null

    override suspend fun start(emit: (Position) -> Unit) {
        val listener = LocationListener { location ->
            emit(location.toPosition(readBattery()))
        }
        try {
            locationManager.requestLocationUpdates(
                config.accuracy.toAndroidProvider(),
                config.intervalSeconds * 1000L,
                config.distanceMeters.toFloat(),
                listener,
                Looper.getMainLooper(),
            )
        } catch (e: SecurityException) {
            throw e
        }
        this.listener = listener
    }

    override fun stop() {
        listener?.let { locationManager.removeUpdates(it) }
        listener = null
    }

    private fun readBattery(): Int? {
        val level = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: return null
        return if (level in 0..100) level else null
    }
}

private fun Accuracy.toAndroidProvider(): String = when (this) {
    Accuracy.HIGHEST, Accuracy.HIGH -> LocationManager.GPS_PROVIDER
    Accuracy.MEDIUM -> LocationManager.NETWORK_PROVIDER
    Accuracy.LOW -> LocationManager.PASSIVE_PROVIDER
}

private fun Location.toPosition(battery: Int?) = Position(
    latitude = latitude,
    longitude = longitude,
    accuracy = accuracy.toDouble(),
    time = time,
    altitude = if (hasAltitude()) altitude else null,
    speed = if (hasSpeed()) speed.toDouble() else null,
    bearing = if (hasBearing()) bearing.toDouble() else null,
    battery = battery,
)
