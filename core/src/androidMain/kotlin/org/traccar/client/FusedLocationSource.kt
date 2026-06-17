package org.traccar.client

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

@SuppressLint("MissingPermission")
class FusedLocationSource(
    scope: ComponentCoroutineScope,
    context: Context,
    config: Config,
    state: StateFlow<State>,
) : LocationSource {

    private val locationConfig = config.location.effective

    private val appContext = context.applicationContext
    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(appContext)

    override val positions = MutableSharedFlow<Position>(extraBufferCapacity = 8)

    private var callback: LocationCallback? = null
    private var currentLocationToken: CancellationTokenSource? = null

    init {
        scope.observeActive(state, { it.enabled && !it.paused }) { active ->
            if (active) ensureStarted() else ensureStopped()
        }
    }

    private suspend fun ensureStarted() {
        if (callback != null) return
        startUpdates(locationConfig)
    }

    private suspend fun ensureStopped() {
        if (callback == null) return
        val finalFix = awaitCurrentLocation()
        stopUpdates()
        finalFix?.let { positions.emit(it.toPosition()) }
    }

    override suspend fun fetchOnce(): Position? = awaitCurrentLocation()?.toPosition()

    private fun startUpdates(locationConfig: LocationConfig) {
        val newCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                positions.tryEmit(location.toPosition())
            }
        }
        val request = LocationRequest.Builder(
            locationConfig.accuracy.toFusedPriority(),
            if (locationConfig.distanceMeters > 0) 0L else locationConfig.intervalSeconds * 1000L,
        )
            .setMinUpdateDistanceMeters(locationConfig.distanceMeters.toFloat())
            .build()
        client.requestLocationUpdates(request, newCallback, Looper.getMainLooper())
        callback = newCallback
    }

    private fun stopUpdates() {
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
    }

    private suspend fun awaitCurrentLocation(): Location? {
        currentLocationToken?.cancel()
        val token = CancellationTokenSource()
        currentLocationToken = token
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()
        return try {
            suspendCancellableCoroutine { continuation ->
                client.getCurrentLocation(request, token.token)
                    .addOnSuccessListener {
                        if (continuation.isActive) continuation.resume(it)
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) continuation.resume(null)
                    }
                continuation.invokeOnCancellation { token.cancel() }
            }
        } finally {
            if (currentLocationToken === token) currentLocationToken = null
        }
    }

    private fun Location.toPosition(): Position = Position(
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy.toDouble(),
        time = time,
        altitude = if (hasAltitude()) altitude else null,
        speed = if (hasSpeed()) speed.toDouble() else null,
        bearing = if (hasBearing()) bearing.toDouble() else null,
    )
}

private fun Accuracy.toFusedPriority(): Int = when (this) {
    Accuracy.HIGHEST, Accuracy.HIGH -> Priority.PRIORITY_HIGH_ACCURACY
    Accuracy.MEDIUM -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
    Accuracy.LOW -> Priority.PRIORITY_LOW_POWER
}
