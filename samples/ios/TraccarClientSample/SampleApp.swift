import SwiftUI
import TraccarClientSDK

@main
struct SampleApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

struct ContentView: View {
    @State private var serverUrl = "https://demo.traccar.org/"
    @State private var deviceId = "123456"
    @State private var isTracking = false

    var body: some View {
        Form {
            Section {
                TextField("Server URL", text: $serverUrl)
                    .keyboardType(.URL)
                    .textInputAutocapitalization(.never)
                TextField("Device ID", text: $deviceId)
                    .textInputAutocapitalization(.never)
            }
            Section {
                Button(isTracking ? "Stop" : "Start") {
                    if isTracking {
                        Tracker.shared.stop()
                        isTracking = false
                    } else {
                        Tracker.shared.start(
                            config: Config(serverUrl: serverUrl, deviceId: deviceId)
                        )
                        isTracking = true
                    }
                }
            }
        }
    }
}
