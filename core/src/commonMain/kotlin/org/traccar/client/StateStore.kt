package org.traccar.client

import app.cash.sqldelight.db.SqlDriver
import kotlinx.serialization.json.Json
import org.traccar.client.db.Database

class StateStore(driver: SqlDriver) {

    private val queries = Database(driver).stateQueries

    fun save(state: State) {
        queries.saveState(Json.encodeToString(state))
    }

    fun load(): State = queries.selectState().executeAsOneOrNull()?.let {
        Json.decodeFromString(it)
    } ?: State()

    fun clear() {
        queries.clearState()
    }
}
