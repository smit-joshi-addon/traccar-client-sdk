package org.traccar.client

import kotlinx.cinterop.ExperimentalForeignApi
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

object IosBackgroundHeartbeat {

    internal const val TASK_IDENTIFIER = "org.traccar.client.heartbeat"

    private var registered = false

    fun register() {
        if (registered) return
        BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(
            identifier = TASK_IDENTIFIER,
            usingQueue = null,
        ) { task -> handleTask(task as BGAppRefreshTask) }
        registered = true
    }

    @OptIn(ExperimentalForeignApi::class)
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
            val tracker = sharedTracker() ?: run {
                task.setTaskCompletedWithSuccess(false)
                return@launch
            }
            schedule(tracker.config.location.heartbeatIntervalSeconds)
            tracker.engine.handle(Signal.HeartbeatTick)
            task.setTaskCompletedWithSuccess(true)
        }
    }
}
