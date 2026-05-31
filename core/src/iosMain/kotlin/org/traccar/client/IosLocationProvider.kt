package org.traccar.client

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import platform.UIKit.UIDevice
import platform.darwin.NSObject

class IosLocationProvider(
    config: LocationConfig,
) : CallbackPositionProvider() {

    private val config: LocationConfig = config.effective

    private var locationManager: CLLocationManager? = null
    private var activityManager: CMMotionActivityManager? = null
    private var emit: ((Position) -> Unit)? = null
    private var lastLocation: CLLocation? = null
    private var stopTimeoutJob: Job? = null
    private var scope: CoroutineScope? = null
    private var paused = false

    init {
        UIDevice.currentDevice.batteryMonitoringEnabled = true
    }

    override suspend fun start(emit: (Position) -> Unit) = withContext(Dispatchers.Main) {
        this@IosLocationProvider.emit = emit
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val authStatus = CompletableDeferred<Boolean>()

        val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
            override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
                didUpdateLocations.forEach { value ->
                    val location = value as CLLocation
                    lastLocation = location
                    emit(location.toPosition(readBattery()))
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

        manager.startUpdatingLocation()
        manager.startMonitoringSignificantLocationChanges()
        locationManager = manager

        if (config.stopDetection) {
            startMotionMonitoring()
        }
    }

    override fun stop() {
        stopTimeoutJob?.cancel()
        stopTimeoutJob = null
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
        val activity = CMMotionActivityManager()
        activityManager = activity
        val now = NSDate()
        val recent = NSDate.dateWithTimeIntervalSinceNow(-MOTION_QUERY_WINDOW_SECONDS)
        activity.queryActivityStartingFromDate(recent, now, NSOperationQueue.mainQueue) { activities, _ ->
            val current = activities?.lastOrNull() as? CMMotionActivity
                ?: return@queryActivityStartingFromDate
            if (current.stationary) {
                onStationaryDetected()
            }
        }
        activity.startActivityUpdatesToQueue(NSOperationQueue.mainQueue) { motion ->
            if (motion == null) return@startActivityUpdatesToQueue
            when {
                motion.stationary -> onStationaryDetected()
                motion.walking || motion.running || motion.automotive || motion.cycling ->
                    onMovingDetected()
            }
        }
    }

    private fun onStationaryDetected() {
        if (paused) return
        if (stopTimeoutJob?.isActive == true) return
        stopTimeoutJob = scope?.launch {
            delay(config.stopTimeoutSeconds * 1000L)
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
    private fun enterStationary() {
        val location = lastLocation ?: return
        val manager = locationManager ?: return
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
    }

    private fun exitStationary() {
        val manager = locationManager ?: return
        manager.monitoredRegions.forEach { region ->
            (region as? CLRegion)?.let { manager.stopMonitoringForRegion(it) }
        }
        paused = false
        manager.startUpdatingLocation()
        Log.log("Moving, resuming location updates")
    }

    private fun readBattery(): Int? {
        val level = UIDevice.currentDevice.batteryLevel
        return if (level >= 0f) (level * 100).toInt() else null
    }

    private companion object {
        const val STATIONARY_REGION_ID = "traccar.stationary"
        const val MOTION_QUERY_WINDOW_SECONDS = 24 * 60 * 60.0
    }
}

private fun Accuracy.toIosAccuracy(): Double = when (this) {
    Accuracy.HIGHEST -> kCLLocationAccuracyBestForNavigation
    Accuracy.HIGH -> kCLLocationAccuracyBest
    Accuracy.MEDIUM -> kCLLocationAccuracyHundredMeters
    Accuracy.LOW -> kCLLocationAccuracyKilometer
}

@OptIn(ExperimentalForeignApi::class)
private fun CLLocation.toPosition(battery: Int?): Position {
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
    )
}
