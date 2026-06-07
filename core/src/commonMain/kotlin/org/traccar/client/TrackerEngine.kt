package org.traccar.client

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TrackerEngine internal constructor(
    private val configStore: ConfigStore,
    private val stateStore: StateStore,
    private val queue: PositionQueue,
    private val network: NetworkMonitor,
    private val createProvider: (LocationConfig) -> PositionProvider,
    private val createUploader: (Config) -> Uploader,
    private val initialBackoff: Duration = 5.seconds,
    private val maxBackoff: Duration = 5.minutes,
) {
    private val mutex = Mutex()
    private var runningScope: CoroutineScope? = null
    private val wakeUp = Channel<Unit>(Channel.CONFLATED)

    suspend fun handle(signal: Signal) = mutex.withLock {
        when (signal) {
            Signal.Restore -> applyRestore()
            Signal.StationaryEnter,
            Signal.StationaryExit,
            Signal.HeartbeatTick -> Log.log("Signal ${signal::class.simpleName} not yet handled")
        }
    }

    private suspend fun applyRestore() {
        val state = stateStore.state.value
        if (!state.enabled) {
            stopRunning()
            return
        }
        val config = configStore.load() ?: run {
            Log.log("No saved config; engine staying down")
            stopRunning()
            return
        }
        startRunning(config)
    }

    private fun startRunning(config: Config) {
        if (runningScope != null) return
        Log.log("Engine running")
        val provider = createProvider(config.location)
        val uploader = createUploader(config)
        val filter = LocationFilter(config.location, stateStore)
        val buffer = config.buffer
        runningScope = CoroutineScope(SupervisorJob() + Dispatchers.Default).apply {
            launch {
                provider.positions()
                    .filter { filter.accept(it) }
                    .collect { position ->
                        if (buffer) {
                            queue.enqueue(position)
                            wakeUp.trySend(Unit)
                        } else {
                            uploader.upload(position)
                        }
                    }
            }
            launch { syncLoop(uploader) }
        }
    }

    private fun stopRunning() {
        if (runningScope == null) return
        Log.log("Engine stopped")
        runningScope?.cancel()
        runningScope = null
    }

    private suspend fun syncLoop(uploader: Uploader) {
        var backoff = initialBackoff
        while (currentCoroutineContext().isActive) {
            val pending = queue.peek()
            when {
                pending == null -> wakeUp.receive()
                !network.isOnline.value -> {
                    Log.log("Offline, waiting for network")
                    network.isOnline.first { it }
                }
                uploader.upload(pending) -> {
                    queue.removeFirst()
                    backoff = initialBackoff
                }
                else -> {
                    Log.log("Upload failed, retrying in $backoff")
                    delay(backoff)
                    backoff = (backoff * 2).coerceAtMost(maxBackoff)
                }
            }
        }
    }
}
