package org.traccar.client

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.location.LocationManagerCompat

@SuppressLint("MissingPermission")
class AndroidLocationProvider(
    context: Context,
    config: LocationConfig,
) : BaseLocationProvider(context, config.effective) {

    private val locationManager: LocationManager = checkNotNull(appContext.getSystemService())
    private var listener: LocationListener? = null
    private var currentLocationCancellation: CancellationSignal? = null

    override suspend fun start(emit: (Position) -> Unit) {
        val listener = LocationListener { location ->
            emit(location.toPosition())
        }
        locationManager.requestLocationUpdates(
            config.accuracy.toAndroidProvider(),
            config.intervalSeconds * 1000L,
            config.distanceMeters.toFloat(),
            listener,
            Looper.getMainLooper(),
        )
        requestCurrentLocation(emit)
        this.listener = listener
    }

    override fun stop() {
        currentLocationCancellation?.cancel()
        currentLocationCancellation = null
        listener?.let { locationManager.removeUpdates(it) }
        listener = null
    }

    private fun requestCurrentLocation(emit: (Position) -> Unit) {
        val signal = CancellationSignal()
        currentLocationCancellation = signal
        LocationManagerCompat.getCurrentLocation(
            locationManager,
            config.accuracy.toAndroidProvider(),
            signal,
            ContextCompat.getMainExecutor(appContext),
        ) { location ->
            location?.let { emit(it.toPosition()) }
        }
    }
}

private fun Accuracy.toAndroidProvider(): String = when (this) {
    Accuracy.HIGHEST, Accuracy.HIGH -> LocationManager.GPS_PROVIDER
    Accuracy.MEDIUM -> LocationManager.NETWORK_PROVIDER
    Accuracy.LOW -> LocationManager.PASSIVE_PROVIDER
}
