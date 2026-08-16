# WakeBridge mobile

`mobile` is WakeBridge's independent Kotlin Multiplatform project. Android is
the complete v1 target; iOS has a shared framework target and a small SwiftUI
placeholder. The mobile build does not invoke or package the Go server.

## Structure

```text
mobile/
├── shared/       KMP validation, result models, Ktor client, and API contract
├── androidApp/   Compose UI, Android Keystore adapter, Glance widget, Worker
└── iosApp/       SwiftUI placeholder for later iOS and WidgetKit work
```

The shared module accepts only a base server URL and credentials. It knows two
operations: `healthCheck()` and `wake()`. It has no Android widget or storage
dependency. Android owns the credential adapter, UI state, and home-screen
widget.

## Build

Requirements:

- JDK 17 or newer
- Android SDK Platform 36.1
- Android SDK Build Tools 35 or newer

From this directory, run:

```bash
./gradlew :shared:testDebugUnitTest :androidApp:assembleDebug
```

The debug APK is written to:

```text
androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

Install it on a connected development phone with:

```bash
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

Validate the iOS shared target on macOS with:

```bash
./gradlew :shared:compileKotlinIosSimulatorArm64
```

## Configure Android

Open the app and enter:

- the HTTPS origin, such as `https://wake.example.com`;
- the Cloudflare Access client ID and secret, when the Access policy requires a
  service token;
- the separate Wake API bearer token configured on the Jetson.

Cloudflare credentials are optional as a pair. Enter both or leave both blank.
The Wake API token is always required. The app rejects HTTP URLs, URLs with
embedded credentials, query strings, and fragments.

Tap **Test Connection**. The app sends only `GET /health`; it never wakes
gianRTX during setup. A successful response saves the normalized configuration
and enables both wake buttons. Failed or invalid first-time setup leaves them
disabled.

After setup, the main screen sends `POST /wake` and reports **Wake command
sent** only when the server accepts the request. This message does not claim
that gianRTX is awake.

## Credential storage

Android serializes the tested configuration, encrypts it with AES-GCM, and
stores only ciphertext and an initialization vector in private preferences.
The non-exportable AES key lives in Android Keystore. Backups and cleartext
network traffic are disabled in the manifest.

Neither widget state nor WorkManager input contains credentials. The worker
decrypts the saved configuration immediately before the HTTPS request. The app
does not log URLs, tokens, client IDs, client secrets, or authorization headers.

## Home-screen widget

Add **WakeBridge** from the Android widget picker after installing the app.

- Before a successful connection test, the widget says **Setup required** and
  opens the app.
- After setup, one tap changes the widget to **Sending…** and enqueues one
  network-constrained WorkManager job.
- The worker reads secure storage and sends one authenticated `POST /wake`.
- Success leaves **Sent ✓**; failure leaves **Failed**.

Unique work plus a two-second tap gate ignores rapid duplicate taps. The worker
does not retry a failed or ambiguous response because a retry could duplicate a
wake request that the server already accepted. No permanent background service
runs.

## Shared network behavior

The Ktor client sends Cloudflare headers on both operations and adds the bearer
token only to `/wake`. Redirect following is disabled so credentials cannot be
forwarded to another host. The client recognizes the exact successful JSON
responses and converts redirects, authentication rejection, server failure,
transport failure, and malformed responses into small shared error types.

## iOS status

The shared module builds static `WakeBridgeShared` frameworks for physical
devices and Apple Silicon simulators. The SwiftUI files under
`iosApp/WakeBridge` are placeholders; v1 does not include an Xcode project,
Keychain adapter, configuration UI, or WidgetKit extension. Those components
can reuse the shared validation and Ktor API instead of rewriting the wire
contract.

## Physical acceptance test

Use a real Android phone for the final test. Complete setup, add the widget,
disable Wi-Fi and any VPN, use cellular data, and tap once. Confirm one
`wake successful` event in the Jetson journal and verify that gianRTX powers on
from a supported state.
