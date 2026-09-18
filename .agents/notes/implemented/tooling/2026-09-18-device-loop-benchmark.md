# Android device-loop benchmark

The benchmark script compares ADB and agent-device using the same source edit,
Gradle build, and screenshot OCR assertion. The report and raw evidence live in
[the benchmark directory](../../../../mobile/benchmarks/2026-09-18/report.md).

Use a unique visible label per run and count every capture attempt. A successful
capture can contain Android's update screen rather than the app. A fixed delay
was rejected after this happened in a pilot run. Warm OCR before timing and
keep the plugged-in screen awake during the benchmark; restore the setting.

Two final runs per backend passed OCR and visual inspection. The original app
was restored and verified. These exploratory timings do not establish a general
speed ranking or measure element-reference navigation.
