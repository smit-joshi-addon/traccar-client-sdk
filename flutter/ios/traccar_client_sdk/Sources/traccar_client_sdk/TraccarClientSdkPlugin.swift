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
      Task {
        let tracker = try await TrackerKt.sharedTracker()
        try await tracker.start(config: config)
        result(true)
      }
    case "stop":
      Task {
        let tracker = try await TrackerKt.sharedTracker()
        try await tracker.stop()
        result(nil)
      }
    case "requestPosition":
      let args = call.arguments as! [String: Any]
      let config = parseConfig(args)
      Task {
        let tracker = try await TrackerKt.sharedTracker()
        tracker.requestPosition(config: config)
        result(nil)
      }
    case "isTracking":
      Task {
        let tracker = try await TrackerKt.sharedTracker()
        result(tracker.isTracking.value)
      }
    case "getLogs":
      Task {
        let tracker = try await TrackerKt.sharedTracker()
        let entries = tracker.getLogs().map { ["time": $0.time, "message": $0.message] as [String: Any] }
        result(entries)
      }
    case "clearLogs":
      Task {
        let tracker = try await TrackerKt.sharedTracker()
        tracker.clearLogs()
        result(nil)
      }
    default:
      result(FlutterMethodNotImplemented)
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
