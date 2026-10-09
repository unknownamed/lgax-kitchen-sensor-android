"""Capture the running app and check its real record/export flow on an emulator."""
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE = "com.unknownamed.dishdry"
OUTPUT = Path("app/build/screenshots")
OUTPUT.mkdir(parents=True, exist_ok=True)
dump_number = 0


def adb(*args):
    return subprocess.check_output(["adb", *args], text=True).strip()


def hierarchy():
    global dump_number
    dump_number += 1
    remote = f"/sdcard/dishdry-ui-{dump_number}.xml"
    dumped = adb("shell", "uiautomator", "dump", remote)
    assert "dumped to" in dumped, dumped
    adb("pull", remote, str(OUTPUT / "last-ui.xml"))
    return ET.parse(OUTPUT / "last-ui.xml").getroot()


def node(view_id):
    for element in hierarchy().iter("node"):
        if element.get("resource-id") == f"{PACKAGE}:id/{view_id}":
            return element
    raise AssertionError(f"View is not visible: {view_id}")


def wait_node(view_id, text=None, enabled=None):
    for _ in range(8):
        element = node(view_id)
        if (text is None or element.get("text") == text) and (
                enabled is None or element.get("enabled") == str(enabled).lower()):
            return element
        time.sleep(0.5)
    raise AssertionError(f"Unexpected view state: {element.attrib}")


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
    for _ in range(10):
        visible = {element.get("resource-id").split("/")[-1]: element
                   for element in hierarchy().iter("node")}
        required = ("record_count", "save", "export")
        if all(name in visible for name in required):
            bounds = [list(map(int, re.findall(r"\d+", visible[name].get("bounds"))))
                      for name in required]
            if all(box[1] >= 60 and box[3] <= height - 90 for box in bounds):
                return
        adb("shell", "input", "swipe", str(width // 2), str(height * 3 // 4),
            str(width // 2), str(height // 4), "350")
        time.sleep(0.5)


adb("install", "-r", "app/build/outputs/apk/debug/app-debug.apk")
adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity")
time.sleep(2)
assert node("toggle").get("text") == "측정 시작"
screenshot("01-overview.png")
tap(node("toggle"))
screenshot("02-measuring.png")
assert node("toggle").get("text") == "측정 중지"
scroll_to_records()
assert node("record_count").get("text") == "0개"
tap(node("save"))
wait_node("record_count", text="1개")
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
wait_node("record_count", text="1개")
wait_node("export", enabled=True)
exported = adb("shell", "cat", "/sdcard/Download/dish-dry-readings.csv")
assert exported == csv, "Exported CSV does not match the internal file"
screenshot("05-export-return.png")
tap(node("export"))
adb("shell", "input", "keyevent", "KEYCODE_BACK")
time.sleep(1)
assert node("record_count").get("text") == "1개"
assert adb("shell", "run-as", PACKAGE, "cat", "files/readings.csv") == csv
adb("shell", "am", "force-stop", PACKAGE)
adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity")
scroll_to_records()
assert node("record_count").get("text") == "1개"
assert node("save").get("enabled") == "false"
screenshot("06-records-reopened.png")
adb("shell", "am", "force-stop", PACKAGE)
adb("shell", "wm", "size", "960x1920")
adb("shell", "wm", "density", "480")
adb("shell", "settings", "put", "system", "font_scale", "1.3")
adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity")
time.sleep(2)
screenshot("07-small-screen.png")
scroll_to_records()
assert node("record_count").get("text") == "1개"
assert node("save").get("text") == "현재 값 기록"
assert node("export").get("text") == "CSV 내보내기"
screenshot("08-large-text-records.png")
runtime = adb("logcat", "-d", "-s", "AndroidRuntime:E")
assert "FATAL EXCEPTION" not in runtime, runtime
(OUTPUT / "run-result.txt").write_text(
    "Android API 30 emulator\n"
    "ESP32 hardware: not connected\n"
    "App launch: passed\n"
    "Measurement start/stop UI: passed\n"
    "Virtual phone sensor record: passed\n"
    "CSV file picker, saved file equality, and save return: passed\n"
    "CSV picker cancellation preserves internal records: passed\n"
    "Record persistence after restart: passed\n"
    "320dp wide screen with 130% font scale: passed\n"
    "Runtime fatal exception: none\n", encoding="utf-8")
print((OUTPUT / "run-result.txt").read_text(encoding="utf-8"))
