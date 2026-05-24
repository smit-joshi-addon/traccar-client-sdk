package org.traccar.client

data class Position(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val time: Long,
)
