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
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import kotlinx.serialization.json.Json
import org.traccar.client.db.Database

class TrackerService : Service() {

    private var engine: TrackerEngine? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()

        val configJson = intent?.getStringExtra(EXTRA_CONFIG) ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        val config = Json.decodeFromString<Config>(configJson)

        if (engine == null) {
            val provider = if (isGooglePlayServicesAvailable(applicationContext)) {
                FusedLocationProvider(applicationContext, config.location)
            } else {
                AndroidLocationProvider(applicationContext, config.location)
            }
            engine = TrackerEngine(
                provider = provider,
                uploader = HttpUploader(config, HttpClient(Android)),
                queue = DatabaseQueue(
                    AndroidSqliteDriver(Database.Schema, applicationContext, "tracker.db"),
                ),
                network = AndroidNetworkMonitor(applicationContext),
                filter = LocationFilter(config.location),
            ).also { it.start() }
        }
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        engine?.stop()
        engine = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startInForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tracker_notification)
            .setContentText("Location tracking")
            .setOngoing(true)
            .build()

    companion object {
        private const val CHANNEL_ID = "tracker"
        private const val NOTIFICATION_ID = 0x7AC0
        private const val EXTRA_CONFIG = "config"

        internal fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService<NotificationManager>() ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Location tracking", NotificationManager.IMPORTANCE_LOW),
            )
        }

        internal fun start(context: Context, config: Config) {
            val intent = Intent(context, TrackerService::class.java).apply {
                putExtra(EXTRA_CONFIG, Json.encodeToString(config))
            }
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
