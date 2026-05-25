package org.traccar.client

import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val serverUrl: String,
    val deviceId: String,
    val location: LocationConfig = LocationConfig(),
)

@Serializable
data class LocationConfig(
    val accuracy: Accuracy = Accuracy.MEDIUM,
    val distanceMeters: Int = 75,
    val intervalSeconds: Int = 300,
    val stopDetection: Boolean = true,
    val stopTimeoutSeconds: Int = 60,
    val stationaryRadiusMeters: Int = 100,
)

@Serializable
enum class Accuracy {
    HIGHEST,
    HIGH,
    MEDIUM,
    LOW,
}

val LocationConfig.effective: LocationConfig
    get() = if (accuracy == Accuracy.HIGHEST) {
        copy(distanceMeters = 0, intervalSeconds = 0, stopDetection = false)
    } else {
        this
    }
