package org.traccar.client

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.traccar.client.db.Database

class SqlDelightQueue(driver: SqlDriver) : PositionQueue {

    private val queries = Database(driver).positionQueries
    private val mutex = Mutex()

    override suspend fun enqueue(position: Position) {
        mutex.withLock {
            queries.enqueue(
                latitude = position.latitude,
                longitude = position.longitude,
                accuracy = position.accuracy.toDouble(),
                time = position.time,
            )
        }
    }

    override suspend fun peek(): Position? = mutex.withLock {
        queries.peek().executeAsOneOrNull()?.let { row ->
            Position(
                latitude = row.latitude,
                longitude = row.longitude,
                accuracy = row.accuracy.toFloat(),
                time = row.time,
            )
        }
    }

    override suspend fun removeFirst() {
        mutex.withLock {
            queries.removeFirst()
        }
    }
}
