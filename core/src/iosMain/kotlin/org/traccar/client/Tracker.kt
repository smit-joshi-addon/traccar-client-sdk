package org.traccar.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

class Tracker internal constructor(
    private val config: Config,
    private val engine: TrackerEngine,
) {
    private val configStore = ConfigStore(sharedDriver)

    fun start() {
        configStore.save(config)
        engine.start()
    }

    fun stop() {
        engine.stop()
        configStore.clear()
    }
}

fun createTracker(config: Config): Tracker = Tracker(
    config = config,
    engine = TrackerEngine(
        provider = IosLocationProvider(config.location),
        uploader = HttpUploader(config, HttpClient(Darwin)),
        queue = DatabaseQueue(sharedDriver),
        network = IosNetworkMonitor(),
        filter = LocationFilter(config.location),
    ),
)
