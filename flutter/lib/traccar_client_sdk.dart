import 'package:flutter/services.dart';

/// Entry point for the Traccar Client SDK Flutter plugin.
class TraccarClientSdk {
  static const MethodChannel _channel = MethodChannel('traccar_client_sdk');

  /// Starts background location tracking. Returns false if required
  /// permissions were denied (Android).
  Future<bool> start({
    required String serverUrl,
    required String deviceId,
  }) async {
    final result = await _channel.invokeMethod<bool>('start', {
      'serverUrl': serverUrl,
      'deviceId': deviceId,
    });
    return result ?? false;
  }

  /// Stops tracking and clears the saved configuration.
  Future<void> stop() => _channel.invokeMethod<void>('stop');

  /// Returns recent diagnostic log lines.
  Future<List<String>> getLogs() async {
    final logs = await _channel.invokeListMethod<String>('getLogs');
    return logs ?? const [];
  }
}
