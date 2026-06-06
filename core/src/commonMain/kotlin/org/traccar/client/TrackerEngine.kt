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
import kotlinx.coroutines.withTimeoutOrNull

class TrackerEngine(
    private val provider: PositionProvider,
    private val uploader: Uploader,
    private val queue: PositionQueue,
    private val network: NetworkMonitor,
    private val filter: PositionFilter,
    private val buffer: Boolean = true,
    private val initialBackoff: Duration = 5.seconds,
    private val maxBackoff: Duration = 5.minutes,
) {
    private var scope: CoroutineScope? = null
    private val wakeUp = Channel<Unit>(Channel.CONFLATED)

    fun start() {
        if (scope != null) return
        Log.log("Engine started")
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default).apply {
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
            launch { syncLoop() }
        }
    }

    fun stop() {
        Log.log("Engine stopped")
        scope?.cancel()
        scope = null
    }

    private suspend fun syncLoop() {
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

    companion object {
        fun oneShotUpload(provider: PositionProvider, uploader: Uploader) {
            Log.log("Request position")
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                try {
                    val position = withTimeoutOrNull(30.seconds) {
                        provider.positions().first()
                    }
                    if (position != null) {
                        uploader.upload(position)
                    } else {
                        Log.log("Request position timed out")
                    }
                } catch (e: Throwable) {
                    Log.log("Request position failed: ${e.message}")
                }
            }
        }
    }
}
