package org.traccar.client

import app.cash.sqldelight.db.SqlDriver
import org.traccar.client.db.Database

class LogStore(driver: SqlDriver) {

    private val queries = Database(driver).logEntryQueries

    fun insert(message: String) {
        queries.insert(time = nowMillis(), message = message)
    }

    fun all(): List<LogEntry> = queries.selectAll().executeAsList().map {
        LogEntry(time = it.time, message = it.message)
    }

    fun clear() {
        queries.clear()
    }

    fun trim(keep: Int) {
        queries.trim(keep.toLong())
    }
}
