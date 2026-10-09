"""Capture the running app and check its real record/export flow on an emulator."""
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE = "com.unknownamed.dishdry"
OUTPUT = Path("app/build/screenshots")
OUTPUT.mkdir(parents=True, exist_ok=True)


def adb(*args):
    return subprocess.check_output(["adb", *args], text=True).strip()


def hierarchy():
    adb("shell", "uiautomator", "dump", "/sdcard/dishdry-ui.xml")
    adb("pull", "/sdcard/dishdry-ui.xml", str(OUTPUT / "last-ui.xml"))
    return ET.parse(OUTPUT / "last-ui.xml").getroot()


def node(view_id):
    for element in hierarchy().iter("node"):
        if element.get("resource-id") == f"{PACKAGE}:id/{view_id}":
            return element
    raise AssertionError(f"View is not visible: {view_id}")


def tap(element):
    assert element.get("enabled") == "true", element.attrib
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", element.get("bounds")))
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(1)


def screenshot(name):
    with (OUTPUT / name).open("wb") as output:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=output, check=True)


def scroll_to_records():
    size = adb("shell", "wm", "size")
    width, height = map(int, re.findall(r"(\d+)x(\d+)", size)[-1])
    for _ in range(3):
        adb("shell", "input", "swipe", str(width // 2), str(height * 3 // 4),
            str(width // 2), str(height // 4), "350")
        time.sleep(0.5)


adb("install", "-r", "app/build/outputs/apk/debug/app-debug.apk")
adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity")
time.sleep(2)
assert node("toggle").get("text") == "측정 시작"
screenshot("01-overview.png")
tap(node("toggle"))
assert node("toggle").get("text") == "측정 중지"
screenshot("02-measuring.png")
scroll_to_records()
assert node("record_count").get("text") == "0개"
tap(node("save"))
assert node("record_count").get("text") == "1개"
screenshot("03-record-saved.png")
csv = adb("shell", "run-as", PACKAGE, "cat", "files/readings.csv")
assert len(csv.splitlines()) == 2, csv
(OUTPUT / "emulator-record.txt").write_text(csv + "\n", encoding="utf-8")
tap(node("export"))
screenshot("04-system-export.png")
buttons = [element for element in hierarchy().iter("node")
           if element.get("text", "").upper() in ("SAVE", "저장")
           and element.get("clickable") == "true"]
assert buttons, "System save button not found"
tap(buttons[-1])
assert node("record_count").get("text") == "1개"
assert node("export").get("enabled") == "true"
screenshot("05-export-return.png")
adb("shell", "am", "force-stop", PACKAGE)
adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity")
scroll_to_records()
assert node("record_count").get("text") == "1개"
assert node("save").get("enabled") == "false"
screenshot("06-records-reopened.png")
runtime = adb("logcat", "-d", "-s", "AndroidRuntime:E")
assert "FATAL EXCEPTION" not in runtime, runtime
(OUTPUT / "run-result.txt").write_text(
    "Android API 30 emulator\n"
    "ESP32 hardware: not connected\n"
    "App launch: passed\n"
    "Measurement start/stop UI: passed\n"
    "Virtual phone sensor record: passed\n"
    "CSV file picker and save return: passed\n"
    "Record persistence after restart: passed\n"
    "Runtime fatal exception: none\n", encoding="utf-8")
print((OUTPUT / "run-result.txt").read_text(encoding="utf-8"))
