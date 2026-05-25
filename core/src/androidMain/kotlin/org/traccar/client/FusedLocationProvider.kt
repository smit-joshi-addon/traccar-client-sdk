package org.traccar.client

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FusedLocationProvider(
    context: Context,
    config: LocationConfig,
) : BaseLocationProvider(context, config.effective) {

    private val locationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(appContext)
    private val activityClient: ActivityRecognitionClient =
        ActivityRecognition.getClient(appContext)

    private var emit: ((Position) -> Unit)? = null
    private var locationCallback: LocationCallback? = null
    private var activityPendingIntent: PendingIntent? = null
    private var transitionReceiver: BroadcastReceiver? = null
    private var stopTimeoutJob: Job? = null
    private var scope: CoroutineScope? = null

    override suspend fun start(emit: (Position) -> Unit) {
        this.emit = emit
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        startLocationUpdates()
        if (config.stopDetection) {
            startActivityMonitoring()
        }
    }

    override fun stop() {
        stopTimeoutJob?.cancel()
        stopTimeoutJob = null
        stopLocationUpdates()
        activityPendingIntent?.let {
            try {
                activityClient.removeActivityTransitionUpdates(it)
            } catch (e: SecurityException) {
                throw e
            }
        }
        activityPendingIntent = null
        transitionReceiver?.let { appContext.unregisterReceiver(it) }
        transitionReceiver = null
        scope?.cancel()
        scope = null
        emit = null
    }

    private fun startLocationUpdates() {
        if (locationCallback != null) return
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { emit?.invoke(it.toPosition()) }
            }
        }
        val request = LocationRequest.Builder(
            config.accuracy.toFusedPriority(),
            config.intervalSeconds * 1000L,
        )
            .setMinUpdateDistanceMeters(config.distanceMeters.toFloat())
            .build()
        try {
            locationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            throw e
        }
        locationCallback = callback
    }

    private fun stopLocationUpdates() {
        locationCallback?.let { locationClient.removeLocationUpdates(it) }
        locationCallback = null
    }

    private fun startActivityMonitoring() {
        val intent = Intent(ACTIVITY_TRANSITION_ACTION).setPackage(appContext.packageName)
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        activityPendingIntent = pendingIntent

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (!ActivityTransitionResult.hasResult(intent)) return
                val result = ActivityTransitionResult.extractResult(intent) ?: return
                result.transitionEvents.forEach { event ->
                    if (event.activityType != DetectedActivity.STILL) return@forEach
                    if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                        onStillEnter()
                    } else {
                        onStillExit()
                    }
                }
            }
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(ACTIVITY_TRANSITION_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        transitionReceiver = receiver

        val transitions = listOf(
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.STILL)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.STILL)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                .build(),
        )
        try {
            activityClient.requestActivityTransitionUpdates(
                ActivityTransitionRequest(transitions),
                pendingIntent,
            )
        } catch (e: SecurityException) {
            throw e
        }
    }

    private fun onStillEnter() {
        stopTimeoutJob?.cancel()
        stopTimeoutJob = scope?.launch {
            delay(config.stopTimeoutSeconds * 1000L)
            stopLocationUpdates()
        }
    }

    private fun onStillExit() {
        stopTimeoutJob?.cancel()
        stopTimeoutJob = null
        try {
            startLocationUpdates()
        } catch (_: SecurityException) {
        }
    }

    private companion object {
        const val ACTIVITY_TRANSITION_ACTION = "org.traccar.client.ACTIVITY_TRANSITION"
    }
}

private fun Accuracy.toFusedPriority(): Int = when (this) {
    Accuracy.HIGHEST, Accuracy.HIGH -> Priority.PRIORITY_HIGH_ACCURACY
    Accuracy.MEDIUM -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
    Accuracy.LOW -> Priority.PRIORITY_LOW_POWER
}
