# Agent Note: Single-purpose wake boundary

Status: implemented

## Problem

Waking gianRTX from an untrusted mobile network requires crossing the public
internet into a private LAN. A general remote-control service would add targets,
commands, credentials, and failure modes that this single-target system does not
need. The Android widget also needs short, reliable asynchronous execution
without turning the app into a permanent background service.

## Decision

The repository ships two independent components joined by an
HTTP contract. The Kotlin Multiplatform client can test `GET /health`, request
`POST /wake`, and check `GET /status`. The Go server maps that fixed wake request to one startup-validated
MAC address, IPv4 broadcast address, and UDP port. No caller can select a target
or provide server-side execution data.

Cloudflare Access authenticates the public HTTPS request and Tunnel forwards it
to the Go server's loopback listener. The Go server remains unaware of
Cloudflare and requires its own bearer token. The client refuses plain HTTP and
redirects, and Android encrypts the tested configuration with a non-exportable
Android Keystore key.

The Glance widget places no credentials in widget state or WorkManager input. An
action callback enqueues uniquely named, network-constrained work; the worker
decrypts configuration, sends one wake request, and polls target status. It
records a small display status and never automatically retries an ambiguous
wake response. See [startup confirmation](2026-09-18-startup-confirmation.md).

## Runtime boundaries

`server-go` is a standalone Go module built into one Linux ARM64 executable.
`mobile` is a standalone Gradle build with a shared KMP domain/network module,
an Android app, and iOS framework targets. Neither build invokes the other.

The server owns target identity and Wake-on-LAN delivery. Cloudflare owns public
TLS, Access policy, and tunnel transport. Shared Kotlin owns URL validation and
the HTTP wire contract. Android owns Keystore persistence, Compose UI, Glance,
and WorkManager execution.

## Alternatives considered

**Direct Wake-on-LAN from the phone.** Internet routers do not normally forward
LAN broadcasts, and exposing UDP broadcast delivery would weaken the security
boundary. The always-on Jetson is the LAN-side sender.

**A reusable command or multi-machine API.** Client-supplied commands, MAC
addresses, hostnames, or destinations would turn a fixed function into a remote
control surface. V1 deliberately gives up that flexibility.

**VPN-only access.** Tailscale or another VPN could protect the route, but the v1
success condition requires a widget tap over ordinary cellular data without a
phone VPN. Cloudflare Access and the application token provide two checks.

**A permanent Android service or automatic request retries.** A permanent
service is unnecessary for a bounded wake-and-check operation. Retrying after a lost response can
send a second command even when the first reached the server, so WorkManager
performs one attempt and reports failure for manual action.

## Verification

Go tests cover startup validation, magic-packet bytes, authentication, routes,
methods, and wake failures. The server builds as a static Linux ARM64 binary.
Shared Kotlin tests cover HTTPS validation and request headers, and the Android
debug APK and iOS simulator framework targets compile.

The physical cellular-data test remains an external verification because it
requires the configured Jetson, Cloudflare account, Android phone, gianRTX
firmware, and powered network adapter.

## Consequences

The system has a small mental model and a narrow attack surface: one public
hostname, one application secret, one configured target, and wake/status operations. The Go
service needs no root privilege or persistent data, and the Android widget needs
no resident process.

The trade-off is intentional rigidity. Adding another machine, verifying application
readiness, retrying delivery, or supporting iOS
secure storage and WidgetKit requires later decisions. The system also depends
on the Jetson, Cloudflare Tunnel, Access policy, and gianRTX Wake-on-LAN support
remaining correctly configured.
