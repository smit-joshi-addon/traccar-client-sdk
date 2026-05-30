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
