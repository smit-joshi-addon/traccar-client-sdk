package org.traccar.client

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import java.util.concurrent.TimeUnit
import org.traccar.client.db.Database

object Tracker {

    private const val LIVENESS_WORK_NAME = "tracker-liveness"
    private const val PREFERENCES_NAME = "traccar-client-sdk"
    private const val BATTERY_PROMPTED_KEY = "battery-prompted"

    private lateinit var configStoreInstance: ConfigStore
    private lateinit var queueInstance: DatabaseQueue

    @Synchronized
    private fun bootstrap(context: Context) {
        if (::configStoreInstance.isInitialized) return
        val driver = AndroidSqliteDriver(Database.Schema, context.applicationContext, "tracker.db")
        configStoreInstance = ConfigStore(driver)
        queueInstance = DatabaseQueue(driver)
    }

    internal fun configStore(context: Context): ConfigStore {
        bootstrap(context)
        return configStoreInstance
    }

    internal fun queue(context: Context): DatabaseQueue {
        bootstrap(context)
        return queueInstance
    }

    suspend fun start(activity: ComponentActivity, config: Config): Boolean {
        TrackerService.ensureNotificationChannel(activity)

        val foreground = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && config.location.stopDetection) {
                add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }
        val missing = foreground.filterNot { hasPermission(activity, it) }
        if (missing.isNotEmpty()) {
            val results = requestPermissions(activity, missing)
            if (results.values.any { !it }) return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !hasPermission(activity, Manifest.permission.ACCESS_BACKGROUND_LOCATION) &&
            !requestPermission(activity, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        ) {
            return false
        }

        val powerManager = activity.getSystemService<PowerManager>()
        if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(activity.packageName)) {
            val preferences = activity.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            if (!preferences.getBoolean(BATTERY_PROMPTED_KEY, false)) {
                activity.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                preferences.edit { putBoolean(BATTERY_PROMPTED_KEY, true) }
            }
        }

        configStore(activity).save(config)
        WorkManager.getInstance(activity).enqueueUniquePeriodicWork(
            LIVENESS_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<TrackerLivenessWorker>(15, TimeUnit.MINUTES).build(),
        )
        TrackerService.start(activity)
        return true
    }

    fun stop(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(LIVENESS_WORK_NAME)
        configStore(context).clear()
        TrackerService.stop(context)
    }
}
