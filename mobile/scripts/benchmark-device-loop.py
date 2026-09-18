#!/usr/bin/env python3
"""Compare ADB and agent-device edit/build/capture loops on macOS; restore afterward."""
import argparse
import json
from pathlib import Path
import statistics
import subprocess
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
parser.add_argument('--output', type=Path, required=True, help='New artifact directory')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
out = args.output.resolve()
out.mkdir(parents=True, exist_ok=False)
source = root / 'androidApp/src/main/kotlin/dev/gianpaj/wakebridge/ui/WakeScreen.kt'
apk = root / 'androidApp/build/outputs/apk/debug/androidApp-debug.apk'
original = source.read_bytes()
anchor = b'"WakeBridge",'
assert original.count(anchor) == 1, 'Expected exactly one app label'
app = 'dev.gianpaj.wakebridge'
adb = ['adb', '-s', args.serial]
agent_flags = ['--session', 'wakebridge-benchmark', '--platform', 'android', '--serial', args.serial]
build = ['./gradlew', ':androidApp:assembleDebug', '--console=plain']
rows, setup, restore = [], {}, {}


def run(command, binary=False):
    result = subprocess.run([str(x) for x in command], cwd=root, capture_output=True, timeout=300)
    if result.returncode:
        raise RuntimeError(f'{command}:\n{result.stdout.decode(errors="replace")}\n{result.stderr.decode(errors="replace")}')
    return result.stdout if binary else result.stdout.decode()


def agent(*command):
    return run(['agent-device', *command, *agent_flags])


def timed(record, stage, operation):
    start = time.perf_counter()
    try:
        return operation()
    finally:
        record[stage] = record.get(stage, 0) + time.perf_counter() - start
        print(f'{stage}: {record[stage]:.3f}s', flush=True)


# Both transports use the same OCR engine to validate pixels, not an accessibility tree.
ocr_source = out / 'ocr.swift'
ocr_source.write_text('''import Foundation
import Vision
let url = URL(fileURLWithPath: CommandLine.arguments[1])
let request = VNRecognizeTextRequest()
request.recognitionLevel = .accurate
request.usesLanguageCorrection = false
try VNImageRequestHandler(url: url).perform([request])
let lines = (request.results ?? []).compactMap { $0.topCandidates(1).first?.string }
print(lines.joined(separator: "\\n"))
''')
ocr = out / 'ocr'


def capture_and_validate(backend, png, marker, record):
    deadline = time.monotonic() + 30
    record['attempts'] = 0
    while True:
        record['attempts'] += 1
        timed(record, 'settle', lambda: time.sleep(0.25))
        timed(record, 'screenshot', lambda: png.write_bytes(run([*adb, 'exec-out', 'screencap', '-p'], binary=True))
              if backend == 'adb' else agent('screenshot', str(png), '--no-stabilize'))
        lines = timed(record, 'validate', lambda: run([ocr, png])).splitlines()
        if marker in lines:
            return
        png.rename(png.with_name(f'{png.stem}-miss-{record["attempts"]}.png'))
        if time.monotonic() >= deadline:
            raise RuntimeError(f'Expected {marker!r} was not visible within 30 seconds')

failure = None
stay_awake = run([*adb, 'shell', 'settings', 'get', 'global', 'stay_on_while_plugged_in']).strip()
try:
    run([*adb, 'shell', 'svc', 'power', 'stayon', 'true'])
    run([*adb, 'shell', 'input', 'keyevent', 'KEYCODE_WAKEUP'])
    timed(setup, 'compile_ocr', lambda: run(['xcrun', 'swiftc', ocr_source, '-o', ocr]))
    timed(setup, 'warm_build', lambda: run(build))
    timed(setup, 'warm_adb', lambda: run([*adb, 'get-state']))
    timed(setup, 'warm_agent', lambda: agent('open', app))
    warm_png = out / 'warmup.png'
    capture_and_validate('adb', warm_png, 'WakeBridge', setup)
    (out / 'versions.txt').write_text(run(['agent-device', '--version']) + run([*adb, 'version']))
    run_id = time.strftime('%H%M%S')
    for index, backend in enumerate(['adb', 'agent-device', 'agent-device', 'adb'], 1):
        marker = f'WakeBridge {"A" if backend == "adb" else "D"}{index} {run_id}'
        row = {'backend': backend, 'marker': marker}
        rows.append(row)
        print(f'\n{backend}: {marker}', flush=True)
        start = time.perf_counter()
        timed(row, 'edit', lambda: source.write_bytes(original.replace(anchor, f'"{marker}",'.encode())))
        build_log = timed(row, 'build', lambda: run(build))
        (out / f'{index}-build.log').write_text(build_log)
        timed(row, 'install', lambda: run([*adb, 'install', '-r', apk]) if backend == 'adb'
              else agent('install', app, str(apk)))
        def launch():
            if backend == 'adb':
                run([*adb, 'shell', 'am', 'force-stop', app])
                return run([*adb, 'shell', 'am', 'start', '-W', '-n', f'{app}/.MainActivity'])
            return agent('open', app, '--relaunch')
        timed(row, 'launch', launch)
        png = out / f'{index}-{backend}.png'
        capture_and_validate(backend, png, marker, row)
        row['passed'] = True
        row['total'] = time.perf_counter() - start
        row['device_loop'] = sum(row[k] for k in ('install', 'launch', 'settle', 'screenshot', 'validate'))
        print(f'TOTAL: {row["total"]:.3f}s; OCR PASS', flush=True)
except BaseException as exc:
    failure = repr(exc)
    raise
finally:
    source.write_bytes(original)
    try:
        timed(restore, 'build', lambda: run(build))
        timed(restore, 'install', lambda: run([*adb, 'install', '-r', apk]))
        timed(restore, 'close_agent', lambda: agent('close'))
        timed(restore, 'launch', lambda: run([*adb, 'shell', 'am', 'start', '-W', '-n', f'{app}/.MainActivity']))
        capture_and_validate('adb', out / 'restored.png', 'WakeBridge', restore)
    finally:
        run([*adb, 'shell', 'settings', 'delete' if stay_awake == 'null' else 'put',
             'global', 'stay_on_while_plugged_in', *([] if stay_awake == 'null' else [stay_awake])])
        (out / 'timings.json').write_text(json.dumps(dict(setup=setup, runs=rows, restore=restore, failure=failure), indent=2))

stages = ['edit', 'build', 'install', 'launch', 'settle', 'screenshot', 'validate', 'device_loop', 'total']
report = ['# ADB vs agent-device edit/build benchmark', '',
          f'Device: Pixel 6, serial `{args.serial}`. Times are wall-clock seconds.', '',
          'Two runs per backend, in ADB / agent-device / agent-device / ADB order.',
          'Gradle, OCR, and device sessions are warmed first. Each run edits the app label,',
          'builds an APK, installs without clearing data, and restarts the app.',
          'Both paths retry capture plus OCR until the label appears, pausing 0.25s per attempt. macOS Vision OCR',
          'check requires the exact unique label in each saved screenshot.', '',
          '| Stage | ADB 1 | Agent 1 | Agent 2 | ADB 2 | ADB mean | Agent mean |',
          '|---|---:|---:|---:|---:|---:|---:|']
for stage in stages:
    values = [r[stage] for r in rows]
    means = [statistics.mean(r[stage] for r in rows if r['backend'] == b) for b in ('adb', 'agent-device')]
    report.append('| ' + stage + ' | ' + ' | '.join(f'{v:.3f}' for v in values + means) + ' |')
report += ['', '## Evidence', '']
for i, row in enumerate(rows, 1):
    screenshot = f'{i}-{row["backend"]}.png'
    report.append(f'- `{row["marker"]}`: OCR passed. [Screenshot]({screenshot})')
report += ['', '## Scope and limitations', '',
           '- Process timings exclude assistant reasoning, tool dispatch, approval, and image-viewer overhead.',
           '- Two samples per backend are exploratory, not a statistically robust speed ranking.',
           '- Build and install variation can dominate capture savings. Device loop excludes editing and building.',
           '- agent-device uses `--no-stabilize`; readiness is confirmed by screenshot OCR for both paths.',
           '- Capture, validation, and settle times include every attempt; failed frames are retained.',
           '- This tests rebuild/capture workflows, not element-reference navigation or input speed.',
           '- Original source was restored, rebuilt, installed, and verified by OCR.',
           '- The plugged-in screen was kept awake during the run; its original setting was restored.', '',
           'Setup and restoration timings are in [timings.json](timings.json).', '']
(out / 'report.md').write_text('\n'.join(report))
print(f'\nReport: {out / "report.md"}', flush=True)
