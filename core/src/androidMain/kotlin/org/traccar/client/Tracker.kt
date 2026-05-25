package org.traccar.client

import android.Manifest
import androidx.activity.ComponentActivity
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import org.traccar.client.db.Database

class Tracker internal constructor(
    private val activity: ComponentActivity,
    private val engine: TrackerEngine,
) {
    suspend fun start(): Boolean {
        if (!hasPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) &&
            !requestPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
        ) {
            return false
        }
        engine.start()
        return true
    }

    fun stop() {
        engine.stop()
    }
}

fun createTracker(activity: ComponentActivity, config: Config): Tracker = Tracker(
    activity = activity,
    engine = TrackerEngine(
        provider = AndroidGpsProvider(activity),
        uploader = HttpUploader(config, HttpClient(Android)),
        queue = SqlDelightQueue(AndroidSqliteDriver(Database.Schema, activity, "tracker.db")),
    ),
)
