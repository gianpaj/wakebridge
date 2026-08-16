# Agent Note: WakeBridge product identity

Status: implemented

## Problem

The original project name also appeared in package names, executable paths,
systemd resources, secure-storage identifiers, widget classes, and the KMP
framework name. Renaming only visible labels would leave deployment and source
identities inconsistent. At the same time, `gianRTX` is the configured machine,
not the product name, so a broad replacement would change the fixed API target.

## Decision

The product is named **WakeBridge** and described as a self-hosted Android button
and widget for securely waking a PC from anywhere. Product-facing source and
runtime identifiers use `WakeBridge` or `wakebridge` consistently.

The Go command is `cmd/wakebridge-server`. Deployment uses
`/usr/local/bin/wakebridge-server`, `/etc/wakebridge.env`,
`wakebridge.service`, and the unprivileged `wakebridge` system user.

Android uses the `dev.gianpaj.wakebridge` application and package namespace,
`WakeBridgeApplication`, and `WakeBridgeWidget`. The shared Kotlin framework is
`WakeBridgeShared`, and the iOS placeholder is named `WakeBridge`.

The single configured computer remains `gianRTX`. The `GIANRTX_MAC` environment
variable, the **Wake gianRTX** action, and the API response target remain
unchanged. The rename does not broaden or otherwise alter the HTTP contract.

Because this repository has not released an application or deployment, it does
not carry compatibility aliases or storage migrations for the old identifiers.

## Alternatives considered

**Rename only visible labels.** This would leave installation commands, service
names, package namespaces, and generated artifacts under the old identity.

**Rename the configured target too.** The product rename does not change which
machine the server wakes. Generalizing or renaming that target would be a
separate behavioral and API decision.

**Keep compatibility aliases.** Aliases and migrations would add operational
paths for identities that have not shipped. They can be introduced later if a
real compatibility requirement appears.

## Consequences

Source, build artifacts, Android identity, and Jetson deployment now use one
recognizable product name. Existing local development installs or manual test
deployments under the old names must be replaced rather than upgraded in place.
The narrow, single-target behavior remains unchanged.
