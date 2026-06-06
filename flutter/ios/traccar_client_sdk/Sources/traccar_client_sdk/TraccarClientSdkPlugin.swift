import Flutter
import UIKit
import TraccarClientSDK

public class TraccarClientSdkPlugin: NSObject, FlutterPlugin {
  public static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(name: "traccar_client_sdk", binaryMessenger: registrar.messenger())
    let instance = TraccarClientSdkPlugin()
    registrar.addMethodCallDelegate(instance, channel: channel)
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    switch call.method {
    case "start":
      let args = call.arguments as! [String: Any]
      let config = parseConfig(args)
      runHandler(result) {
        let tracker = try await TrackerKt.sharedTracker()
        try await tracker.start(config: config)
        return nil
      }
    case "stop":
      runHandler(result) {
        let tracker = try await TrackerKt.sharedTracker()
        try await tracker.stop()
        return nil
      }
    case "requestPosition":
      let args = call.arguments as! [String: Any]
      let config = parseConfig(args)
      runHandler(result) {
        let tracker = try await TrackerKt.sharedTracker()
        return try await tracker.requestPosition(config: config)
      }
    case "isTracking":
      runHandler(result) {
        let tracker = try await TrackerKt.sharedTracker()
        return tracker.isTracking.value
      }
    case "getLogs":
      runHandler(result) {
        let tracker = try await TrackerKt.sharedTracker()
        return try await tracker.getLogs().map { ["time": $0.time, "message": $0.message] as [String: Any] }
      }
    case "clearLogs":
      runHandler(result) {
        let tracker = try await TrackerKt.sharedTracker()
        try await tracker.clearLogs()
        return nil
      }
    default:
      result(FlutterMethodNotImplemented)
    }
  }

  private func runHandler(_ result: @escaping FlutterResult, block: @escaping () async throws -> Any?) {
    Task {
      do {
        let value = try await block()
        result(value)
      } catch {
        result(FlutterError(code: String(describing: type(of: error)), message: error.localizedDescription, details: nil))
      }
    }
  }

  private func parseConfig(_ args: [String: Any]) -> Config {
    let location = args["location"] as! [String: Any]
    let notification = args["notification"] as! [String: Any]
    return Config(
      serverUrl: args["serverUrl"] as! String,
      deviceId: args["deviceId"] as! String,
      location: LocationConfig(
        accuracy: parseAccuracy(location["accuracy"] as! String),
        distanceMeters: Int32(location["distanceMeters"] as! Int),
        intervalSeconds: Int32(location["intervalSeconds"] as! Int),
        angleDegrees: Int32(location["angleDegrees"] as! Int),
        stopDetection: location["stopDetection"] as! Bool,
        stopTimeoutSeconds: Int32(location["stopTimeoutSeconds"] as! Int),
        stationaryRadiusMeters: Int32(location["stationaryRadiusMeters"] as! Int)
      ),
      wakeLock: args["wakeLock"] as! Bool,
      buffer: args["buffer"] as! Bool,
      notification: NotificationConfig(text: notification["text"] as! String)
    )
  }

  private func parseAccuracy(_ name: String) -> Accuracy {
    switch name {
    case "HIGHEST": return Accuracy.highest
    case "HIGH": return Accuracy.high
    case "LOW": return Accuracy.low
    default: return Accuracy.medium
    }
  }
}
