# traccar_client_sdk

Flutter plugin for background location tracking. Posts updates to a [Traccar](https://www.traccar.org) server. Wraps the [Traccar Client SDK](https://github.com/traccar/traccar-client-sdk) for Android and iOS — see the main repository for features, architecture, and reliability details.

Requires Android API 24+ and iOS 15+.

## Usage

```dart
import 'package:traccar_client_sdk/traccar_client_sdk.dart';

final tracker = TraccarClientSdk();

await tracker.start(
  serverUrl: 'https://demo.traccar.org',
  deviceId: '123456',
);

// later
await tracker.stop();

// diagnostic logs
final logs = await tracker.getLogs();
```

## Permissions

The plugin requests location, notification (Android 13+), and activity recognition (Android 10+) permissions at runtime via a transparent activity. On first start it also prompts to disable battery optimization for reliable background tracking.

On iOS, add `NSLocationAlwaysAndWhenInUseUsageDescription` and `NSLocationWhenInUseUsageDescription` keys to your app's `Info.plist`.

## License

Apache License 2.0 — see [LICENSE](LICENSE).
