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
import com.google.android.gms.location.ActivityRecognitionResult
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
) : BaseLocationProvider(context, config.effective) {

    private val locationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(appContext)
    private val activityClient: ActivityRecognitionClient =
        ActivityRecognition.getClient(appContext)

    private var emit: ((Position) -> Unit)? = null
    private var locationCallback: LocationCallback? = null
    private var activityPendingIntent: PendingIntent? = null
    private var activitySnapshotPendingIntent: PendingIntent? = null
    private var transitionReceiver: BroadcastReceiver? = null
    private var stopTimeoutJob: Job? = null
    private var scope: CoroutineScope? = null
    private var currentLocationToken: CancellationTokenSource? = null

    override suspend fun start(emit: (Position) -> Unit) {
        this.emit = emit
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        startLocationUpdates()
        requestCurrentLocation()
        if (config.stopDetection) {
            startActivityMonitoring()
        }
    }

    override fun stop() {
        currentLocationToken?.cancel()
        currentLocationToken = null
        stopTimeoutJob?.cancel()
        stopTimeoutJob = null
        stopLocationUpdates()
        activityPendingIntent?.let { activityClient.removeActivityTransitionUpdates(it) }
        activityPendingIntent = null
        activitySnapshotPendingIntent?.let { activityClient.removeActivityUpdates(it) }
        activitySnapshotPendingIntent = null
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

        val snapshotIntent = Intent(ACTIVITY_SNAPSHOT_ACTION).setPackage(appContext.packageName)
        val snapshotPendingIntent = PendingIntent.getBroadcast(
            appContext,
            1,
            snapshotIntent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        activitySnapshotPendingIntent = snapshotPendingIntent

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    ACTIVITY_TRANSITION_ACTION -> handleTransition(intent)
                    ACTIVITY_SNAPSHOT_ACTION -> handleSnapshot(intent)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(ACTIVITY_TRANSITION_ACTION)
            addAction(ACTIVITY_SNAPSHOT_ACTION)
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
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
        activityClient.requestActivityTransitionUpdates(
            ActivityTransitionRequest(transitions),
            transitionPendingIntent,
        )
        activityClient.requestActivityUpdates(0, snapshotPendingIntent)
    }

    private fun handleTransition(intent: Intent) {
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

    private fun handleSnapshot(intent: Intent) {
        val result = ActivityRecognitionResult.extractResult(intent) ?: return
        activitySnapshotPendingIntent?.let { activityClient.removeActivityUpdates(it) }
        activitySnapshotPendingIntent = null
        if (result.mostProbableActivity.type == DetectedActivity.STILL) {
            onStillEnter()
        }
    }

    private fun onStillEnter() {
        stopTimeoutJob?.cancel()
        stopTimeoutJob = scope?.launch {
            delay(config.stopTimeoutSeconds * 1000L)
            Log.log("Stationary, pausing location updates")
            stopLocationUpdates()
        }
    }

    private fun onStillExit() {
        stopTimeoutJob?.cancel()
        stopTimeoutJob = null
        Log.log("Moving, resuming location updates")
        try {
            startLocationUpdates()
        } catch (_: SecurityException) {
        }
    }

    private companion object {
        const val ACTIVITY_TRANSITION_ACTION = "org.traccar.client.ACTIVITY_TRANSITION"
        const val ACTIVITY_SNAPSHOT_ACTION = "org.traccar.client.ACTIVITY_SNAPSHOT"
    }
}

private fun Accuracy.toFusedPriority(): Int = when (this) {
    Accuracy.HIGHEST, Accuracy.HIGH -> Priority.PRIORITY_HIGH_ACCURACY
    Accuracy.MEDIUM -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
    Accuracy.LOW -> Priority.PRIORITY_LOW_POWER
}
