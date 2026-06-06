package org.traccar.client

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import org.traccar.client.db.Database

object Tracker {

    private val configStore: ConfigStore
    private val stateStore: StateStore
    private val queue: DatabaseQueue
    private var engine: TrackerEngine? = null

    val isTracking: Boolean get() = stateStore.load().enabled

    init {
        val driver = NativeSqliteDriver(Database.Schema, "tracker.db")
        configStore = ConfigStore(driver)
        stateStore = StateStore(driver)
        queue = DatabaseQueue(driver)
        Log.store = LogStore(driver)
    }

    fun start(config: Config) {
        Log.log("Tracker start ${config.serverUrl} ${config.deviceId}")
        configStore.save(config)
        stateStore.save(stateStore.load().copy(enabled = true))
        resume()
    }

    fun stop() {
        Log.log("Tracker stop")
        stateStore.save(stateStore.load().copy(enabled = false))
        engine?.stop()
        engine = null
    }

    fun resume() {
        if (engine != null) return
        if (!stateStore.load().enabled) return
        val config = configStore.load() ?: return
        Log.log("Tracker resume")
        engine = TrackerEngine(
            provider = IosLocationProvider(config.location),
            uploader = HttpUploader(config, HttpClient(Darwin)),
            queue = queue,
            network = IosNetworkMonitor(),
            filter = LocationFilter(config.location),
            buffer = config.buffer,
        ).also { it.start() }
    }

    fun requestPosition(config: Config) {
        TrackerEngine.oneShotUpload(
            provider = IosLocationProvider(config.location.copy(stopDetection = false)),
            config = config,
            httpClient = HttpClient(Darwin),
        )
    }

    fun getLogs(): List<LogEntry> = Log.store?.all() ?: emptyList()

    fun clearLogs() {
        Log.store?.clear()
    }
}
