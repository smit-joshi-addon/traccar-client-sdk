package org.traccar.client

import android.annotation.SuppressLint
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
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
class FusedLocationProvider(
    context: Context,
    config: LocationConfig,
    private val stateStore: StateStore,
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
    private var heartbeatJob: Job? = null
    private var scope: CoroutineScope? = null
    private var currentLocationToken: CancellationTokenSource? = null

    override suspend fun start(emit: (Position) -> Unit) {
        this.emit = emit
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        if (config.stopDetection) {
            startActivityMonitoring()
        }
        if (config.stopDetection && stateStore.state.value.paused) {
            Log.log("Restoring stationary state")
            startHeartbeatLoop()
        } else {
            startLocationUpdates()
            requestCurrentLocation()
        }
    }

    override fun stop() {
        currentLocationToken?.cancel()
        currentLocationToken = null
        stopTimeoutJob?.cancel()
        stopTimeoutJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        stopLocationUpdates()
        activityPendingIntent?.let { activityClient.removeActivityTransitionUpdates(it) }
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
            if (config.distanceMeters > 0) 0L else config.intervalSeconds * 1000L,
        )
            .setMinUpdateDistanceMeters(config.distanceMeters.toFloat())
            .build()
        locationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        locationCallback = callback
    }

    private fun stopLocationUpdates() {
        locationCallback?.let { locationClient.removeLocationUpdates(it) }
        locationCallback = null
    }

    private fun requestCurrentLocation() {
        val token = CancellationTokenSource()
        currentLocationToken = token
        val request = CurrentLocationRequest.Builder()
            .setPriority(config.accuracy.toFusedPriority())
            .build()
        locationClient.getCurrentLocation(request, token.token)
            .addOnSuccessListener { location ->
                location?.let { emit?.invoke(it.toPosition()) }
            }
    }

    private fun startActivityMonitoring() {
        val transitionIntent = Intent(ACTIVITY_TRANSITION_ACTION).setPackage(appContext.packageName)
        val transitionPendingIntent = PendingIntent.getBroadcast(
            appContext,
            0,
            transitionIntent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        activityPendingIntent = transitionPendingIntent

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == ACTIVITY_TRANSITION_ACTION) {
                    handleTransition(intent)
                }
            }
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(ACTIVITY_TRANSITION_ACTION),
            ContextCompat.RECEIVER_EXPORTED,
        )
        transitionReceiver = receiver

        val activityTypes = listOf(
            DetectedActivity.STILL,
            DetectedActivity.IN_VEHICLE,
            DetectedActivity.ON_BICYCLE,
            DetectedActivity.RUNNING,
            DetectedActivity.WALKING,
        )
        val transitionTypes = listOf(
            ActivityTransition.ACTIVITY_TRANSITION_ENTER,
            ActivityTransition.ACTIVITY_TRANSITION_EXIT,
        )
        val transitions = activityTypes.flatMap { activityType ->
            transitionTypes.map { transitionType ->
                ActivityTransition.Builder()
                    .setActivityType(activityType)
                    .setActivityTransition(transitionType)
                    .build()
            }
        }
        activityClient.requestActivityTransitionUpdates(
            ActivityTransitionRequest(transitions),
            transitionPendingIntent,
        )
            .addOnSuccessListener { Log.log("Activity transition updates registered") }
            .addOnFailureListener { Log.log("Activity transition updates failed: $it") }
    }

    private fun handleTransition(intent: Intent) {
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        Log.log("Activity transition: ${result.transitionEvents.joinToString { "${activityName(it.activityType)}/${transitionName(it.transitionType)}" }}")
        result.transitionEvents.forEach { event ->
            if (event.activityType != DetectedActivity.STILL) return@forEach
            if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                onStillEnter()
            } else {
                onStillExit()
            }
        }
    }

    private fun onStillEnter() {
        if (locationCallback == null) return
        stopTimeoutJob?.cancel()
        stopTimeoutJob = scope?.launch {
            delay(config.stopTimeoutSeconds.seconds)
            Log.log("Stationary, pausing location updates")
            requestCurrentLocation()
            stopLocationUpdates()
            startHeartbeatLoop()
            stateStore.update { it.copy(paused = true) }
        }
    }

    private fun onStillExit() {
        stopTimeoutJob?.cancel()
        stopTimeoutJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        Log.log("Moving, resuming location updates")
        try {
            startLocationUpdates()
        } catch (_: SecurityException) {
        }
        scope?.launch { stateStore.update { it.copy(paused = false) } }
    }

    private fun startHeartbeatLoop() {
        val intervalSeconds = config.heartbeatIntervalSeconds
        if (intervalSeconds <= 0) return
        heartbeatJob?.cancel()
        heartbeatJob = scope?.launch {
            while (true) {
                delay(intervalSeconds.seconds)
                Log.log("Heartbeat")
                emit?.invoke(Position(time = System.currentTimeMillis(), battery = readBattery()))
            }
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

private fun activityName(type: Int): String = when (type) {
    DetectedActivity.IN_VEHICLE -> "in_vehicle"
    DetectedActivity.ON_BICYCLE -> "on_bicycle"
    DetectedActivity.ON_FOOT -> "on_foot"
    DetectedActivity.STILL -> "still"
    DetectedActivity.WALKING -> "walking"
    DetectedActivity.RUNNING -> "running"
    DetectedActivity.TILTING -> "tilting"
    else -> "unknown"
}

private fun transitionName(type: Int): String = when (type) {
    ActivityTransition.ACTIVITY_TRANSITION_ENTER -> "enter"
    ActivityTransition.ACTIVITY_TRANSITION_EXIT -> "exit"
    else -> "unknown"
}
