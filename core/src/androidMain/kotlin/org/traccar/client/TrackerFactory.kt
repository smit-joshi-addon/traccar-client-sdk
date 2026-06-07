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
    val configStore = ConfigStore(driver)
    val queue = DatabaseQueue(driver)
    val networkMonitor = AndroidNetworkMonitor(appContext)
    val httpClient = HttpClient(Android)
    val createProvider: (LocationConfig) -> PositionProvider = {
        createLocationProvider(appContext, it, stateStore)
    }
    val createUploader: (Config) -> Uploader = { HttpUploader(it, httpClient) }
    val engine = TrackerEngine(
        configStore = configStore,
        stateStore = stateStore,
        queue = queue,
        network = networkMonitor,
        createProvider = createProvider,
        createUploader = createUploader,
    )
    Tracker(
        configStore = configStore,
        stateStore = stateStore,
        engine = engine,
        createProvider = createProvider,
        createUploader = createUploader,
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
