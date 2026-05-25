package org.traccar.client

data class Position(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double,
    val time: Long,
    val altitude: Double? = null,
    val speed: Double? = null,
    val bearing: Double? = null,
    val battery: Int? = null,
)
