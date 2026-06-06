## 0.0.15

* Persist tracker state across process kills: stationary mode and the location filter's reference position now survive cold launch, so resume after an iOS region exit or Android `BootReceiver` restart no longer re-runs the 60s stop-detection timeout or emits a duplicate-feeling first fix.
* SDK errors propagate to Flutter as `PlatformException` instead of silently hanging the method-channel result (includes permission denial from `start`).
* `requestPosition` returns `Future<bool>` indicating whether the upload succeeded; previously fire-and-forget with no result.
* Internal: all store I/O is properly async (no sync DB on the main thread), tracker construction uses a suspend factory plus Android `ContentProvider` / iOS `+load` for context capture, and runtime state has a single source of truth backed by `StateStore`.

## 0.0.14

* Drop the activity-recognition snapshot subscription added in 0.0.10. The transition subscription itself delivers the current state as an `ENTER` event on registration (verified on Android, assumed by symmetry on iOS), so the separate snapshot path is redundant.
* Remove `ON_FOOT` from the Android activity transition request as a precaution; the remaining types (`STILL`, `IN_VEHICLE`, `ON_BICYCLE`, `RUNNING`, `WALKING`) cover all real movement cases.
* Log every activity transition with human-readable names on Android, and every motion update on iOS, to aid stop-detection diagnostics.

## 0.0.13

* Fix Android stop-detection silently never engaging: the activity recognition broadcast receiver was registered with `RECEIVER_NOT_EXPORTED`, which blocks PendingIntent deliveries originated by Google Play Services. Switched to `RECEIVER_EXPORTED`.
* Register activity transitions for all supported types (STILL, IN_VEHICLE, ON_BICYCLE, ON_FOOT, RUNNING, WALKING) so any movement transition is observable, not just STILL enter/exit.
* Log activity recognition request results and incoming events on both Android and iOS to aid stop-detection debugging.

## 0.0.12

* Send position uploads as `POST` with form-encoded body instead of `GET` query string.

## 0.0.11

* Fix iOS build broken in 0.0.10 by an incorrect `NSDate` constructor used for the motion history query window.
* Remove `TrackerLivenessWorker` — Android 12+ restrictions made it silently fail to restart the foreground service; recovery now relies on `START_REDELIVER_INTENT` and `BootReceiver`.

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
