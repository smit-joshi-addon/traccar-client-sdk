# Architecture

## Principle

The engine owns all SDK behavior. State is the persisted truth. Every wake-up — user action, live observer, OS-mediated persistent trigger — flows into the engine as a `Signal`. Nothing outside the engine has its own copy of heartbeat-upload, stationary-transition, or geofence-registration logic.

## Signal vocabulary

```
Signal.Restore            // make runtime match state, no transition intent
Signal.StationaryEnter    // observer (live or persistent) says: now still
Signal.StationaryExit     // observer (live or persistent) says: now moving
Signal.HeartbeatTick      // periodic work; do not change mode
```

There is no `UserStart` / `UserStop`. Lifecycle calls on the public `Tracker` facade write state and then send `Restore`.

## Engine modes

The engine has one entry: `suspend fun handle(signal: Signal)`. It does not have a separate "start" or "set initial mode" phase. The signal is the work to do; state is the context.

Internally the engine operates in one of two ways depending on the caller:

- **Long-running.** Engine alive in a foreground service (Android) or a foregrounded / background-launched session (iOS). Live observers feed signals directly.
- **Brief revival.** Engine constructed inside a static `BroadcastReceiver` (Android) or `BGAppRefreshTask` / location-event launch (iOS) to process one signal, then torn down with the process. Same engine code; just doesn't start observers it doesn't need for the signal.

The engine does not assume it is long-running. Every wake-up may be brief.

## What each signal does

| Signal | enabled=false | enabled=true, paused=true | enabled=true, paused=false |
|---|---|---|---|
| `Restore` | shut everything down, no-op | enter paused mode: register geofence + activity-transition + heartbeat trigger (idempotent) | enter active mode: start FGS (Android) / start updating location (iOS), register live observers |
| `StationaryEnter` | drop | drop (already paused) | schedule stop-timeout; when it fires, write `paused=true` and transition to paused mode |
| `StationaryExit` | drop, attempt cleanup of stale registration that delivered this | write `paused=false`, tear down paused-mode triggers, transition to active mode (Android: attempt FGS start — may fail on 12+, that's OK) | drop (already moving) |
| `HeartbeatTick` | drop | upload heartbeat position via existing pipeline, reschedule next tick | drop (active mode has its own location flow) |

`Restore`'s job in paused mode includes re-registering OS-level triggers because they can drift (Play Services updated, alarms dropped by battery saver, etc.). Idempotent — safe to call repeatedly.

## Persistent triggers

State-change triggers — survive process death, OS dispatches:

- Android: `addGeofences(pendingIntent → StationaryReceiver::class)`, `requestActivityTransitionUpdates(pendingIntent → StationaryReceiver::class)`
- iOS: `CLCircularRegion` monitoring, significant location changes (both via `CLLocationManagerDelegate`)

Periodic-work triggers — survive process death, OS-throttled:

- Android: `AlarmManager` exact/inexact alarm → static receiver
- iOS: `BGAppRefreshTask` → handler registered with `BGTaskScheduler`

All of them, when fired, do the same three things and nothing else:

1. Read `state.enabled`. If false, exit. (Stale registration; harmless to ignore.)
2. Ensure tracker is constructed (`sharedTracker()` is single-shot via `Deferred`).
3. Call `engine.handle(signal)`.

No business logic in receivers. No state writes. No scheduling. No FGS starts. The engine does all of it.

## Cold-start coverage

### Android

| Wake source | Signal |
|---|---|
| Host app launches and calls into SDK | `Restore` |
| `BootReceiver` (`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`) | `Restore` |
| Geofence broadcast | `StationaryExit` |
| Activity-transition broadcast (STILL/ENTER or STILL/EXIT) | `StationaryEnter` or `StationaryExit` |
| `AlarmManager` heartbeat broadcast | `HeartbeatTick` |
| `Tracker.start(config)` | writes state, then `Restore` |
| `Tracker.stop()` | writes state, then `Restore` |

`TrackerService` uses `START_NOT_STICKY`. There is no Android-managed auto-restart competing with our persistent triggers; persistent triggers are the single recovery mechanism.

### iOS

| Wake source | Signal |
|---|---|
| Host app launches | `Restore` |
| App relaunched via `UIApplicationLaunchOptionsLocationKey`, then `CLLocationManagerDelegate` fires `didExitRegion` | `StationaryExit` |
| App relaunched via SLC, then `didUpdateLocations` fires | `StationaryExit` (SLC implies meaningful movement) |
| `BGAppRefreshTask` fires | `HeartbeatTick` |
| `Tracker.start(config)` | writes state, then `Restore` |
| `Tracker.stop()` | writes state, then `Restore` |

`BGTaskScheduler.register` for the heartbeat task must be called by the host before `application(_:didFinishLaunchingWithOptions:)` returns. Handler body is async; only registration is sync.

## State as source of truth

`StateStore.update` mutates `MutableStateFlow` atomically and synchronously in-memory, then persists asynchronously. After a writer returns, `stateStore.state.value` reflects the write — readers don't race.

`engine.handle` is `suspend` and serialized by a single mutex. Signals are processed strictly in order. There is no per-event flag, per-platform `paused` field, or other parallel source of truth.

## Public API (`Tracker`)

The Tracker facade collapses to:

- `start(config)`: write config + `enabled=true`, send `Restore` to the engine via the service.
- `stop()`: write `enabled=false`, send `Restore`.
- `isTracking` / `getLogs` / `clearLogs` / `requestPosition`: unchanged.

No `notifyStationaryExit`, no `resume`, no per-signal methods. The signal vocabulary is internal.

## Things the architecture explicitly does not solve

- **Active-state recovery from FGS death.** If Android kills the foreground service while in active tracking (no stationary state, no pending triggers because we never entered paused), nothing wakes us back up. Out of scope.
- **Android 12+ FGS background-start failures.** When the engine handles `StationaryExit` from a brief-revival receiver and the FGS-start attempt is rejected, state is correct (`paused=false`) but the engine cannot continue running. Next persistent trigger or host-app launch will retry. Documented limitation.
- **Host iOS plist / permission misconfiguration.** If `UIBackgroundModes` or location-always permission are missing, active mode degrades to foreground-only. SDK does not detect or compensate.
