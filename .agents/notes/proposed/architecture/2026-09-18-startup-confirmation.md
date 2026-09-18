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

Implementation and verification are in progress on feat/wake-status.
Physical Jetson/Android wake testing requires the configured LAN destination.
