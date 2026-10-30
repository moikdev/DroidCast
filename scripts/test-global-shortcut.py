#!/usr/bin/env python3
"""Test the opt-in service on the dedicated emulator, outside UiAutomation.

The app stays unprivileged. Root adbd is used only to inject events through the
emulator's Linux keyboard device; adb input events bypass the accessibility filter.
"""
import argparse
import json
import os
import re
import subprocess
import time
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("--serial", default="emulator-5580")
parser.add_argument("--adb", default=os.environ.get("ADB", "adb"))
parser.add_argument("--rounds", type=int, default=1)
args = parser.parse_args()
assert 1 <= args.rounds <= 50, "Use 1 to 50 rounds"
base = [args.adb, "-s", args.serial]

def adb(*arguments):
    return subprocess.check_output(base + list(arguments), text=True, stderr=subprocess.STDOUT).strip()

def wait_for(check, message, timeout=10):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if check():
            return
        time.sleep(.2)
    raise AssertionError(message)

def focused():
    text = adb("shell", "dumpsys", "window")
    return next((s.strip() for s in text.splitlines() if "mCurrentFocus=" in s), "")

assert adb("shell", "getprop", "ro.kernel.qemu") == "1", "Use an emulator"
assert adb("emu", "avd", "name").splitlines()[0] == "Tinycast_Tablet_API_36", "Use the dedicated Tinycast test AVD"
adb("root")
adb("wait-for-device")
adb("shell", "settings", "delete", "secure", "enabled_accessibility_services")
adb("shell", "am", "force-stop", "dev.tinycast.android")
adb("shell", "am", "start", "-W", "-n", "dev.tinycast.android/.MainActivity")
adb("shell", "settings", "put", "secure", "enabled_accessibility_services", "dev.tinycast.android/dev.tinycast.android.PaletteShortcutService")
adb("shell", "settings", "put", "secure", "accessibility_enabled", "1")
wait_for(lambda: "Bound services:{Service[label=DroidCast" in adb("shell", "dumpsys", "accessibility"), "Shortcut service did not bind")
devices = adb("shell", "getevent", "-pl")
keyboard = re.search(r"add device \d+: (/dev/input/event\d+)\s+name:\s+\"qwerty2\"", devices).group(1)

def chord():
    events = [(1, 29, 1), (0, 0, 0), (1, 57, 1), (0, 0, 0), (1, 57, 0), (1, 29, 0), (0, 0, 0)]
    adb("shell", "; ".join(f"sendevent {keyboard} {t} {c} {v}" for t, c, v in events))

results = {"device": args.serial, "app_requires_root": False, "event_injection": "Linux keyboard device on dedicated emulator"}
trials = []
for round_index in range(args.rounds):
    for label, target in [("settings", ["shell", "am", "start", "-W", "-a", "android.settings.SETTINGS"]),
                          ("home", ["shell", "input", "keyevent", "KEYCODE_HOME"])]:
        adb(*target)
        wait_for(lambda: "dev.tinycast.android/" not in focused(), "Could not leave Tinycast")
        start = time.monotonic()
        chord()
        wait_for(lambda: "dev.tinycast.android/" in focused(), "Ctrl + Space did not summon Tinycast")
        outcome = {"passed": True, "observed_seconds": round(time.monotonic() - start, 3)}
        results[f"from_{label}"] = outcome
        trials.append({"round": round_index + 1, "from": label, **outcome})
results["trials"] = trials

results["app_process"] = adb("shell", "ps", "-A", "-o", "USER,NAME").splitlines()
results["app_process"] = [line for line in results["app_process"] if line.endswith("dev.tinycast.android")]
output = Path(__file__).resolve().parents[1] / "artifacts" / "global-shortcut-test.json"
output.write_text(json.dumps(results, indent=2) + "\n")
print(json.dumps(results, indent=2))
