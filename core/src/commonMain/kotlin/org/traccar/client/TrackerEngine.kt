package org.traccar.client

import io.ktor.client.HttpClient
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
    private val initialBackoffMs: Long = 5_000,
    private val maxBackoffMs: Long = 5 * 60_000,
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
        var backoff = initialBackoffMs
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
                    backoff = initialBackoffMs
                }
                else -> {
                    Log.log("Upload failed, retrying in ${backoff}ms")
                    delay(backoff)
                    backoff = (backoff * 2).coerceAtMost(maxBackoffMs)
                }
            }
        }
    }

    companion object {
        fun oneShotUpload(
            provider: PositionProvider,
            config: Config,
            httpClient: HttpClient,
        ) {
            Log.log("Request position ${config.serverUrl} ${config.deviceId}")
            val uploader = HttpUploader(config, httpClient)
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                try {
                    val position = withTimeoutOrNull(30_000) {
                        provider.positions().first()
                    }
                    if (position != null) {
                        uploader.upload(position)
                    } else {
                        Log.log("Request position timed out")
                    }
                } catch (e: Throwable) {
                    Log.log("Request position failed: ${e.message}")
                } finally {
                    httpClient.close()
                }
            }
        }
    }
}
