// swift-tools-version:5.5
import PackageDescription

let package = Package(
    name: "TraccarClientSDK",
    platforms: [
        .iOS(.v15),
    ],
    products: [
        .library(name: "TraccarClientSDK", targets: ["TraccarClientSDK"]),
    ],
    targets: [
        .binaryTarget(
            name: "TraccarClientSDK",
            path: "build/XCFrameworks/release/TraccarClientSDK.xcframework"
        ),
    ]
)
