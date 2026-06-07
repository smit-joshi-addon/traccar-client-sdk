package org.traccar.client

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreLocation.CLCircularRegion
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.CLRegion
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.CoreLocation.kCLDistanceFilterNone
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.CoreLocation.kCLLocationAccuracyBestForNavigation
import platform.CoreLocation.kCLLocationAccuracyHundredMeters
import platform.CoreLocation.kCLLocationAccuracyKilometer
import platform.CoreMotion.CMMotionActivity
import platform.CoreMotion.CMMotionActivityManager
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSOperationQueue
import platform.Foundation.timeIntervalSince1970
import platform.darwin.NSObject

class IosLocationProvider(
    config: LocationConfig,
    private val stateStore: StateStore,
) : CallbackPositionProvider() {

    private val config: LocationConfig = config.effective

    private var locationManager: CLLocationManager? = null
    private var activityManager: CMMotionActivityManager? = null
    private var emit: ((Position) -> Unit)? = null
    private var lastLocation: CLLocation? = null
    private var stopTimeoutJob: Job? = null
    private var scope: CoroutineScope? = null
    private var paused = false
    private var pendingLocation: CompletableDeferred<CLLocation>? = null

    override suspend fun start(emit: (Position) -> Unit) = withContext(Dispatchers.Main) {
        this@IosLocationProvider.emit = emit
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val authStatus = CompletableDeferred<Boolean>()

        val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
            override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
                didUpdateLocations.forEach { value ->
                    val location = value as CLLocation
                    lastLocation = location
                    emit(location.toPosition(readBattery(), readCharging()))
                    pendingLocation?.complete(location)
                }
            }

            override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
            }

            override fun locationManager(manager: CLLocationManager, didExitRegion: CLRegion) {
                if (didExitRegion.identifier == STATIONARY_REGION_ID) {
                    exitStationary()
                }
            }

            override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
                if (authStatus.isCompleted) return
                when (manager.authorizationStatus) {
                    kCLAuthorizationStatusAuthorizedAlways,
                    kCLAuthorizationStatusAuthorizedWhenInUse -> authStatus.complete(true)
                    kCLAuthorizationStatusDenied,
                    kCLAuthorizationStatusRestricted -> authStatus.complete(false)
                }
            }
        }

        val manager = CLLocationManager().apply {
            this.delegate = delegate
            desiredAccuracy = config.accuracy.toIosAccuracy()
            distanceFilter = if (config.distanceMeters == 0) {
                kCLDistanceFilterNone
            } else {
                config.distanceMeters.toDouble()
            }
            allowsBackgroundLocationUpdates = true
        }

        val authorized = when (manager.authorizationStatus) {
            kCLAuthorizationStatusAuthorizedAlways,
            kCLAuthorizationStatusAuthorizedWhenInUse -> true
            kCLAuthorizationStatusDenied,
            kCLAuthorizationStatusRestricted -> false
            else -> {
                manager.requestAlwaysAuthorization()
                authStatus.await()
            }
        }

        if (!authorized) {
            throw IllegalStateException("Location permission denied")
        }

        manager.startMonitoringSignificantLocationChanges()
        locationManager = manager

        if (config.stopDetection && stateStore.state.value.paused) {
            Log.log("Restoring stationary state")
            paused = true
            IosBackgroundHeartbeat.schedule(config.heartbeatIntervalSeconds)
        } else {
            manager.startUpdatingLocation()
        }

        if (config.stopDetection) {
            startMotionMonitoring()
        }
    }

    override fun stop() {
        stopTimeoutJob?.cancel()
        stopTimeoutJob = null
        IosBackgroundHeartbeat.cancel()
        pendingLocation?.cancel()
        pendingLocation = null
        activityManager?.stopActivityUpdates()
        activityManager = null
        val manager = locationManager
        manager?.stopUpdatingLocation()
        manager?.stopMonitoringSignificantLocationChanges()
        manager?.monitoredRegions?.forEach { region ->
            (region as? CLRegion)?.let { manager.stopMonitoringForRegion(it) }
        }
        manager?.delegate = null
        locationManager = null
        scope?.cancel()
        scope = null
        emit = null
        lastLocation = null
        paused = false
    }

    private fun startMotionMonitoring() {
        if (!CMMotionActivityManager.isActivityAvailable()) {
            Log.log("Motion activity not available on this device")
            return
        }
        val activity = CMMotionActivityManager()
        activityManager = activity
        activity.startActivityUpdatesToQueue(NSOperationQueue.mainQueue) { motion ->
            if (motion == null) return@startActivityUpdatesToQueue
            Log.log("Motion update: ${motion.describe()}")
            when {
                motion.stationary -> onStationaryDetected()
                motion.walking || motion.running || motion.automotive || motion.cycling ->
                    onMovingDetected()
            }
        }
        Log.log("Motion updates subscribed")
    }

    private fun CMMotionActivity.describe(): String {
        val types = buildList {
            if (stationary) add("stationary")
            if (walking) add("walking")
            if (running) add("running")
            if (automotive) add("automotive")
            if (cycling) add("cycling")
            if (unknown) add("unknown")
        }
        val label = if (types.isEmpty()) "none" else types.joinToString("/")
        return "$label/confidence=$confidence"
    }

    private fun onStationaryDetected() {
        if (paused) return
        if (stopTimeoutJob?.isActive == true) return
        stopTimeoutJob = scope?.launch {
            delay(config.stopTimeoutSeconds.seconds)
            enterStationary()
        }
    }

    private fun onMovingDetected() {
        stopTimeoutJob?.cancel()
        stopTimeoutJob = null
        if (paused) {
            exitStationary()
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private suspend fun enterStationary() {
        val manager = locationManager ?: return
        val deferred = CompletableDeferred<CLLocation>()
        pendingLocation = deferred
        manager.requestLocation()
        val fresh = try {
            withTimeoutOrNull(10.seconds) { deferred.await() }
        } finally {
            pendingLocation = null
        }
        val location = fresh ?: lastLocation ?: return
        manager.stopUpdatingLocation()
        val (lat, lon) = location.coordinate.useContents { latitude to longitude }
        val region = CLCircularRegion(
            center = CLLocationCoordinate2DMake(lat, lon),
            radius = config.stationaryRadiusMeters.toDouble(),
            identifier = STATIONARY_REGION_ID,
        )
        region.notifyOnExit = true
        region.notifyOnEntry = false
        manager.startMonitoringForRegion(region)
        paused = true
        Log.log("Stationary, pausing location updates")
        IosBackgroundHeartbeat.schedule(config.heartbeatIntervalSeconds)
        stateStore.update { it.copy(paused = true) }
    }

    private fun exitStationary() {
        val manager = locationManager ?: return
        manager.monitoredRegions.forEach { region ->
            (region as? CLRegion)?.let { manager.stopMonitoringForRegion(it) }
        }
        IosBackgroundHeartbeat.cancel()
        paused = false
        manager.startUpdatingLocation()
        Log.log("Moving, resuming location updates")
        scope?.launch { stateStore.update { it.copy(paused = false) } }
    }

    private companion object {
        const val STATIONARY_REGION_ID = "traccar.stationary"
    }
}

private fun Accuracy.toIosAccuracy(): Double = when (this) {
    Accuracy.HIGHEST -> kCLLocationAccuracyBestForNavigation
    Accuracy.HIGH -> kCLLocationAccuracyBest
    Accuracy.MEDIUM -> kCLLocationAccuracyHundredMeters
    Accuracy.LOW -> kCLLocationAccuracyKilometer
}

@OptIn(ExperimentalForeignApi::class)
private fun CLLocation.toPosition(battery: Int?, charging: Boolean?): Position {
    val (latitude, longitude) = coordinate.useContents { latitude to longitude }
    val timestamp: NSDate = timestamp
    return Position(
        latitude = latitude,
        longitude = longitude,
        accuracy = horizontalAccuracy,
        time = (timestamp.timeIntervalSince1970 * 1000).toLong(),
        altitude = if (verticalAccuracy >= 0) altitude else null,
        speed = if (speed >= 0) speed else null,
        bearing = if (course >= 0) course else null,
        battery = battery,
        charging = charging,
    )
}
