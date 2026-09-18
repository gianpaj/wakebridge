# ADB vs agent-device edit/build benchmark

The mean edit-to-validated-screen time was **9.65s with ADB** and
**10.78s with agent-device**. Agent-device was 1.13s slower in this small sample;
there is no evidence here of an end-to-end speedup.

ADB needed five captures in its first run and two in its second. Agent-device
needed one per run, but spent longer in launch. The screenshot row below is
cumulative across retries, so it is not a per-frame capture-speed comparison.
Installation took about five seconds with either tool.

Device: Pixel 6 over USB. Host: macOS arm64. Tools: agent-device 0.21.6,
ADB 37.0.1, Gradle 8.13. Times are wall-clock seconds.

Two runs per backend, in ADB / agent-device / agent-device / ADB order.
Gradle, OCR, and device sessions are warmed first. Each run edits the app label,
builds an APK, installs without clearing data, and restarts the app.
Both paths retry capture plus OCR until the label appears, pausing 0.25s per
attempt. The same macOS Vision OCR check requires the exact unique label in
each saved screenshot. Labels include a run timestamp to avoid reusing cached
APKs from earlier benchmark invocations. Gradle's other caches remain enabled.

| Stage       |  ADB 1 | Agent 1 | Agent 2 | ADB 2 | ADB mean | Agent mean |
| ----------- | -----: | ------: | ------: | ----: | -------: | ---------: |
| edit        |  0.000 |   0.000 |   0.000 | 0.000 |    0.000 |      0.000 |
| build       |  1.693 |   1.363 |   1.271 | 1.176 |    1.434 |      1.317 |
| install     |  5.058 |   5.172 |   5.537 | 5.142 |    5.100 |      5.355 |
| launch      |  0.159 |   3.273 |   2.719 | 0.278 |    0.218 |      2.996 |
| settle      |  1.272 |   0.255 |   0.251 | 0.510 |    0.891 |      0.253 |
| screenshot  |  1.731 |   0.384 |   0.851 | 0.767 |    1.249 |      0.617 |
| validate    |  1.135 |   0.252 |   0.225 | 0.372 |    0.754 |      0.238 |
| device_loop |  9.355 |   9.335 |   9.583 | 7.069 |    8.212 |      9.459 |
| total       | 11.051 |  10.700 |  10.855 | 8.247 |    9.649 |     10.778 |

`settle` measures the explicit pauses, not either tool's UI-stability detector.
`device_loop` excludes the source edit and Gradle build. Rounded edit times of
0.000s are nonzero; full precision is preserved in `timings.json`.

## Commands and reproduction

Run from the repository root on this Mac with an unlocked, connected Pixel:

```bash
python3 mobile/scripts/benchmark-device-loop.py \
  --serial 18211FDF6008E0 \
  --output /tmp/wakebridge-bench-new
```

The output directory must not exist. Python 3, Xcode command-line tools with
Swift/Vision, Android SDK/ADB, agent-device, and the project's Gradle toolchain
are required. The script temporarily changes the app label and the plugged-in
screen-awake setting, then restores both. It preserves the source's existing
uncommitted changes and installs with app data intact. Avoid editing
`WakeScreen.kt` or interacting with the phone while it runs.

| Operation | ADB path                                                                 | agent-device path                             |
| --------- | ------------------------------------------------------------------------ | --------------------------------------------- |
| Build     | `./gradlew :androidApp:assembleDebug --console=plain`                    | Same                                          |
| Install   | `adb -s SERIAL install -r APK`                                           | `agent-device install APP APK`                |
| Restart   | `am force-stop APP`, then `am start -W -n APP/.MainActivity` through ADB | `agent-device open APP --relaunch`            |
| Capture   | `adb -s SERIAL exec-out screencap -p`                                    | `agent-device screenshot FILE --no-stabilize` |
| Validate  | Exact label match in screenshot OCR                                      | Same                                          |

Agent commands use one named session and an explicit Android device serial.
Every subprocess invocation is included in its stage timing. Setup and cleanup
use ADB for shared device preparation and restoration.

## Evidence

- `WakeBridge A1 184558`: OCR passed. [Screenshot](1-adb.png)
- `WakeBridge D2 184558`: OCR passed. [Screenshot](2-agent-device.png)
- `WakeBridge D3 184558`: OCR passed. [Screenshot](3-agent-device.png)
- `WakeBridge A4 184558`: OCR passed. [Screenshot](4-adb.png)

## Scope and limitations

- Process timings exclude assistant reasoning, tool dispatch, approval, and image-viewer overhead.
- Two samples per backend are exploratory, not a statistically robust speed ranking.
- Build and install variation can dominate capture savings. Device loop excludes editing and building.
- agent-device uses `--no-stabilize`; readiness is confirmed by screenshot OCR for both paths.
- Capture, validation, and settle times include every attempt; failed frames are retained.
- This tests rebuild/capture workflows, not element-reference navigation or input speed.
- Original source was restored, rebuilt, installed, and verified by OCR.
- The plugged-in screen was kept awake during the run; its original setting was restored.

## Setup, restoration, and pilot findings

Measured setup stages totaled 3.18s; restoration stages totaled 8.30s. These
are outside the comparison. Small uninstrumented setup operations, file writes,
and report generation are not included in those sums. Full stage timings are
in [timings.json](timings.json).

Two preliminary attempts were excluded before the final four-run comparison:

- The first OCR invocation took 33.20s, then later calls took about 0.2s.
  The screen subsequently slept and a black screenshot failed validation.
- A fixed 0.5s delay captured Android's “Updating…” screen. Capturing a PNG
  successfully did not mean the changed app was ready.

The final script warms OCR and keeps the plugged-in screen awake. Both paths
use the same bounded screenshot/OCR retry loop. Final-run missed captures are
saved alongside the successful images, including [an updating frame](1-adb-miss-1.png).
All four final screenshots were also visually inspected. The original
[WakeBridge label](restored.png) passed OCR after restoration, and the
screen-awake setting was confirmed back at its original value of `0`.

For this workflow, choose agent-device for element references and state-aware
actions when useful; this experiment does not establish that it is faster.
It does show that the two paths have similar rebuild-loop costs, with installs
accounting for roughly half the elapsed time.

## TODO: benchmark Android Studio deployment

This comparison uses incremental Gradle builds followed by APK installation,
not clean rebuilds. It does not measure Android Studio's Run, Apply Changes,
or Compose Live Edit paths. Installation accounts for about five seconds per
run, making deployment the next optimization to test.

- [ ] Benchmark Run, Apply Changes and Restart Activity, and Live Edit using
  the same visible text edit on the Pixel 6.
- [ ] Check whether Live Edit detects source changes written outside Studio.
  Record any required save, focus, or manual apply action.
- [ ] Find a way to trigger the supported Studio actions without computer use.
  Record any manual steps separately from automated timings.
- [ ] Measure edit-to-visible-update and total edit-to-validated-screenshot
  time using agent-device capture and the same exact-label OCR check.
- [ ] Separate initial deployment from warm iterations, vary execution order,
  and record unsupported changes or fallback deployments.
- [ ] Restore the original text and verify the restored app on the device.

References: [Run and Apply Changes](https://developer.android.com/studio/run),
[Compose Live Edit](https://developer.android.com/develop/ui/compose/tooling/iterative-development).
