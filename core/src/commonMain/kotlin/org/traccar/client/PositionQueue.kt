package org.traccar.client

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface PositionQueue {
    suspend fun enqueue(position: Position)
    suspend fun peek(): Position?
    suspend fun remove(position: Position)
}

class InMemoryQueue : PositionQueue {

    private val mutex = Mutex()
    private val items = ArrayDeque<Position>()

    override suspend fun enqueue(position: Position) = mutex.withLock {
        items.addLast(position)
    }

    override suspend fun peek(): Position? = mutex.withLock {
        items.firstOrNull()
    }

    override suspend fun remove(position: Position) = mutex.withLock {
        items.remove(position)
        Unit
    }
}
