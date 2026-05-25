package org.traccar.client

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.traccar.client.db.Database

class DatabaseQueue(driver: SqlDriver) : PositionQueue {

    private val queries = Database(driver).positionQueries
    private val mutex = Mutex()

    override suspend fun enqueue(position: Position) {
        mutex.withLock {
            queries.enqueue(
                latitude = position.latitude,
                longitude = position.longitude,
                accuracy = position.accuracy,
                time = position.time,
                altitude = position.altitude,
                speed = position.speed,
                bearing = position.bearing,
                battery = position.battery?.toLong(),
            )
        }
    }

    override suspend fun peek(): Position? = mutex.withLock {
        queries.peek().executeAsOneOrNull()?.let { row ->
            Position(
                latitude = row.latitude,
                longitude = row.longitude,
                accuracy = row.accuracy,
                time = row.time,
                altitude = row.altitude,
                speed = row.speed,
                bearing = row.bearing,
                battery = row.battery?.toInt(),
            )
        }
    }

    override suspend fun removeFirst() {
        mutex.withLock {
            queries.removeFirst()
        }
    }
}
