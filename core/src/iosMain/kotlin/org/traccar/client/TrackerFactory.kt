package org.traccar.client

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.traccar.client.db.Database

internal actual suspend fun createTracker(): Tracker = withContext(Dispatchers.IO) {
    val driver = NativeSqliteDriver(Database.Schema, "tracker.db")
    enableBatteryMonitoring()
    Log.store = LogStore(driver)
    val stateStore = StateStore.create(driver)
    val httpClient = HttpClient(Darwin)
    val queue = DatabaseQueue(driver)
    val configStore = ConfigStore(driver)
    val createUploader: (Config) -> Uploader = { HttpUploader(it, httpClient) }
    val engineBuilder = EngineBuilder(
        queue = queue,
        stateStore = stateStore,
        networkMonitor = IosNetworkMonitor(),
        createProvider = { IosLocationProvider(it, stateStore) },
        createUploader = createUploader,
    )
    IosBackgroundHeartbeat.bind(
        queue = queue,
        configProvider = { configStore.load() },
        createUploader = createUploader,
    )
    var engine: TrackerEngine? = null
    Tracker(
        configStore = configStore,
        stateStore = stateStore,
        engineBuilder = engineBuilder,
        onStarted = { config ->
            if (engine == null) engine = engineBuilder.build(config).also { it.start() }
        },
        onStopped = {
            engine?.stop()
            engine = null
        },
    )
}
