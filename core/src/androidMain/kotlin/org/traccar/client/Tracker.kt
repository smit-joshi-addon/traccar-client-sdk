package org.traccar.client

import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class Tracker internal constructor(
    private val activity: ComponentActivity,
    private val config: Config,
) {
    private val configStore = ConfigStore(sharedDriver(activity))

    init {
        TrackerService.ensureNotificationChannel(activity)
    }

    suspend fun start(): Boolean {
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

        configStore.save(config)
        WorkManager.getInstance(activity).enqueueUniquePeriodicWork(
            LIVENESS_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<TrackerLivenessWorker>(15, TimeUnit.MINUTES).build(),
        )
        TrackerService.start(activity)
        return true
    }

    fun stop() {
        WorkManager.getInstance(activity).cancelUniqueWork(LIVENESS_WORK_NAME)
        configStore.clear()
        TrackerService.stop(activity)
    }

    private companion object {
        const val LIVENESS_WORK_NAME = "tracker-liveness"
    }
}

fun createTracker(activity: ComponentActivity, config: Config): Tracker =
    Tracker(activity, config)
