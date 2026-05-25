package org.traccar.client

import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity

class Tracker internal constructor(
    private val activity: ComponentActivity,
    private val config: Config,
) {
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

        TrackerService.start(activity, config)
        return true
    }

    fun stop() {
        TrackerService.stop(activity)
    }
}

fun createTracker(activity: ComponentActivity, config: Config): Tracker =
    Tracker(activity, config)
