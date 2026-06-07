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
    internal val engine: TrackerEngine,
    private val createProvider: (LocationConfig) -> PositionProvider,
    private val createUploader: (Config) -> Uploader,
    private val onStarted: () -> Unit,
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
        onStarted()
        engine.handle(Signal.Restore)
    }

    suspend fun stop() = lifecycleMutex.withLock {
        Log.log("Tracker stop")
        stateStore.update { it.copy(enabled = false, paused = false) }
        engine.handle(Signal.Restore)
        onStopped()
    }

    suspend fun restore() = lifecycleMutex.withLock {
        Log.log("Tracker restore")
        if (stateStore.state.value.enabled) onStarted()
        engine.handle(Signal.Restore)
    }

    suspend fun requestPosition(config: Config): Boolean {
        Log.log("Request position ${config.serverUrl} ${config.deviceId}")
        val provider = createProvider(config.location.copy(stopDetection = false))
        val uploader = createUploader(config)
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

internal expect suspend fun createTracker(): Tracker

private val sharedScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

private val sharedTrackerDeferred: Deferred<Tracker> = sharedScope.async(start = CoroutineStart.LAZY) {
    createTracker()
}

suspend fun sharedTracker(): Tracker = sharedTrackerDeferred.await()
