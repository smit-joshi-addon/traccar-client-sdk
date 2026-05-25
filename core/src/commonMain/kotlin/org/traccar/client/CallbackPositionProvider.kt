package org.traccar.client

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

abstract class CallbackPositionProvider : PositionProvider {

    final override fun positions(): Flow<Position> = callbackFlow {
        val emit: (Position) -> Unit = { trySend(it) }
        try {
            start(emit)
        } catch (e: Throwable) {
            close(e)
            return@callbackFlow
        }
        awaitClose { stop() }
    }

    protected abstract suspend fun start(emit: (Position) -> Unit)
    protected abstract fun stop()
}
