#!/usr/bin/env python3
"""Reopen the extended-test documents after a real app process stop."""
import argparse
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("--adb", default="adb")
parser.add_argument("--serial", default="emulator-5580")
args = parser.parse_args()
base = [args.adb, "-s", args.serial]
output = Path(__file__).resolve().parents[1] / "artifacts" / "extended-test"
output.mkdir(parents=True, exist_ok=True)


def adb(*arguments):
    return subprocess.check_output(base + list(arguments), text=True, stderr=subprocess.STDOUT)


def tree():
    adb("shell", "uiautomator", "dump", "/sdcard/tinycast-restart.xml")
    return ET.fromstring(adb("shell", "cat", "/sdcard/tinycast-restart.xml"))


def tap(node):
    x, y, right, bottom = map(int, re.findall(r"\d+", node.get("bounds")))
    adb("shell", "input", "tap", str((x + right) // 2), str((y + bottom) // 2))


def wait_focus(package):
    deadline = time.monotonic() + 10
    while time.monotonic() < deadline:
        focus = next((line for line in adb("shell", "dumpsys", "window").splitlines() if "mCurrentFocus=" in line), "")
        if package + "/" in focus:
            return
        time.sleep(.2)
    raise AssertionError("Destination did not receive focus: " + package)


assert adb("emu", "avd", "name").splitlines()[0] == "Tinycast_Tablet_API_36"
results = []
for filename, destination in [
    ("tinycast-test-image.png", "com.google.android.apps.photos"),
    ("tinycast-soak-document.pdf", "com.google.android.apps.docs"),
    ("tinycast-soak-document.txt", "com.android.htmlviewer"),
]:
    adb("shell", "am", "force-stop", destination)
    adb("shell", "am", "force-stop", "dev.tinycast.android")
    adb("shell", "am", "start", "-W", "-n", "dev.tinycast.android/.MainActivity")
    assert any(n.get("class") == "android.widget.EditText" for n in tree().iter("node"))
    adb("shell", "input", "text", filename)
    assert any(n.get("text") == filename for n in tree().iter("node")), "Recent file did not survive restart"
    adb("shell", "input", "keyevent", "66")
    if filename.endswith(".txt"):
        nodes = list(tree().iter("node"))
        if any(n.get("text", "").startswith("Open with") for n in nodes):
            choice = next((n for n in nodes if n.get("text") == "HTML Viewer"), None)
            if choice is not None:
                tap(choice)
                nodes = list(tree().iter("node"))
            once = next(n for n in nodes if n.get("text") == "Just once")
            tap(once)
    wait_focus(destination)
    visible = tree()
    assert any(n.get("package") == destination for n in visible.iter("node"))
    name = filename.rsplit(".", 1)[1]
    adb("shell", "screencap", "-p", f"/sdcard/restart-{name}.png")
    adb("pull", f"/sdcard/restart-{name}.png", str(output / f"restart-{name}.png"))
    results.append({"file": filename, "viewer": destination, "passed_after_process_stop": True, "viewer_also_restarted": True})
    (output / "restart-workflows.json").write_text(json.dumps(results, indent=2) + "\n")
print(json.dumps(results, indent=2))
