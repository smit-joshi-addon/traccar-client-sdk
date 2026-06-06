package org.traccar.client

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.traccar.client.db.Database

internal actual suspend fun createTracker(): Tracker = withContext(Dispatchers.IO) {
    val appContext = applicationContext
    val driver = AndroidSqliteDriver(Database.Schema, appContext, "tracker.db")
    Log.store = LogStore(driver)
    val stateStore = StateStore.create(driver)
    val httpClient = HttpClient(Android)
    val engineBuilder = EngineBuilder(
        queue = DatabaseQueue(driver),
        stateStore = stateStore,
        networkMonitor = AndroidNetworkMonitor(appContext),
        createProvider = { createLocationProvider(appContext, it, stateStore) },
        createUploader = { HttpUploader(it, httpClient) },
    )
    Tracker(
        configStore = ConfigStore(driver),
        stateStore = stateStore,
        engineBuilder = engineBuilder,
        onStarted = { TrackerService.start(appContext) },
        onStopped = { TrackerService.stop() },
    )
}

internal fun createLocationProvider(
    context: Context,
    config: LocationConfig,
    stateStore: StateStore,
): PositionProvider {
    val useFused = GoogleApiAvailability.getInstance()
        .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    Log.log("Using ${if (useFused) "FusedLocationProvider" else "AndroidLocationProvider"}")
    return if (useFused) {
        FusedLocationProvider(context, config, stateStore)
    } else {
        AndroidLocationProvider(context, config)
    }
}
