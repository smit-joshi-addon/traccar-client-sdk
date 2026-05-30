package org.traccar.client

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import java.util.concurrent.TimeUnit
import org.traccar.client.db.Database

class Tracker private constructor(context: Context) {

    internal val configStore: ConfigStore
    internal val queue: DatabaseQueue

    val isTracking: Boolean get() = TrackerService.isRunning

    init {
        val driver = AndroidSqliteDriver(Database.Schema, context.applicationContext, "tracker.db")
        configStore = ConfigStore(driver)
        queue = DatabaseQueue(driver)
        Log.store = LogStore(driver)
    }

    suspend fun start(context: Context, config: Config): Boolean {
        Log.log("Tracker start ${config.serverUrl} ${config.deviceId}")
        TrackerService.ensureNotificationChannel(context)

        if (!ensurePermissions(context)) {
            Log.log("Permissions denied")
            return false
        }

        val powerManager = context.getSystemService<PowerManager>()
        if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
            val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            if (!preferences.getBoolean(BATTERY_PROMPTED_KEY, false)) {
                context.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                preferences.edit { putBoolean(BATTERY_PROMPTED_KEY, true) }
            }
        }

        configStore.save(config)
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            LIVENESS_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<TrackerLivenessWorker>(15, TimeUnit.MINUTES).build(),
        )
        TrackerService.start(context)
        return true
    }

    fun stop(context: Context) {
        Log.log("Tracker stop")
        WorkManager.getInstance(context).cancelUniqueWork(LIVENESS_WORK_NAME)
        configStore.clear()
        TrackerService.stop(context)
    }

    fun getLogs(): List<LogEntry> = Log.store?.all() ?: emptyList()

    fun clearLogs() {
        Log.store?.clear()
    }

    companion object {
        private const val LIVENESS_WORK_NAME = "tracker-liveness"
        private const val PREFERENCES_NAME = "traccar-client-sdk"
        private const val BATTERY_PROMPTED_KEY = "battery-prompted"

        @Volatile
        private var instance: Tracker? = null

        fun shared(context: Context): Tracker =
            instance ?: synchronized(this) {
                instance ?: Tracker(context).also { instance = it }
            }
    }
}
