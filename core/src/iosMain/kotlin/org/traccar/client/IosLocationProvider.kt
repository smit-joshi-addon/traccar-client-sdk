package org.traccar.client

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.timeIntervalSince1970
import platform.darwin.NSObject

class IosLocationProvider(
    private val distanceFilterMeters: Double = 10.0,
) : CallbackPositionProvider() {

    private var locationManager: CLLocationManager? = null

    override suspend fun start(emit: (Position) -> Unit) = withContext(Dispatchers.Main) {
        val authStatus = CompletableDeferred<Boolean>()

        val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
            override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
                didUpdateLocations.forEach { value ->
                    emit((value as CLLocation).toPosition())
                }
            }

            override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
                // ignored for v0
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
            desiredAccuracy = kCLLocationAccuracyBest
            distanceFilter = distanceFilterMeters
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
        locationManager = manager
    }

    override fun stop() {
        locationManager?.stopUpdatingLocation()
        locationManager?.delegate = null
        locationManager = null
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun CLLocation.toPosition(): Position {
    val (latitude, longitude) = coordinate.useContents { latitude to longitude }
    val timestamp: NSDate = timestamp
    return Position(
        latitude = latitude,
        longitude = longitude,
        accuracy = horizontalAccuracy.toFloat(),
        time = (timestamp.timeIntervalSince1970 * 1000).toLong(),
    )
}
