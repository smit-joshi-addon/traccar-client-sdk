package org.traccar.client

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.activity.ComponentActivity
import androidx.core.content.getSystemService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class AndroidGpsProvider(
    context: Context,
    private val activity: ComponentActivity? = null,
    private val minTimeMs: Long = 1000L,
    private val minDistanceMeters: Float = 10f,
) : PositionProvider {

    private val appContext = context.applicationContext
    private val locationManager: LocationManager = checkNotNull(appContext.getSystemService())

    override fun positions(): Flow<Position> = callbackFlow {
        if (!hasLocationPermission(appContext)) {
            val activity = activity ?: run {
                close(SecurityException("Location permission denied"))
                return@callbackFlow
            }
            if (!requestLocationPermission(activity)) {
                close(SecurityException("Location permission denied"))
                return@callbackFlow
            }
        }
        val listener = LocationListener { location ->
            trySend(location.toPosition())
        }
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                minTimeMs,
                minDistanceMeters,
                listener,
            )
        } catch (e: SecurityException) {
            close(e)
            return@callbackFlow
        }
        awaitClose { locationManager.removeUpdates(listener) }
    }
}

private fun Location.toPosition() = Position(
    latitude = latitude,
    longitude = longitude,
    accuracy = accuracy,
    time = time,
)
