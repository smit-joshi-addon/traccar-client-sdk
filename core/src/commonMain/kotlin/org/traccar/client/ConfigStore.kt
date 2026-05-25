package org.traccar.client

import app.cash.sqldelight.db.SqlDriver
import kotlinx.serialization.json.Json
import org.traccar.client.db.Database

class ConfigStore(driver: SqlDriver) {

    private val queries = Database(driver).configQueries

    fun save(config: Config) {
        queries.saveConfig(Json.encodeToString(config))
    }

    fun load(): Config? = queries.selectConfig().executeAsOneOrNull()?.let {
        Json.decodeFromString(it)
    }

    fun clear() {
        queries.clearConfig()
    }
}
