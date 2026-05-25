package org.traccar.client

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.getSystemService

class AndroidGpsProvider(
    context: Context,
    private val minTimeMs: Long = 1000L,
    private val minDistanceMeters: Float = 10f,
) : CallbackPositionProvider() {

    private val appContext = context.applicationContext
    private val locationManager: LocationManager = checkNotNull(appContext.getSystemService())
    private var listener: LocationListener? = null

    override suspend fun start(emit: (Position) -> Unit) {
        val listener = LocationListener { location ->
            emit(location.toPosition())
        }
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                minTimeMs,
                minDistanceMeters,
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
}

private fun Location.toPosition() = Position(
    latitude = latitude,
    longitude = longitude,
    accuracy = accuracy,
    time = time,
)
