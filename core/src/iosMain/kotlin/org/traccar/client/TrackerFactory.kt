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
    Log.store = LogStore(driver)
    val httpClient = HttpClient(Darwin)
    val engineBuilder = EngineBuilder(
        queue = DatabaseQueue(driver),
        networkMonitor = IosNetworkMonitor(),
        createProvider = { IosLocationProvider(it) },
        createUploader = { HttpUploader(it, httpClient) },
    )
    var engine: TrackerEngine? = null
    Tracker(
        configStore = ConfigStore(driver),
        stateStore = StateStore(driver),
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
