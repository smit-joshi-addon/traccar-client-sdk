package org.traccar.client

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@SuppressLint("MissingPermission")
class GeofenceDetector(
    private val scope: ComponentCoroutineScope,
    context: Context,
    config: Config,
    private val state: StateFlow<State>,
    private val incomingPositions: Flow<Position>,
) : SignalSource {

    private val radius = config.location.stationaryRadiusMeters

    private val appContext = context.applicationContext
    private val client: GeofencingClient = LocationServices.getGeofencingClient(appContext)

    override val signals = MutableSharedFlow<Signal>(extraBufferCapacity = 8)

    private var pendingIntent: PendingIntent? = null
    private var receiver: BroadcastReceiver? = null

    init {
        scope.observeActive(state, { it.enabled && it.paused }) { active ->
            if (active) tryRegister() else unregister()
        }
    }

    private suspend fun tryRegister() {
        if (pendingIntent != null) return
        val anchor = waitForAnchor() ?: run {
            Log.log("Geofence: no anchor available")
            return
        }
        if (anchor.latitude == null || anchor.longitude == null) return
        register(anchor.latitude, anchor.longitude, radius)
    }

    private suspend fun waitForAnchor(): Position? {
        val fresh = withTimeoutOrNull(WAIT_TIMEOUT) { incomingPositions.first() }
        return fresh ?: state.value.lastAcceptedLocation
    }

    private fun register(latitude: Double, longitude: Double, radius: Int) {
        val geofence = Geofence.Builder()
            .setRequestId(REQUEST_ID)
            .setCircularRegion(latitude, longitude, radius.toFloat())
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0)
            .addGeofence(geofence)
            .build()
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
                if (intent.action == ACTION) handleEvent(intent)
            }
        }
        ContextCompat.registerReceiver(
            appContext,
            newReceiver,
            IntentFilter(ACTION),
            ContextCompat.RECEIVER_EXPORTED,
        )
        receiver = newReceiver

        client.addGeofences(request, newPendingIntent)
            .addOnSuccessListener { Log.log("Geofence registered") }
            .addOnFailureListener { Log.log("Geofence registration failed: $it") }
    }

    private fun unregister() {
        pendingIntent?.let { client.removeGeofences(it) }
        pendingIntent = null
        receiver?.let { appContext.unregisterReceiver(it) }
        receiver = null
    }

    private fun handleEvent(intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.log("Geofence error: ${event.errorCode}")
            return
        }
        if (event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            scope.launch { signals.emit(Signal.StationaryExit) }
        }
    }

    private companion object {
        const val ACTION = "org.traccar.client.GEOFENCE_TRANSITION"
        const val REQUEST_ID = "traccar.stationary"
        val WAIT_TIMEOUT = 10.seconds
    }
}
