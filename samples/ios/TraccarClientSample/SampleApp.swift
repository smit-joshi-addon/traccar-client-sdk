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
    @State private var tracker: Tracker?

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
                Button(tracker == nil ? "Start" : "Stop") {
                    if let current = tracker {
                        current.stop()
                        tracker = nil
                    } else {
                        let newTracker = TrackerIosKt.createTracker(
                            config: Config(serverUrl: serverUrl, deviceId: deviceId)
                        )
                        newTracker.start()
                        tracker = newTracker
                    }
                }
            }
        }
    }
}
