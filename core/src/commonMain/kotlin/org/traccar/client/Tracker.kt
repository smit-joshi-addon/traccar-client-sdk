package org.traccar.client

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class Tracker(
    private val provider: PositionProvider,
    private val uploader: Uploader,
    private val filter: PositionFilter = NoOpFilter,
    private val queue: PositionQueue = InMemoryQueue(),
) {
    fun start(scope: CoroutineScope): Job = scope.launch {
        provider.positions()
            .filter { filter.accept(it) }
            .onEach { queue.enqueue(it) }
            .collect { sync() }
    }

    private suspend fun sync() {
        while (true) {
            val pending = queue.peek() ?: return
            if (!uploader.upload(pending)) return
            queue.remove(pending)
        }
    }
}
