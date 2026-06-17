package org.traccar.client

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

class ForegroundServiceHolder(
    scope: ComponentCoroutineScope,
    context: Context,
    state: StateFlow<State>,
) : SignalSource {

    override val signals: Flow<Signal> = emptyFlow()

    private val appContext = context.applicationContext

    init {
        scope.observeActive(state, { it.enabled }) { enabled ->
            if (enabled) start() else stop()
        }
    }

    private fun start() {
        val intent = Intent(appContext, TrackerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(intent)
        } else {
            appContext.startService(intent)
        }
    }

    private fun stop() {
        appContext.stopService(Intent(appContext, TrackerService::class.java))
    }
}

class TrackerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Log.log("Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground(NotificationConfig())

        serviceScope.launch {
            sharedTracker()?.let { startInForeground(it.config.notification) }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.log("Service destroyed")
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startInForeground(settings: NotificationConfig) {
        val notification = buildNotification(settings)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(settings: NotificationConfig): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tracker_notification)
            .setContentText(settings.text)
            .setOngoing(true)
            .build()

    companion object {
        private const val CHANNEL_ID = "tracker"
        private const val NOTIFICATION_ID = 0x7AC0

        internal fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService<NotificationManager>() ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Location tracking", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }
}
