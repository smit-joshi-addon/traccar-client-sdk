package org.traccar.client

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import org.traccar.client.db.Database

internal val sharedDriver: SqlDriver by lazy {
    NativeSqliteDriver(Database.Schema, "tracker.db")
}
