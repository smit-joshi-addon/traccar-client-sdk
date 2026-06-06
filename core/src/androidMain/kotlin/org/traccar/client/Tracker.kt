package org.traccar.client

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.edit
import androidx.core.content.getSystemService
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import org.traccar.client.db.Database

class Tracker private constructor(context: Context) {

    internal val configStore: ConfigStore
    internal val stateStore: StateStore
    internal val queue: DatabaseQueue

    val isTracking: Boolean get() = stateStore.load().enabled

    init {
        val driver = AndroidSqliteDriver(Database.Schema, context.applicationContext, "tracker.db")
        configStore = ConfigStore(driver)
        stateStore = StateStore(driver)
        queue = DatabaseQueue(driver)
        Log.store = LogStore(driver)
    }

    suspend fun start(context: Context, config: Config): Boolean {
        Log.log("Tracker start ${config.serverUrl} ${config.deviceId}")
        TrackerService.ensureNotificationChannel(context)

        if (!ensurePermissions(context)) {
            Log.log("Permissions denied")
            return false
        }

        val powerManager = context.getSystemService<PowerManager>()
        if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
            val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            if (!preferences.getBoolean(BATTERY_PROMPTED_KEY, false)) {
                context.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                preferences.edit { putBoolean(BATTERY_PROMPTED_KEY, true) }
            }
        }

        configStore.save(config)
        stateStore.save(stateStore.load().copy(enabled = true))
        TrackerService.start(context)
        return true
    }

    fun stop(context: Context) {
        Log.log("Tracker stop")
        stateStore.save(stateStore.load().copy(enabled = false))
        TrackerService.stop(context)
    }

    fun requestPosition(context: Context, config: Config) {
        TrackerEngine.oneShotUpload(
            provider = createLocationProvider(context, config.location.copy(stopDetection = false)),
            config = config,
            httpClient = HttpClient(Android),
        )
    }

    fun getLogs(): List<LogEntry> = Log.store?.all() ?: emptyList()

    fun clearLogs() {
        Log.store?.clear()
    }

    companion object {
        private const val PREFERENCES_NAME = "traccar-client-sdk"
        private const val BATTERY_PROMPTED_KEY = "battery-prompted"

        @Volatile
        private var instance: Tracker? = null

        fun shared(context: Context): Tracker =
            instance ?: synchronized(this) {
                instance ?: Tracker(context).also { instance = it }
            }
    }
}

internal fun createLocationProvider(context: Context, config: LocationConfig): PositionProvider {
    val useFused = GoogleApiAvailability.getInstance()
        .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    Log.log("Using ${if (useFused) "FusedLocationProvider" else "AndroidLocationProvider"}")
    return if (useFused) {
        FusedLocationProvider(context, config)
    } else {
        AndroidLocationProvider(context, config)
    }
}
