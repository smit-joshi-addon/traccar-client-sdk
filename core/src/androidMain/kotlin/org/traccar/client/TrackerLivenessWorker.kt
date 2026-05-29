package org.traccar.client

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class TrackerLivenessWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {

    override fun doWork(): Result {
        if (!TrackerService.isRunning &&
            Tracker.shared(applicationContext).configStore.load() != null
        ) {
            TrackerService.start(applicationContext)
        }
        return Result.success()
    }
}
