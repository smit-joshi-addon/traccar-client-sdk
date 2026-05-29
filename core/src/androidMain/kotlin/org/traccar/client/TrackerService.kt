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
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android

class TrackerService : Service() {

    private lateinit var tracker: Tracker
    private var engine: TrackerEngine? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        tracker = Tracker.shared(applicationContext)
        Log.log("Service created")
    }

    @SuppressLint("WakelockTimeout")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground(NotificationConfig())

        val config = tracker.configStore.load()
        if (config == null) {
            Log.log("No saved config, stopping service")
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground(config.notification)

        if (engine == null) {
            if (config.wakeLock) {
                Log.log("Acquiring wakelock")
                wakeLock = getSystemService<PowerManager>()
                    ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "traccar:tracker")
                    ?.also { it.acquire() }
            }
            val useFused = isGooglePlayServicesAvailable(applicationContext)
            Log.log("Using ${if (useFused) "FusedLocationProvider" else "AndroidLocationProvider"}")
            val provider = if (useFused) {
                FusedLocationProvider(applicationContext, config.location)
            } else {
                AndroidLocationProvider(applicationContext, config.location)
            }
            engine = TrackerEngine(
                provider = provider,
                uploader = HttpUploader(config, HttpClient(Android)),
                queue = tracker.queue,
                network = AndroidNetworkMonitor(applicationContext),
                filter = LocationFilter(config.location),
            ).also { it.start() }
        }
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        Log.log("Service destroyed")
        engine?.stop()
        engine = null
        wakeLock?.release()
        wakeLock = null
        isRunning = false
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

        @Volatile
        internal var isRunning = false
            private set

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

        internal fun stop(context: Context) {
            context.stopService(Intent(context, TrackerService::class.java))
        }
    }
}

private fun isGooglePlayServicesAvailable(context: Context): Boolean =
    GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
