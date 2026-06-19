package org.traccar.client

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import org.traccar.client.db.Database

internal actual fun platformModule(): Module = module {
    single<SqlDriver> { NativeSqliteDriver(Database.Schema, "tracker.db") }
    single { HttpClient(Darwin) }
    single<NetworkMonitor> { IosNetworkMonitor() }
    single<PositionProcessor> { IosBatteryProcessor() }

    single<LocationSource> { IosLocationSource(get(), get(), get()) }

    single { MotionActivityDetector(get(), get(), get()) } bind SignalSource::class
    single { RegionDetector(get(), get(), get(), get()) } bind SignalSource::class
    single { IosBackgroundHeartbeat(get(), get(), get()) } bind SignalSource::class
}
