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

The shared module accepts only a base server URL and credentials. It exposes
`healthCheck()`, `wake()`, `status()`, and the `awaitOnline()` polling helper. It has no Android widget or storage
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

Tap **Test & save connection**. The app sends only `GET /health`; it never wakes
gianRTX during setup. A successful response saves the normalized configuration
and enables both wake buttons. Failed or invalid first-time setup leaves them
disabled.

After setup, the main screen sends `POST /wake`, shows **Waking gianRTX…**,
and checks `/status` immediately, then two seconds after each offline response.
Polling stops when the target is online, an API error occurs, or 90 seconds
elapse, including time spent in requests. **gianRTX is online** means the
configured TCP port accepted a connection. It does not verify SSH login or
application readiness. Online is the last check's result, not continuous monitoring.

A timeout says that gianRTX has not responded yet. A status API error says
that the app could not check status, separately from failure to send the wake
command. **Check again** repeats status polling without sending another wake
packet. Set `GIANRTX_SSH_ADDR` on the Jetson to enable these checks; see the
[server configuration](../server-go/README.md#configuration).

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
The whole tile is the tap target. Its two-line layout leaves room for larger
text, and the widget can be resized in either direction.

- Before a successful connection test, the widget says **Tap to set up** and
  opens the app.
- After setup, one tap changes the widget to **Sending…** and enqueues one
  network-constrained WorkManager job.
- The worker reads secure storage and sends one authenticated `POST /wake`.
- After an accepted wake request, **Waking…** remains visible during polling.
- A successful status check leaves **Online · wake again**. A timeout leaves
  **No response · check again**; a status error leaves **Check failed · check
  again**, or **Set up status · check again** when the address is not configured.
- Tapping a check-again state only polls status. A failed wake request leaves
  **Failed · retry**, which sends a wake request when tapped.

Unique work plus a two-second tap gate ignores rapid duplicate taps. The worker
does not retry a failed or ambiguous response because a retry could duplicate a
wake request that the server already accepted. No permanent background service
runs.

## Shared network behavior

The Ktor client sends Cloudflare headers on all operations and adds the bearer
token to `/wake` and `/status`. Redirect following is disabled so credentials cannot be
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

For startup confirmation, verify that the app and widget progress from waking
to online. Test an unreachable target, missing server probe configuration, and
an invalid bearer token. Check-again taps must send only `/status` requests.
The worker runs polling without opening the app; Android may defer or stop it
under background execution constraints. Cancelling a poll closes its client.
