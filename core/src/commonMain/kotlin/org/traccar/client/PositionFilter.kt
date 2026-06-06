package org.traccar.client

fun interface PositionFilter {
    suspend fun accept(position: Position): Boolean
}
