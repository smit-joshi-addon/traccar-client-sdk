package org.traccar.client

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.dsl.koinApplication

class Tracker internal constructor(
    val config: Config,
    private val stateStore: StateStore,
    internal val engine: TrackerEngine,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val isTracking: StateFlow<Boolean> = stateStore.state
        .map { it.enabled }
        .stateIn(scope, SharingStarted.Eagerly, stateStore.state.value.enabled)

    fun start() {
        Log.log("Tracker start ${config.serverUrl} ${config.deviceId}")
        stateStore.update { it.copy(enabled = true) }
    }

    fun stop() {
        Log.log("Tracker stop")
        stateStore.update { it.copy(enabled = false, paused = false) }
    }

    suspend fun requestPosition(): Boolean = engine.requestPosition()

    suspend fun getLogs(): List<LogEntry> = Log.store?.all() ?: emptyList()

    suspend fun clearLogs() {
        Log.store?.clear()
    }
}

private val sharedMutex = Mutex()
private var sharedTrackerInstance: Tracker? = null

suspend fun sharedTracker(config: Config? = null): Tracker? = sharedMutex.withLock {
    sharedTrackerInstance?.let { return@withLock it }
    withContext(Dispatchers.IO) {
        val koin = koinApplication { modules(coreModule, platformModule) }.koin
        val driver: SqlDriver = koin.get()
        Log.store = LogStore(driver)
        val configStore: ConfigStore = koin.get()
        val effectiveConfig = config ?: configStore.load() ?: return@withContext null
        if (config != null) configStore.save(config)
        koin.declare(StateStore.create(koin.get()))
        koin.declare(effectiveConfig)
        val tracker = koin.get<Tracker>()
        sharedTrackerInstance = tracker
        tracker
    }
}
