package org.traccar.client

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TrackerService : Service() {

    private var engine: TrackerEngine? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Log.log("Service created")
    }

    @SuppressLint("WakelockTimeout")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground(NotificationConfig())

        serviceScope.launch {
            val tracker = sharedTracker()
            val config = tracker.loadConfig()
            if (config == null || !tracker.isTracking.value) {
                Log.log("Tracking not enabled, stopping service")
                stopSelf()
                return@launch
            }
            startInForeground(config.notification)
            if (config.wakeLock && wakeLock == null) {
                Log.log("Acquiring wakelock")
                wakeLock = getSystemService<PowerManager>()
                    ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "traccar:tracker")
                    ?.also { it.acquire() }
            }
            if (engine == null) {
                engine = tracker.engineBuilder.build(config).also { it.start() }
            }
        }
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        Log.log("Service destroyed")
        serviceScope.cancel()
        engine?.stop()
        engine = null
        wakeLock?.release()
        wakeLock = null
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

        internal fun start(context: Context) {
            val intent = Intent(context, TrackerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        internal fun stop() {
            applicationContext.stopService(Intent(applicationContext, TrackerService::class.java))
        }
    }
}
