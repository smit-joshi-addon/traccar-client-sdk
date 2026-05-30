import 'package:flutter/services.dart';

/// Location-accuracy preset. Maps to the SDK's `Accuracy` enum.
enum Accuracy { highest, high, medium, low }

/// Tuning parameters for the location pipeline.
class LocationConfig {
  const LocationConfig({
    this.accuracy = Accuracy.medium,
    this.distanceMeters = 75,
    this.intervalSeconds = 300,
    this.angleDegrees = 0,
    this.stopDetection = true,
    this.stopTimeoutSeconds = 60,
    this.stationaryRadiusMeters = 100,
  });

  final Accuracy accuracy;
  final int distanceMeters;
  final int intervalSeconds;
  final int angleDegrees;
  final bool stopDetection;
  final int stopTimeoutSeconds;
  final int stationaryRadiusMeters;

  Map<String, Object?> _toMap() => {
        'accuracy': accuracy.name.toUpperCase(),
        'distanceMeters': distanceMeters,
        'intervalSeconds': intervalSeconds,
        'angleDegrees': angleDegrees,
        'stopDetection': stopDetection,
        'stopTimeoutSeconds': stopTimeoutSeconds,
        'stationaryRadiusMeters': stationaryRadiusMeters,
      };
}

/// Foreground-service notification settings (Android only).
class NotificationConfig {
  const NotificationConfig({this.text = 'Location tracking'});

  final String text;

  Map<String, Object?> _toMap() => {'text': text};
}

/// Tracker configuration passed to [TraccarClientSdk.start].
class Config {
  const Config({
    required this.serverUrl,
    required this.deviceId,
    this.location = const LocationConfig(),
    this.wakeLock = false,
    this.notification = const NotificationConfig(),
  });

  final String serverUrl;
  final String deviceId;
  final LocationConfig location;

  /// Hold a wakelock while tracking (Android only).
  final bool wakeLock;
  final NotificationConfig notification;

  Map<String, Object?> _toMap() => {
        'serverUrl': serverUrl,
        'deviceId': deviceId,
        'location': location._toMap(),
        'wakeLock': wakeLock,
        'notification': notification._toMap(),
      };
}

/// A single diagnostic log entry.
class LogEntry {
  const LogEntry({required this.time, required this.message});

  /// Epoch milliseconds at which the entry was recorded.
  final int time;
  final String message;
}

/// Entry point for the Traccar Client SDK Flutter plugin.
class TraccarClientSdk {
  static const MethodChannel _channel = MethodChannel('traccar_client_sdk');

  /// Starts background location tracking with [config]. Returns false if
  /// required permissions were denied (Android).
  Future<bool> start(Config config) async {
    final result = await _channel.invokeMethod<bool>('start', config._toMap());
    return result ?? false;
  }

  /// Stops tracking.
  Future<void> stop() => _channel.invokeMethod<void>('stop');

  /// Returns whether tracking is currently active.
  Future<bool> isTracking() async {
    final result = await _channel.invokeMethod<bool>('isTracking');
    return result ?? false;
  }

  /// Returns recent diagnostic entries, oldest first.
  Future<List<LogEntry>> getLogs() async {
    final raw =
        await _channel.invokeListMethod<Map<dynamic, dynamic>>('getLogs');
    if (raw == null) return const [];
    return raw
        .map((m) => LogEntry(
              time: m['time'] as int,
              message: m['message'] as String,
            ))
        .toList(growable: false);
  }

  /// Clears all stored diagnostic entries.
  Future<void> clearLogs() => _channel.invokeMethod<void>('clearLogs');
}
