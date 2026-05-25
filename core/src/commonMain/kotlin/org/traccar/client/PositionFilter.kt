package org.traccar.client

fun interface PositionFilter {
    fun accept(position: Position): Boolean
}
