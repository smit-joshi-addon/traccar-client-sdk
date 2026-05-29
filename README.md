# Traccar Client SDK

A Kotlin Multiplatform location tracking SDK for [Traccar](https://www.traccar.org). It runs background
location tracking on Android and iOS, persists positions in a local SQLite queue, and uploads them to a
Traccar server with network-aware retry.

## Features

- Background tracking with stop detection (motion-aware pause/resume)
- Distance, time, and angle filtering
- Offline SQLite queue with exponential-backoff upload retry
- Android reliability: foreground service, `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` restart, optional
  wakelock, WorkManager liveness watchdog
- iOS reliability: significant-location-change wake-up that resumes tracking after the app is killed,
  with no consumer `AppDelegate` code required
- DB-backed diagnostic log accessible to the host app

## Installation

### Android (Gradle)

```kotlin
dependencies {
    implementation("org.traccar:traccar-client-sdk:0.0.1")
}
```

### iOS (Swift Package Manager)

```swift
dependencies: [
    .package(url: "https://github.com/traccar/traccar-client-sdk.git", from: "0.0.1")
]
```

## Usage

### Android

`Tracker.shared(context)` returns the process-wide tracker. `start` requires a `ComponentActivity`
because it requests location permissions.

```kotlin
val config = Config(serverUrl = "https://demo.traccar.org", deviceId = "123456")
Tracker.shared(activity).start(activity, config) // suspend; returns false if permissions denied
Tracker.shared(context).stop(context)
```

### iOS

```swift
import TraccarClientSDK

Tracker.shared.start(config: Config(serverUrl: "https://demo.traccar.org", deviceId: "123456"))
Tracker.shared.stop()
```

## Configuration

```kotlin
Config(
    serverUrl = "https://demo.traccar.org",
    deviceId = "123456",
    location = LocationConfig(
        accuracy = Accuracy.MEDIUM,          // HIGHEST, HIGH, MEDIUM, LOW
        distanceMeters = 75,
        intervalSeconds = 300,
        angleDegrees = 0,                    // 0 disables angle trigger
        stopDetection = true,
        stopTimeoutSeconds = 60,
        stationaryRadiusMeters = 100,
    ),
    wakeLock = false,                        // Android only
    notification = NotificationConfig(text = "Location tracking"), // Android only
)
```

## Logs

Recent diagnostic events are persisted and can be read by the host app:

```kotlin
Tracker.shared(context).getLogs()  // Android
Tracker.shared.getLogs()           // iOS
```

## License

Apache License 2.0. See [LICENSE](LICENSE).
