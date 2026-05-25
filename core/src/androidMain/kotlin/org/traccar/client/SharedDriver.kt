package org.traccar.client

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import org.traccar.client.db.Database

private var instance: SqlDriver? = null

@Synchronized
internal fun sharedDriver(context: Context): SqlDriver =
    instance ?: AndroidSqliteDriver(Database.Schema, context.applicationContext, "tracker.db")
        .also { instance = it }
