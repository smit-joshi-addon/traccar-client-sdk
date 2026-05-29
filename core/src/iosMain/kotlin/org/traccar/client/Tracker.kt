package org.traccar.client

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import org.traccar.client.db.Database

object Tracker {

    private val configStore: ConfigStore
    private val queue: DatabaseQueue
    private var engine: TrackerEngine? = null

    init {
        val driver = NativeSqliteDriver(Database.Schema, "tracker.db")
        configStore = ConfigStore(driver)
        queue = DatabaseQueue(driver)
        Log.store = LogStore(driver)
    }

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
            queue = queue,
            network = IosNetworkMonitor(),
            filter = LocationFilter(config.location),
        ).also { it.start() }
    }

    fun getLogs(): List<LogEntry> = Log.store?.all() ?: emptyList()

    fun clearLogs() {
        Log.store?.clear()
    }
}
