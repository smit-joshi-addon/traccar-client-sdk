package org.traccar.client

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

interface LocationSource {
    val positions: Flow<Position>
    suspend fun fetchOnce(): Position?
}

interface SignalSource {
    val signals: Flow<Signal>
}

fun interface PositionProcessor {
    suspend fun process(position: Position): Position?
}

class ComponentCoroutineScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default)

internal fun CoroutineScope.observeActive(
    state: StateFlow<State>,
    predicate: (State) -> Boolean,
    block: suspend (Boolean) -> Unit,
): Job = launch {
    state
        .map(predicate)
        .distinctUntilChanged()
        .collectLatest(block)
}
