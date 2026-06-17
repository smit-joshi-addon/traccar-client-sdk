package org.traccar.client

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive

class InProcessHeartbeatTrigger(
    scope: ComponentCoroutineScope,
    config: Config,
    state: StateFlow<State>,
) : SignalSource {

    private val interval = config.location.heartbeatIntervalSeconds

    override val signals = MutableSharedFlow<Signal>(extraBufferCapacity = 8)

    init {
        scope.observeActive(state, { it.enabled && it.paused }) { active ->
            if (active) runHeartbeats()
        }
    }

    private suspend fun runHeartbeats() {
        if (interval <= 0) return
        while (currentCoroutineContext().isActive) {
            delay(interval.seconds)
            signals.emit(Signal.HeartbeatTick)
        }
    }
}
