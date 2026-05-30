package org.traccar.traccar_client_sdk

import android.content.Context
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.traccar.client.Accuracy
import org.traccar.client.Config
import org.traccar.client.LocationConfig
import org.traccar.client.NotificationConfig
import org.traccar.client.Tracker

class TraccarClientSdkPlugin :
    FlutterPlugin,
    MethodCallHandler {

    private lateinit var channel: MethodChannel
    private lateinit var context: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext
        channel = MethodChannel(binding.binaryMessenger, "traccar_client_sdk")
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "start" -> {
                val config = parseConfig(call.arguments as Map<*, *>)
                scope.launch {
                    result.success(Tracker.shared(context).start(context, config))
                }
            }
            "stop" -> {
                Tracker.shared(context).stop(context)
                result.success(null)
            }
            "isTracking" -> {
                result.success(Tracker.shared(context).isTracking)
            }
            "getLogs" -> {
                val entries = Tracker.shared(context).getLogs().map {
                    mapOf("time" to it.time, "message" to it.message)
                }
                result.success(entries)
            }
            "clearLogs" -> {
                Tracker.shared(context).clearLogs()
                result.success(null)
            }
            else -> result.notImplemented()
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        scope.cancel()
    }

    private fun parseConfig(args: Map<*, *>): Config {
        val location = args["location"] as Map<*, *>
        val notification = args["notification"] as Map<*, *>
        return Config(
            serverUrl = args["serverUrl"] as String,
            deviceId = args["deviceId"] as String,
            location = LocationConfig(
                accuracy = Accuracy.valueOf(location["accuracy"] as String),
                distanceMeters = (location["distanceMeters"] as Number).toInt(),
                intervalSeconds = (location["intervalSeconds"] as Number).toInt(),
                angleDegrees = (location["angleDegrees"] as Number).toInt(),
                stopDetection = location["stopDetection"] as Boolean,
                stopTimeoutSeconds = (location["stopTimeoutSeconds"] as Number).toInt(),
                stationaryRadiusMeters = (location["stationaryRadiusMeters"] as Number).toInt(),
            ),
            wakeLock = args["wakeLock"] as Boolean,
            notification = NotificationConfig(text = notification["text"] as String),
        )
    }
}
