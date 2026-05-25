package org.traccar.client

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import org.traccar.client.db.Database

class Tracker internal constructor(
    private val engine: TrackerEngine,
) {
    fun start() = engine.start()
    fun stop() = engine.stop()
}

fun createTracker(config: Config): Tracker = Tracker(
    TrackerEngine(
        provider = IosLocationProvider(config.location),
        uploader = HttpUploader(config, HttpClient(Darwin)),
        queue = SqlDelightQueue(NativeSqliteDriver(Database.Schema, "tracker.db")),
        network = IosNetworkMonitor(),
        filter = LocationFilter(config.location),
    ),
)
