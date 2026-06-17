package org.traccar.client

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
class ActivityRecognitionDetector(
    private val scope: ComponentCoroutineScope,
    context: Context,
    config: Config,
    state: StateFlow<State>,
) : SignalSource {

    private val stopTimeoutSeconds = config.location.stopTimeoutSeconds

    private val appContext = context.applicationContext
    private val client = ActivityRecognition.getClient(appContext)

    override val signals = MutableSharedFlow<Signal>(extraBufferCapacity = 8)

    private var pendingIntent: PendingIntent? = null
    private var receiver: BroadcastReceiver? = null
    private var stopTimeoutJob: Job? = null

    init {
        scope.observeActive(state, { it.enabled && !it.paused }) { active ->
            if (active) ensureRegistered() else ensureUnregistered()
        }
    }

    private fun ensureRegistered() {
        if (pendingIntent != null) return
        val intent = Intent(ACTION).setPackage(appContext.packageName)
        val newPendingIntent = PendingIntent.getBroadcast(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        pendingIntent = newPendingIntent

        val newReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == ACTION) handleResult(intent)
            }
        }
        ContextCompat.registerReceiver(
            appContext,
            newReceiver,
            IntentFilter(ACTION),
            ContextCompat.RECEIVER_EXPORTED,
        )
        receiver = newReceiver

        val request = ActivityTransitionRequest(
            listOf(DetectedActivity.STILL, DetectedActivity.IN_VEHICLE, DetectedActivity.ON_BICYCLE, DetectedActivity.RUNNING, DetectedActivity.WALKING).flatMap { activity ->
                listOf(ActivityTransition.ACTIVITY_TRANSITION_ENTER, ActivityTransition.ACTIVITY_TRANSITION_EXIT).map { transition ->
                    ActivityTransition.Builder().setActivityType(activity).setActivityTransition(transition).build()
                }
            }
        )
        client.requestActivityTransitionUpdates(request, newPendingIntent)
            .addOnSuccessListener { Log.log("Activity transitions registered") }
            .addOnFailureListener { Log.log("Activity transitions failed: $it") }
    }

    private fun ensureUnregistered() {
        stopTimeoutJob?.cancel()
        stopTimeoutJob = null
        pendingIntent?.let { client.removeActivityTransitionUpdates(it) }
        pendingIntent = null
        receiver?.let { appContext.unregisterReceiver(it) }
        receiver = null
    }

    private fun handleResult(intent: Intent) {
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        result.transitionEvents.forEach { event ->
            if (event.activityType != DetectedActivity.STILL) return@forEach
            if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) onStillEnter()
            else onStillExit()
        }
    }

    private fun onStillEnter() {
        stopTimeoutJob?.cancel()
        stopTimeoutJob = scope.launch {
            delay(stopTimeoutSeconds.seconds)
            signals.emit(Signal.StationaryEnter)
        }
    }

    private fun onStillExit() {
        stopTimeoutJob?.cancel()
        stopTimeoutJob = null
        scope.launch { signals.emit(Signal.StationaryExit) }
    }

    private companion object {
        const val ACTION = "org.traccar.client.ACTIVITY_TRANSITION"
    }
}
