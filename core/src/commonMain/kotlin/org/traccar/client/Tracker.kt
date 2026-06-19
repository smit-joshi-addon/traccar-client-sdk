package org.traccar.client

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.Koin
import org.koin.dsl.koinApplication

class Tracker internal constructor(
    val config: Config,
    private val stateStore: StateStore,
    private val locationSource: LocationSource,
    private val batteryProcessor: PositionProcessor,
    private val uploader: Uploader,
    private val componentScope: ComponentCoroutineScope,
) {
    val state: StateFlow<State> = stateStore.state

    suspend fun start() = sharedMutex.withLock {
        Log.log("Tracker start ${config.serverUrl} ${config.deviceId}")
        stateStore.update { it.copy(enabled = true) }
    }

    suspend fun stop() = sharedMutex.withLock {
        Log.log("Tracker stop")
        stateStore.update { it.copy(enabled = false, paused = false) }
    }

    suspend fun requestPosition(): Boolean {
        val raw = locationSource.fetchOnce() ?: return false
        val processed = batteryProcessor.process(raw) ?: return false
        return uploader.upload(processed)
    }

    suspend fun getLogs(): List<LogEntry> = Log.store?.all() ?: emptyList()

    suspend fun clearLogs() = Log.store?.clear()

    suspend fun updateConfig(newConfig: Config): Tracker = sharedMutex.withLock {
        componentScope.coroutineContext.job.cancelAndJoin()
        sharedTrackerInstance = null
        bootstrap(newConfig)
    }
}

private val sharedMutex = Mutex()
private var sharedTrackerInstance: Tracker? = null

suspend fun sharedTracker(): Tracker? = sharedMutex.withLock {
    sharedTrackerInstance?.let { return@withLock it }
    val koin = openKoin()
    val persisted = koin.get<ConfigStore>().load() ?: return@withLock null
    install(koin, persisted)
}

suspend fun initTracker(config: Config): Tracker = sharedMutex.withLock {
    bootstrap(config)
}

private suspend fun bootstrap(config: Config): Tracker {
    val koin = openKoin()
    koin.get<ConfigStore>().save(config)
    return install(koin, config)
}

private suspend fun openKoin() = withContext(Dispatchers.IO) {
    val koin = koinApplication { modules(coreModule, platformModule) }.koin
    Log.store = LogStore(koin.get())
    koin
}

private suspend fun install(koin: Koin, config: Config): Tracker {
    koin.declare(StateStore.create(koin.get()))
    koin.declare(config)
    koin.get<TrackerEngine>()
    return koin.get<Tracker>().also { sharedTrackerInstance = it }
}
