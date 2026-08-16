# WakeBridge iOS placeholder

This directory holds the first SwiftUI shell. It is not an Xcode project and
does not provide configuration, Keychain storage, or a WidgetKit widget yet.

The `shared` Gradle module already builds a static `WakeBridgeShared` framework
for `iosArm64` and `iosSimulatorArm64`. Future iOS work should create an Xcode
app and WidgetKit extension, link that framework, implement a Keychain-backed
configuration store, and call the shared `WakeApi` contract.
