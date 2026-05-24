package org.traccar.client

import kotlinx.coroutines.flow.Flow

interface PositionProvider {
    fun positions(): Flow<Position>
}
