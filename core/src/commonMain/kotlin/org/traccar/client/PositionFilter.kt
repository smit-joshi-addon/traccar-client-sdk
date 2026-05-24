package org.traccar.client

fun interface PositionFilter {
    fun accept(position: Position): Boolean
}

object NoOpFilter : PositionFilter {
    override fun accept(position: Position): Boolean = true
}
