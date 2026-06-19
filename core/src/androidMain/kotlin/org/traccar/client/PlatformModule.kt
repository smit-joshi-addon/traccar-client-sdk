package org.traccar.client

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import org.traccar.client.db.Database

internal actual fun platformModule(): Module = module {
    single<Context> { applicationContext }
    single<SqlDriver> { AndroidSqliteDriver(Database.Schema, get(), "tracker.db") }
    single { HttpClient(Android) }
    single<NetworkMonitor> { AndroidNetworkMonitor(get()) }
    single<PositionProcessor> { AndroidBatteryProcessor(get()) }

    single<LocationSource> {
        val context = get<Context>()
        val playServicesAvailable = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        Log.log("Using ${if (playServicesAvailable) "Fused" else "Android"}LocationSource")
        if (playServicesAvailable) {
            FusedLocationSource(get(), get(), get(), get())
        } else {
            AndroidLocationSource(get(), get(), get(), get())
        }
    }

    single { ActivityRecognitionDetector(get(), get(), get(), get()) } bind SignalSource::class
    single { GeofenceDetector(get(), get(), get(), get(), get()) } bind SignalSource::class
    single { WakeLockHolder(get(), get(), get(), get()) } bind SignalSource::class
    single { AlarmHeartbeatTrigger(get(), get(), get(), get()) } bind SignalSource::class
    single { ForegroundServiceHolder(get(), get(), get()) } bind SignalSource::class
}
