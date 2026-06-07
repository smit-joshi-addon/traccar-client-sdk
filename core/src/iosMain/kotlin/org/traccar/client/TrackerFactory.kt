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
    val configStore = ConfigStore(driver)
    val queue = DatabaseQueue(driver)
    val networkMonitor = IosNetworkMonitor()
    val httpClient = HttpClient(Darwin)
    val createProvider: (LocationConfig) -> PositionProvider = { IosLocationProvider(it, stateStore) }
    val createUploader: (Config) -> Uploader = { HttpUploader(it, httpClient) }
    val engine = TrackerEngine(
        configStore = configStore,
        stateStore = stateStore,
        queue = queue,
        network = networkMonitor,
        createProvider = createProvider,
        createUploader = createUploader,
    )
    IosBackgroundHeartbeat.bind(
        queue = queue,
        configProvider = { configStore.load() },
        createUploader = createUploader,
    )
    Tracker(
        configStore = configStore,
        stateStore = stateStore,
        engine = engine,
        createProvider = createProvider,
        createUploader = createUploader,
        onStarted = { },
        onStopped = { },
    )
}
