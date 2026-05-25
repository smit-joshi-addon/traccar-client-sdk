package org.traccar.client

import android.content.Context
import androidx.activity.ComponentActivity
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import org.traccar.client.db.Database

fun createTracker(config: Config, activity: ComponentActivity): Tracker = Tracker(
    provider = AndroidGpsProvider(activity, activity),
    uploader = HttpUploader(config, HttpClient(Android)),
    queue = SqlDelightQueue(AndroidSqliteDriver(Database.Schema, activity, "tracker.db")),
)

fun createTracker(config: Config, context: Context): Tracker = Tracker(
    provider = AndroidGpsProvider(context),
    uploader = HttpUploader(config, HttpClient(Android)),
    queue = SqlDelightQueue(AndroidSqliteDriver(Database.Schema, context, "tracker.db")),
)
