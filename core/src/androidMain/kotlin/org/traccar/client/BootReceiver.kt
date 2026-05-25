package org.traccar.client

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (ConfigStore(sharedDriver(context)).load() != null) {
            TrackerService.start(context)
        }
    }
}
