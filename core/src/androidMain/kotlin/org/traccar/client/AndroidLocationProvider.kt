package org.traccar.client

import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.getSystemService

class AndroidLocationProvider(
    context: Context,
    config: LocationConfig,
) : BaseLocationProvider(context, config.effective) {

    private val locationManager: LocationManager = checkNotNull(appContext.getSystemService())
    private var listener: LocationListener? = null

    override suspend fun start(emit: (Position) -> Unit) {
        val listener = LocationListener { location ->
            emit(location.toPosition())
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
}

private fun Accuracy.toAndroidProvider(): String = when (this) {
    Accuracy.HIGHEST, Accuracy.HIGH -> LocationManager.GPS_PROVIDER
    Accuracy.MEDIUM -> LocationManager.NETWORK_PROVIDER
    Accuracy.LOW -> LocationManager.PASSIVE_PROVIDER
}
