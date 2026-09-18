# Android widget and setup layout

The Pixel 6 launcher clipped the Glance button label at 115% font size.
A title, spacer, and native button exceeded the widget's 90dp height.

## Decision

Use the entire widget as one tap target, with a power icon and two text lines.
Keep the existing setup and wake actions. Show action-oriented status labels,
use an error accent for failure, and suppress taps while sending. Allow widget
resizing in both directions.

Reject shrinking text or raising the minimum widget height as the sole fix.
Both leave the stacked button competing with the title for space.

The Compose app uses safe drawing and keyboard insets, a scrollable column
limited to 560dp, and minimum rather than fixed button heights. The activity
uses adjustResize so the keyboard does not pan content over system controls.
Required credentials precede the optional Cloudflare group. Secret fields
support Show/Hide, and the primary setup action says it tests and saves.
The ready screen says "Setup verified" because stored configuration does not
prove a live connection. Status messages use a polite accessibility live region.

## Verification

Debug assembly, Android lint, and existing shared unit tests passed.
Lint reports one SDK-target warning.
On Pixel 6, verified the widget at 115% and 150% text size, tapping it to open
setup, empty-form validation, scrolling, and portrait/landscape app layouts.
The original 115% text size and landscape rotation lock were restored.
Automated UI checks did not send a wake request. The user confirmed that
the configured app wakes the computer over Tailscale.

## Connection failure visibility

Network errors already reached the view model, but the neutral status panel
below the setup help text was easy to miss. Setup errors appear directly above
the test button in an error-colored panel. BringIntoViewRequester reveals the
panel after layout, and accessibility semantics mark it as an error. Retrying
or editing clears the error state. Unreachable-server guidance names Tailscale
as a connection to check without claiming the app detected its VPN state.

Verified an unreachable tailnet URL on Pixel 6: the full error appeared without
manual scrolling. The health check used a temporary token, which is not sent
to /health; the token was cleared afterward. Debug build, lint, and existing
shared tests passed. Installing the update resets unsaved setup fields.
