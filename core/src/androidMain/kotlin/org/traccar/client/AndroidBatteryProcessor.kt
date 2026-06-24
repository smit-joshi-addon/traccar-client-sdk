package org.traccar.client

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

class AndroidBatteryProcessor(private val context: Context) : PositionProcessor {

    private val batteryManager: BatteryManager? by lazy {
        context.applicationContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    }

    override suspend fun process(position: Position): Position {
        val level = position.battery ?: getBatteryLevel()
        val charging = position.charging ?: getChargingStatus()

        return position.copy(
            battery = level,
            charging = charging,
        )
    }

    private fun getBatteryLevel(): Int? {
        // Try BatteryManager first
        val capacity = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (capacity != null && capacity in 0..100) {
            return capacity
        }

        // Fallback to sticky intent
        return try {
            val batteryIntent = context.applicationContext.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            batteryIntent?.let { intent ->
                val rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (rawLevel >= 0 && scale > 0) {
                    (rawLevel * 100) / scale
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getChargingStatus(): Boolean {
        // Try BatteryManager first
        val isCharging = batteryManager?.isCharging
        if (isCharging != null) {
            return isCharging
        }

        // Fallback to sticky intent
        return try {
            val batteryIntent = context.applicationContext.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            batteryIntent?.let { intent ->
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL ||
                        plugged == BatteryManager.BATTERY_PLUGGED_AC ||
                        plugged == BatteryManager.BATTERY_PLUGGED_USB ||
                        plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS
            } ?: false
        } catch (e: Exception) {
            false
        }
    }
}
