package org.traccar.client

import android.content.Context
import android.location.Location
import android.os.BatteryManager
import androidx.core.content.getSystemService

abstract class BaseLocationProvider(
    context: Context,
    protected val config: LocationConfig,
) : CallbackPositionProvider() {

    protected val appContext: Context = context.applicationContext
    private val batteryManager: BatteryManager? = appContext.getSystemService()

    protected fun readBattery(): Int? {
        val level = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: return null
        return if (level in 0..100) level else null
    }

    protected fun Location.toPosition(): Position = Position(
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy.toDouble(),
        time = time,
        altitude = if (hasAltitude()) altitude else null,
        speed = if (hasSpeed()) speed.toDouble() else null,
        bearing = if (hasBearing()) bearing.toDouble() else null,
        battery = readBattery(),
    )
}
