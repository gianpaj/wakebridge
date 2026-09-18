# WakeBridge server

`server-go` is WakeBridge's Jetson-side service. It accepts authenticated wake and status requests, builds a Wake-on-LAN magic packet for the configured gianRTX MAC
address, and sends that packet to the configured LAN broadcast address. It has
no database, web framework, command runner, or client-selectable target.

## Requirements

- Go 1.23 or newer for building
- a 64-bit Jetson Linux installation
- network reachability from the Jetson to the gianRTX broadcast domain
- Wake-on-LAN enabled in gianRTX firmware and its Ethernet adapter

The process needs no root privileges. It binds to `127.0.0.1:8787` by default.

## Build and test

Run native tests and build from this directory:

```bash
go test ./...
go build -trimpath -o bin/wakebridge-server ./cmd/wakebridge-server
```

Cross-compile a static Linux ARM64 binary from macOS or another Go host:

```bash
GOOS=linux GOARCH=arm64 CGO_ENABLED=0 \
  go build -trimpath -o bin/wakebridge-server-linux-arm64 ./cmd/wakebridge-server
```

## Configuration

Copy `examples/wakebridge.env.example` to a local file and set:

| Variable | Required | Meaning |
| --- | --- | --- |
| `WAKE_API_TOKEN` | yes | Long random bearer token shared with the mobile app |
| `GIANRTX_MAC` | yes | Six-byte Ethernet MAC address for gianRTX |
| `WOL_BROADCAST` | yes | IPv4 broadcast address, such as `192.168.1.255` |
| `LISTEN_ADDR` | no | HTTP listen address; default `127.0.0.1:8787` |
| `WOL_PORT` | no | UDP destination port; default `9` |
| `GIANRTX_SSH_ADDR` | no | gianRTX LAN host:port, such as `192.168.1.100:22`; enables startup checks |

The server rejects missing or malformed settings before it starts. It validates
the MAC address, IPv4 address, listen address, and UDP port. Logs never include
the API token or HTTP authorization header.

Generate a token with a password manager or, on a system with OpenSSL:

```bash
openssl rand -hex 32
```

Choose the broadcast address for the Jetson's gianRTX-facing subnet. Do not use
the public hostname, gianRTX hostname, or phone-supplied data as the UDP target.

## Install on the Jetson

Build on the Jetson or copy the Linux ARM64 binary to it. Then run:

```bash
sudo useradd --system --no-create-home --shell /usr/sbin/nologin wakebridge
sudo install -m 0755 bin/wakebridge-server-linux-arm64 \
  /usr/local/bin/wakebridge-server
sudo install -m 0600 examples/wakebridge.env.example /etc/wakebridge.env
sudo install -m 0644 deploy/wakebridge.service \
  /etc/systemd/system/wakebridge.service
sudoedit /etc/wakebridge.env
sudo systemctl daemon-reload
sudo systemctl enable --now wakebridge.service
```

The environment file may remain owned by root because systemd reads it before
starting the service as `wakebridge`.

Check the service and follow its structured logs:

```bash
systemctl status wakebridge.service
journalctl -u wakebridge -f
```

## Local checks

On the Jetson, verify health without sending a magic packet:

```bash
curl --fail http://127.0.0.1:8787/health
```

Send a wake request with the token loaded in your shell:

```bash
curl --fail --request POST \
  --header "Authorization: Bearer $WAKE_API_TOKEN" \
  http://127.0.0.1:8787/wake
```

A successful HTTP response means the server sent three standard 102-byte magic
packets. It does not prove that gianRTX reached an awake state.

## Cloudflare Tunnel

Cloudflare remains outside this process. Configure `cloudflared` separately so
one protected public hostname reaches the loopback listener:

```yaml
ingress:
  - hostname: wake.example.com
    service: http://127.0.0.1:8787
  - service: http_status:404
```

Protect `wake.example.com` with Cloudflare Access. Create a service token for
the mobile client when the Access policy requires it. The app sends the
`CF-Access-Client-Id` and `CF-Access-Client-Secret` headers to Cloudflare; the Go
server only validates its separate bearer token.

## Wake-on-LAN troubleshooting

If HTTP succeeds but gianRTX stays off, check the physical path:

1. Use wired Ethernet on gianRTX; many Wi-Fi adapters cannot wake a powered-off
   computer.
2. Enable Wake-on-LAN or PCIe wake in firmware.
3. Enable magic-packet wake in the operating system and network adapter.
4. Confirm that the selected sleep or soft-off state supplies standby power to
   the adapter.
5. Verify the MAC and subnet broadcast address from the Jetson.
6. Watch `journalctl` while testing and confirm one `wake successful` event.

The final power-state test depends on gianRTX hardware and firmware and cannot
be replaced by the unit tests.

## Startup confirmation

Set `GIANRTX_SSH_ADDR` in `/etc/wakebridge.env` and restart `wakebridge.service`.
Use a DHCP reservation for gianRTX's wired LAN address. No SSH credentials,
key files, extra privileges, or software on gianRTX are required. An unset
address leaves wake requests available but disables target status checks.

See the root [HTTP contract](../README.md#target-status) for `/status` responses.
Verify from the Jetson with an authenticated request:

```bash
curl --fail --header "Authorization: Bearer $WAKE_API_TOKEN" http://127.0.0.1:8787/status
```

Confirm `online: true` while SSH listens and `online: false` while gianRTX is
asleep. A successful port connection does not prove which service is listening.
