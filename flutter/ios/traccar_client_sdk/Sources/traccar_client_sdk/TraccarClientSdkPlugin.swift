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
      let args = call.arguments as? [String: Any] ?? [:]
      let config = Config(
        serverUrl: args["serverUrl"] as? String ?? "",
        deviceId: args["deviceId"] as? String ?? ""
      )
      Tracker.shared.start(config: config)
      result(true)
    case "stop":
      Tracker.shared.stop()
      result(nil)
    case "getLogs":
      let logs = Tracker.shared.getLogs().map { "\($0.time) \($0.message)" }
      result(logs)
    default:
      result(FlutterMethodNotImplemented)
    }
  }
}
