package org.traccar.client

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object Tracker {

    private const val LIVENESS_WORK_NAME = "tracker-liveness"

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

        ConfigStore(sharedDriver(activity)).save(config)
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
        ConfigStore(sharedDriver(context)).clear()
        TrackerService.stop(context)
    }
}
