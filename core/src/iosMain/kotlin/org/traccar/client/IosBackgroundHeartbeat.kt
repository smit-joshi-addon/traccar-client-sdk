package org.traccar.client

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import platform.BackgroundTasks.BGAppRefreshTask
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.dateByAddingTimeInterval
import platform.Foundation.timeIntervalSince1970

object IosBackgroundHeartbeat {

    internal const val TASK_IDENTIFIER = "org.traccar.client.heartbeat"

    private var registered = false
    private lateinit var queue: PositionQueue
    private lateinit var configProvider: suspend () -> Config?
    private lateinit var createUploader: (Config) -> Uploader

    fun register() {
        if (registered) return
        BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(
            identifier = TASK_IDENTIFIER,
            usingQueue = null,
        ) { task -> handleTask(task as BGAppRefreshTask) }
        registered = true
    }

    internal fun bind(
        queue: PositionQueue,
        configProvider: suspend () -> Config?,
        createUploader: (Config) -> Uploader,
    ) {
        this.queue = queue
        this.configProvider = configProvider
        this.createUploader = createUploader
    }

    internal fun schedule(intervalSeconds: Int) {
        if (intervalSeconds <= 0) return
        val request = BGAppRefreshTaskRequest(identifier = TASK_IDENTIFIER)
        request.earliestBeginDate = NSDate().dateByAddingTimeInterval(intervalSeconds.toDouble())
        BGTaskScheduler.sharedScheduler.submitTaskRequest(request, null)
        Log.log("Heartbeat scheduled in ${intervalSeconds}s")
    }

    internal fun cancel() {
        BGTaskScheduler.sharedScheduler.cancelTaskRequestWithIdentifier(TASK_IDENTIFIER)
    }

    private fun handleTask(task: BGAppRefreshTask) {
        Log.log("Heartbeat task fired")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        task.setExpirationHandler {
            scope.cancel()
            task.setTaskCompletedWithSuccess(false)
        }
        scope.launch {
            sharedTracker()
            val config = configProvider()!!
            schedule(config.location.heartbeatIntervalSeconds)
            val position = Position(
                time = (NSDate().timeIntervalSince1970 * 1000).toLong(),
                battery = readBattery(),
                charging = readCharging(),
            )
            val success = createUploader(config).upload(position)
            if (!success) queue.enqueue(position)
            task.setTaskCompletedWithSuccess(success)
        }
    }
}
