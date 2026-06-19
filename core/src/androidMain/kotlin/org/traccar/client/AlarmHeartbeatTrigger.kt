package org.traccar.client

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.AlarmManagerCompat
import androidx.core.content.getSystemService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AlarmHeartbeatTrigger(
    scope: ComponentCoroutineScope,
    context: Context,
    config: Config,
    private val state: StateFlow<State>,
) : SignalSource {

    private val intervalSeconds = config.location.heartbeatIntervalSeconds
    private val appContext = context.applicationContext
    private val alarmManager: AlarmManager = checkNotNull(appContext.getSystemService())

    override val signals = MutableSharedFlow<Signal>(extraBufferCapacity = 8)

    init {
        scope.launch {
            HeartbeatReceiver.events.collect {
                signals.emit(Signal.HeartbeatTick)
                if (state.value.enabled && state.value.paused) scheduleNext()
            }
        }
        scope.observeState(state, { it.enabled && it.paused }, inactive = false) { active ->
            if (active) scheduleNext() else cancelScheduled()
        }
    }

    private fun scheduleNext() {
        if (intervalSeconds <= 0) return
        val triggerAt = nowMillis() + intervalSeconds * 1000L
        AlarmManagerCompat.setAndAllowWhileIdle(
            alarmManager,
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pendingIntent(),
        )
        Log.log("Heartbeat alarm scheduled in ${intervalSeconds}s")
    }

    private fun cancelScheduled() {
        alarmManager.cancel(pendingIntent())
        Log.log("Heartbeat alarm cancelled")
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        0,
        Intent(appContext, HeartbeatReceiver::class.java),
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

class HeartbeatReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.log("Heartbeat alarm fired")
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                sharedTracker()
                events.tryEmit(Unit)
            } finally {
                pending.finish()
            }
        }
    }

    internal companion object {
        val events = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 8)
    }
}
