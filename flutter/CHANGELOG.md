## 0.0.10

* Detect already-stationary state at start (Android `requestActivityUpdates` snapshot, iOS `queryActivityStarting`) so stop-detection engages even when the user hasn't transitioned since tracking began.
* Use a single OS-level filter on Android: when `distanceMeters > 0`, request by distance only; otherwise request by time. Avoids the AND deadlock that left stationary users with no updates.

## 0.0.9

* Request an immediate first fix on Android via `getCurrentLocation`, avoiding a multi-minute silent period after `start` with large `intervalSeconds` or balanced-power accuracy.

## 0.0.8

* Add `requestPosition(Config)` for a one-off fix and upload, independent of `start`/`stop`. Disables stop-detection on the provider and applies a 30s timeout.

## 0.0.7

* Add `Config.buffer` (default `true`). When `false`, positions upload directly without queue or retry (real-time only).

## 0.0.6

* Add `isTracking()` to query current tracking state.

## 0.0.5

* No functional changes; ships pub.dev publishing pipeline fix.

## 0.0.3

* Expose full `Config` (`LocationConfig`, `NotificationConfig`, `Accuracy`).
* Bridge `clearLogs()`.
* `getLogs()` now returns `List<LogEntry>` with structured `time` and `message` fields.
* Breaking change vs 0.0.2: `start({serverUrl, deviceId})` is now `start(Config)`; `getLogs()` return type changed from `List<String>`.

## 0.0.2

* Initial plugin scaffold. `start`, `stop`, and `getLogs` for background location tracking on Android and iOS.
