package org.traccar.client

import android.content.Context
import androidx.activity.ComponentActivity
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android

fun createTracker(config: Config, activity: ComponentActivity): Tracker = Tracker(
    provider = AndroidGpsProvider(activity, activity),
    uploader = HttpUploader(config, HttpClient(Android)),
)

fun createTracker(config: Config, context: Context): Tracker = Tracker(
    provider = AndroidGpsProvider(context),
    uploader = HttpUploader(config, HttpClient(Android)),
)
