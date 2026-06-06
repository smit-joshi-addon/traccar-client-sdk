package org.traccar.client

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import org.traccar.client.db.Database

class StateStore(driver: SqlDriver) {

    private val queries = Database(driver).stateQueries
    private val _state = MutableStateFlow(loadFromDb())
    val state: StateFlow<State> = _state.asStateFlow()

    fun update(transform: (State) -> State) {
        val next = transform(_state.value)
        _state.value = next
        queries.saveState(Json.encodeToString(next))
    }

    private fun loadFromDb(): State = queries.selectState().executeAsOneOrNull()
        ?.let { Json.decodeFromString<State>(it) } ?: State()
}
