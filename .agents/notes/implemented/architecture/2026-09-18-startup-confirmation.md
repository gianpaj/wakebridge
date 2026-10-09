# Startup confirmation

WakeBridge checks a single server-configured TCP port on demand. The app and
widget poll after a wake request and stop on success, error, or a 90-second
deadline. This keeps the Go service stateless and gives the phone feedback
without requiring direct access to the LAN.

## Decisions

- Use GIANRTX_SSH_ADDR with a one-second TCP connection timeout. No SSH
  authentication: reachability satisfies the requested wake confirmation.
- Keep the setting optional so deploying the server preserves wake requests.
- Authenticate /status with the existing bearer token and disable caching.
- Distinguish target unreachability from probe setup, DNS, and API errors.
- Share the polling loop between the app and widget; check-only retries do
  not send additional wake packets.
- No server jobs, persistent status, SSH keys, or command execution are needed.

## Verification

- Go: `go test -race ./...` and `go vet ./...` passed. Linux ARM64 compilation
  passed. Tests include local listening/closed TCP ports, cancellation,
  configuration, authentication, and probe-error responses.
- Mobile: `:shared:testDebugUnitTest :androidApp:assembleDebug` passed on Linux
  with a temporary JDK 17 and Android command-line SDK. All 17 shared tests
  passed, including polling deadlines, cancellation, and status response cases.
- Android Studio is not required for these checks. No device/emulator test or
  iOS compilation ran here. Android can defer or interrupt widget work.
- Jetson rollout requires GIANRTX_SSH_ADDR in /etc/wakebridge.env and a service
  restart. A real Android/Jetson wake cycle remains a physical acceptance check.

The app and widget display the last successful check, not continuous status.
Check-again actions poll without waking. Missing probe configuration leaves
wake requests available and produces a separate status error.
