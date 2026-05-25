package org.traccar.client

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class TrackerEngine(
    private val provider: PositionProvider,
    private val uploader: Uploader,
    private val queue: PositionQueue,
    private val filter: PositionFilter = NoOpFilter,
) {
    private var scope: CoroutineScope? = null

    fun start() {
        if (scope != null) return
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = newScope
        newScope.launch {
            provider.positions()
                .filter { filter.accept(it) }
                .onEach { queue.enqueue(it) }
                .collect { sync() }
        }
    }

    fun stop() {
        scope?.cancel()
        scope = null
    }

    private suspend fun sync() {
        while (true) {
            val pending = queue.peek() ?: return
            if (!uploader.upload(pending)) return
            queue.removeFirst()
        }
    }
}
