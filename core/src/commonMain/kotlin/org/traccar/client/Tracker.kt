package org.traccar.client

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

class Tracker internal constructor(
    private val configStore: ConfigStore,
    private val stateStore: StateStore,
    internal val engineBuilder: EngineBuilder,
    private val onStarted: (Config) -> Unit,
    private val onStopped: () -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val isTracking: StateFlow<Boolean> = stateStore.state
        .map { it.enabled }
        .stateIn(scope, SharingStarted.Eagerly, stateStore.state.value.enabled)

    private val lifecycleMutex = Mutex()

    suspend fun start(config: Config) = lifecycleMutex.withLock {
        Log.log("Tracker start ${config.serverUrl} ${config.deviceId}")
        configStore.save(config)
        stateStore.update { it.copy(enabled = true) }
        onStarted(config)
    }

    suspend fun stop() = lifecycleMutex.withLock {
        Log.log("Tracker stop")
        stateStore.update { it.copy(enabled = false) }
        onStopped()
    }

    suspend fun resume() = lifecycleMutex.withLock {
        if (!stateStore.state.value.enabled) return@withLock
        val config = configStore.load() ?: return@withLock
        Log.log("Tracker resume")
        onStarted(config)
    }

    suspend fun requestPosition(config: Config): Boolean {
        Log.log("Request position ${config.serverUrl} ${config.deviceId}")
        val provider = engineBuilder.createProvider(config.location.copy(stopDetection = false))
        val uploader = engineBuilder.createUploader(config)
        val position = withTimeoutOrNull(30.seconds) {
            provider.positions().first()
        }
        if (position == null) {
            Log.log("Request position timed out")
            return false
        }
        return uploader.upload(position)
    }

    internal suspend fun loadConfig(): Config? = configStore.load()

    suspend fun getLogs(): List<LogEntry> = Log.store?.all() ?: emptyList()

    suspend fun clearLogs() {
        Log.store?.clear()
    }
}

class EngineBuilder internal constructor(
    private val queue: PositionQueue,
    private val networkMonitor: NetworkMonitor,
    internal val createProvider: (LocationConfig) -> PositionProvider,
    internal val createUploader: (Config) -> Uploader,
) {
    fun build(config: Config): TrackerEngine = TrackerEngine(
        provider = createProvider(config.location),
        uploader = createUploader(config),
        queue = queue,
        network = networkMonitor,
        filter = LocationFilter(config.location),
        buffer = config.buffer,
    )
}

internal expect suspend fun createTracker(): Tracker

private val sharedScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

private val sharedTrackerDeferred: Deferred<Tracker> = sharedScope.async(start = CoroutineStart.LAZY) {
    createTracker()
}

suspend fun sharedTracker(): Tracker = sharedTrackerDeferred.await()
