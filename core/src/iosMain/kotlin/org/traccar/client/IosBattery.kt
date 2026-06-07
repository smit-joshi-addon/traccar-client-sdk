package org.traccar.client

import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceBatteryState

internal fun enableBatteryMonitoring() {
    UIDevice.currentDevice.batteryMonitoringEnabled = true
}

internal fun readBattery(): Int? {
    val level = UIDevice.currentDevice.batteryLevel
    return if (level >= 0f) (level * 100).toInt() else null
}

internal fun readCharging(): Boolean? = when (UIDevice.currentDevice.batteryState) {
    UIDeviceBatteryState.UIDeviceBatteryStateCharging,
    UIDeviceBatteryState.UIDeviceBatteryStateFull -> true
    UIDeviceBatteryState.UIDeviceBatteryStateUnplugged -> false
    else -> null
}
