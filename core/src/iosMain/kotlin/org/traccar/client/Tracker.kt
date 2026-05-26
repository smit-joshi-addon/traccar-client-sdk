package org.traccar.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

object Tracker {

    private val configStore = ConfigStore(sharedDriver)
    private var engine: TrackerEngine? = null

    fun start(config: Config) {
        configStore.save(config)
        resume()
    }

    fun stop() {
        engine?.stop()
        engine = null
        configStore.clear()
    }

    fun resume() {
        if (engine != null) return
        val config = configStore.load() ?: return
        engine = TrackerEngine(
            provider = IosLocationProvider(config.location),
            uploader = HttpUploader(config, HttpClient(Darwin)),
            queue = DatabaseQueue(sharedDriver),
            network = IosNetworkMonitor(),
            filter = LocationFilter(config.location),
        ).also { it.start() }
    }
}
