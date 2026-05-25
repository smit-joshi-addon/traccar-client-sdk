package org.traccar.client

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TrackerEngine(
    private val provider: PositionProvider,
    private val uploader: Uploader,
    private val queue: PositionQueue,
    private val network: NetworkMonitor,
    private val filter: PositionFilter = NoOpFilter,
    private val initialBackoffMs: Long = 5_000,
    private val maxBackoffMs: Long = 5 * 60_000,
) {
    private var scope: CoroutineScope? = null
    private val wakeUp = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun start() {
        if (scope != null) return
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default).apply {
            launch {
                provider.positions()
                    .filter { filter.accept(it) }
                    .collect {
                        queue.enqueue(it)
                        wakeUp.tryEmit(Unit)
                    }
            }
            launch { syncLoop() }
        }
    }

    fun stop() {
        scope?.cancel()
        scope = null
    }

    private suspend fun syncLoop() {
        var backoff = initialBackoffMs
        while (currentCoroutineContext().isActive) {
            val pending = queue.peek()
            when {
                pending == null -> wakeUp.first()
                !network.isOnline.value -> network.isOnline.first { it }
                uploader.upload(pending) -> {
                    queue.removeFirst()
                    backoff = initialBackoffMs
                }
                else -> {
                    delay(backoff)
                    backoff = (backoff * 2).coerceAtMost(maxBackoffMs)
                }
            }
        }
    }
}
