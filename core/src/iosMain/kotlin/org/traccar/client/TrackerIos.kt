package org.traccar.client

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import org.traccar.client.db.Database

fun createTracker(config: Config): Tracker = Tracker(
    provider = IosLocationProvider(),
    uploader = HttpUploader(config, HttpClient(Darwin)),
    queue = SqlDelightQueue(NativeSqliteDriver(Database.Schema, "tracker.db")),
)
