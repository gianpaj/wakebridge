# gianRTX Wake

gianRTX Wake turns one authenticated phone tap into one Wake-on-LAN request for
a single computer. A small Go service runs continuously on a Jetson Nano. An
Android app and home-screen widget reach that service through Cloudflare Access
and Cloudflare Tunnel.

```text
Android app or widget
        │
        │ HTTPS: Cloudflare credentials + app bearer token
        ▼
Cloudflare Access and Tunnel
        │
        │ HTTP over the tunnel to 127.0.0.1:8787
        ▼
Jetson Nano: Go wake server
        │
        │ UDP Wake-on-LAN magic packet
        ▼
gianRTX
```

The system exposes no shell, remote shutdown, arbitrary target, or host lookup.
The client can request only `wake`; the Jetson decides the one MAC address,
broadcast address, and UDP port that operation means.

## Repository

```text
.
├── README.md
├── server-go/   Standalone Go module and systemd deployment
└── mobile/      Standalone Kotlin Multiplatform and Android Gradle project
```

The components share an HTTP contract, not a build. Run Go commands inside
`server-go` and Gradle commands inside `mobile`. Either component can be built,
tested, released, or replaced without invoking the other build system.

## HTTP contract

### Health

```http
GET /health
CF-Access-Client-Id: <client-id>          # when Access requires it
CF-Access-Client-Secret: <client-secret>  # when Access requires it
```

```json
{"ok": true}
```

The Android setup screen uses this endpoint to validate HTTPS connectivity and
Cloudflare Access without waking gianRTX.

### Wake

```http
POST /wake
Authorization: Bearer <wake-api-token>
CF-Access-Client-Id: <client-id>          # when Access requires it
CF-Access-Client-Secret: <client-secret>  # when Access requires it
```

```json
{"ok": true, "target": "gianRTX"}
```

The server returns:

| Status | Meaning |
| --- | --- |
| `200` | The server sent the magic packet |
| `401` | The local Wake API token is invalid |
| `404` | The route is unknown |
| `405` | The route does not allow that HTTP method |
| `500` | The server could not send the Wake-on-LAN packet |

Cloudflare may reject a request before it reaches this API. A `200` wake
response confirms packet transmission, not that gianRTX finished booting.

## Cloudflare's role

`cloudflared` publishes a route such as:

```text
wake.example.com → http://127.0.0.1:8787
```

Cloudflare Tunnel gives the phone a public HTTPS origin without opening an
inbound port on the home router. Cloudflare Access authenticates the mobile
client at that origin. The client may present a Cloudflare service-token pair;
Cloudflare consumes those headers before forwarding the request.

The Go process does not implement Cloudflare APIs and does not receive
Cloudflare configuration. Its bearer token remains a separate application-level
check in case the Access or tunnel policy changes.

## How Wake-on-LAN works here

At startup, the server validates `GIANRTX_MAC`, `WOL_BROADCAST`, and `WOL_PORT`.
For an authenticated wake request it builds the standard packet:

```text
FF FF FF FF FF FF + gianRTX MAC repeated 16 times
```

It sends the 102-byte packet three times by UDP to the configured LAN broadcast
address. The request cannot override any destination. gianRTX must use a network
adapter, firmware setting, operating-system setting, and sleep or soft-off state
that support magic-packet wake.

## End-to-end setup

1. Enable magic-packet Wake-on-LAN for gianRTX and record its wired Ethernet MAC
   address. Confirm the desired power state keeps the adapter ready to wake.
2. Build and install the Go service on the Jetson. Set a long random
   `WAKE_API_TOKEN`, the gianRTX MAC, and the Jetson subnet's broadcast address.
   Enable `gianrtx-wake.service` at boot.
3. Verify `curl http://127.0.0.1:8787/health` on the Jetson, then make one local
   authenticated `/wake` request and confirm that the hardware wakes.
4. Install and run `cloudflared` separately. Route a dedicated hostname to
   `http://127.0.0.1:8787` and protect it with Cloudflare Access. Create a
   service token for the Android client when the policy requires one.
5. Build and install the Android app. Enter the HTTPS hostname, Cloudflare
   credentials, and Wake API token. Tap **Test Connection** and add the
   **gianRTX Wake** home-screen widget.
6. Disable phone Wi-Fi and any VPN. On cellular data, tap the widget once. Check
   `journalctl -u gianrtx-wake -f` for one successful request and confirm that
   gianRTX powers on.

See [server-go/README.md](server-go/README.md) for Jetson installation and
Cloudflare assumptions. See [mobile/README.md](mobile/README.md) for Android
build, secure storage, widget behavior, and the iOS boundary.

## Development checks

Server:

```bash
cd server-go
go test ./...
GOOS=linux GOARCH=arm64 CGO_ENABLED=0 go build ./cmd/wake-server
```

Mobile:

```bash
cd mobile
./gradlew :shared:testDebugUnitTest :androidApp:assembleDebug
./gradlew :shared:compileKotlinIosSimulatorArm64
```

The final cellular-data and power-state test requires the actual Jetson,
Cloudflare hostname, Android phone, and gianRTX hardware.
