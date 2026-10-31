#!/usr/bin/env python3
"""Guarded, sequential calculator UI evidence on an explicitly owned emulator."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

p = argparse.ArgumentParser()
p.add_argument('--serial', required=True)
p.add_argument('--avd', required=True)
p.add_argument('--layout', choices=['tablet', 'phone'], required=True)
p.add_argument('--mode', choices=['baseline', 'negative', 'audit-negative', 'audit-focused', 'final'], required=True)
p.add_argument('--apk', type=Path, required=True)
p.add_argument('--test-apk', type=Path, required=True)
p.add_argument('--output', type=Path, required=True)
a = p.parse_args()
sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
if not sdk:
    sdk = next(line.removeprefix('sdk.dir=') for line in Path('local.properties').read_text().splitlines() if line.startswith('sdk.dir='))
adb = [str(Path(sdk) / 'platform-tools/adb'), '-s', a.serial]
pkg = 'dev.tinycast.android'
def call(*args, data=None):
    for attempt in range(3):
        result = subprocess.run(adb + list(args), input=data, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        if result.returncode == 0: return result.stdout
        if attempt == 2 or not any(word in result.stderr.lower() for word in (b'offline', b'closed', b'not found')):
            raise subprocess.CalledProcessError(result.returncode, result.args, result.stdout, result.stderr)
        # Retry only this idempotent operation on the same emulator. Never
        # restart an emulator or rerun instrumentation after a transport loss.
        subprocess.run(adb + ['wait-for-device'], check=True, timeout=15)
def text(*args): return call(*args).decode().strip()
def sha(data): return hashlib.sha256(data).hexdigest()
assert a.serial.startswith('emulator-')
assert text('shell', 'getprop', 'ro.kernel.qemu') == '1'
assert text('shell', 'getprop', 'ro.boot.qemu.avd_name') == a.avd
assert text('shell', 'id').startswith('uid=2000')
assert 'Override' not in text('shell', 'wm', 'size')
assert 'Override' not in text('shell', 'wm', 'density')
instrumentation = text('shell', 'dumpsys', 'activity', 'processes')
assert 'Unknown command' not in instrumentation, 'Cannot verify instrumentation state'
assert 'ActiveInstrumentation{' not in instrumentation, 'Finish instrumentation before calculator trials'
settings = {k: text('shell', 'settings', 'get', 'secure', k) for k in
            ('enabled_accessibility_services', 'accessibility_enabled', 'show_ime_with_hard_keyboard')}
assert settings['enabled_accessibility_services'] == 'null'
assert settings['accessibility_enabled'] in ('0', 'null')
assert not a.output.exists(), 'Use a fresh evidence directory; never mix failed and final captures'
a.output.mkdir(parents=True)
original_prefs = call('exec-out', 'run-as', pkg, 'cat', 'shared_prefs/palette.xml')
# Private, ignored recovery data: never included in manifests or PR assets.
backup = a.output / 'private-palette-backup.xml'
backup.write_bytes(original_prefs)
backup.chmod(0o600)
record = {'mode': a.mode, 'layout': a.layout, 'serial': a.serial, 'avd': a.avd,
          'api': text('shell', 'getprop', 'ro.build.version.sdk'), 'abi': text('shell', 'getprop', 'ro.product.cpu.abi'),
          'apk_sha256': sha(a.apk.read_bytes()), 'test_apk_sha256': sha(a.test_apk.read_bytes()), 'completed': False}
try:
    for source in (a.apk, a.test_apk):
        shutil.copy2(source, a.output / source.name)
        call('install', '-r', str(source))
    installed = text('shell', 'pm', 'path', pkg).removeprefix('package:')
    record['installed_sha256'] = sha(call('exec-out', 'cat', installed))
    assert record['installed_sha256'] == record['apk_sha256']
    if a.layout == 'phone':
        call('shell', 'wm', 'size', '1080x2340')
        call('shell', 'wm', 'density', '420')
    record['size'] = text('shell', 'wm', 'size')
    record['density'] = text('shell', 'wm', 'density')
    call('shell', 'run-as', pkg, 'rm', '-rf', 'files/calculator-evidence')
    prefix = 'dev.tinycast.android.'
    if a.mode == 'baseline':
        classes = prefix + 'CalculatorBaselineDeviceTest'
        expected = 1
    elif a.mode == 'negative':
        classes = ','.join(prefix + 'CalculatorIntentDeviceTest#' + method for method in (
            'bareNumbersRemainSearchableAndCanFindSavedItems', 'multiplicationVariantsShowTimesSignAndCopyOnlyTheNumber'))
        expected = 2
    elif a.mode in ('audit-negative', 'audit-focused'):
        classes = prefix + 'CalculatorIntentDeviceTest#constantOperandsSupportXInAutomaticAndExplicitCalculatorModes'
        expected = 1
    else:
        classes = ','.join((prefix + 'CalculatorIntentDeviceTest',
            prefix + 'PaletteDeviceTest#calculatorResultCopiesToSystemClipboard',
            prefix + 'PaletteDeviceTest#conversionShowsAccurateResult',
            prefix + 'ShortcutDeviceTest#calculatorShortcutTogglesItsExistingPaletteMode',
            prefix + 'PaletteOverlayTest#typingAndSelectionUpdateWithoutAnimatedScrollOrMovingSearchFrame',
            prefix + 'EmojiDeviceTest#typingAndFourWayNavigationWorkImmediatelyWithoutTab'))
        expected = 14
    command = adb + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'dedicatedAvd', a.avd,
        '-e', 'calculatorBaseline', str(a.mode == 'baseline').lower(),
        '-e', 'calculatorConstantNegative', str(a.mode == 'audit-negative').lower(), '-e', 'class', classes,
        pkg + '.test/androidx.test.runner.AndroidJUnitRunner']
    with (a.output / 'tests.log').open('wb') as log:
        subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, check=True)
    names = text('shell', 'run-as', pkg, 'ls', 'files/calculator-evidence').splitlines()
    for name in names:
        assert re.fullmatch(r'[a-z0-9-]+\.(png|xml|json)', name), name
        remote = 'files/calculator-evidence/' + name
        expected_size = int(text('shell', 'run-as', pkg, 'stat', '-c', '%s', remote))
        for attempt in range(3):
            raw = call('exec-out', 'run-as', pkg, 'cat', remote)
            if len(raw) == expected_size: break
        assert len(raw) == expected_size, 'Incomplete artifact transfer: ' + name
        (a.output / name).write_bytes(raw)
    log = (a.output / 'tests.log').read_text()
    statuses = [int(n) for n in re.findall(r'^INSTRUMENTATION_STATUS_CODE: (-?\d+)$', log, re.M)]
    record['passes'] = statuses.count(0)
    record['failures'] = statuses.count(-2)
    assert statuses.count(1) == expected, statuses
    if a.mode in ('negative', 'audit-negative'):
        assert f'Tests run: {expected},  Failures: {expected}' in log and record['failures'] == expected
    else:
        assert f'OK ({expected} test' in log and 'FAILURES!!!' not in log
        assert record['passes'] == expected and record['failures'] == 0
    record['completed'] = True
    print(log[-1800:], flush=True)
finally:
    (a.output / 'record.json').write_text(json.dumps(record, indent=2) + '\n')
    call('shell', 'am', 'force-stop', pkg)
    # Keep private preference bytes in memory only; no private values enter evidence.
    call('shell', 'run-as', pkg, 'tee', 'shared_prefs/palette.xml', data=original_prefs)
    record['palette_preferences_restored'] = call('exec-out', 'run-as', pkg, 'cat', 'shared_prefs/palette.xml') == original_prefs
    if a.layout == 'phone':
        call('shell', 'wm', 'size', 'reset')
        call('shell', 'wm', 'density', 'reset')
    restored = {k: text('shell', 'settings', 'get', 'secure', k) for k in settings}
    record['settings_unchanged'] = restored == settings
    record['restored_size'] = text('shell', 'wm', 'size')
    record['restored_density'] = text('shell', 'wm', 'density')
    record['adbd'] = text('shell', 'id').split(' groups=')[0]
    record['app_uid'] = text('shell', 'run-as', pkg, 'id').split(' groups=')[0]
    (a.output / 'record.json').write_text(json.dumps(record, indent=2) + '\n')
    assert record['palette_preferences_restored'] and record['settings_unchanged']
    assert record['adbd'].startswith('uid=2000')
