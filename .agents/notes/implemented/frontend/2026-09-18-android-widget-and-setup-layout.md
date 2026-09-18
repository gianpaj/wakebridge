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
No credentials were supplied and no wake request was sent; configured wake
and server-response states still need end-to-end verification.
