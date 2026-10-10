"""Capture actual app rendering using explicitly labeled debug-only sample data."""
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE = "com.unknownamed.dishdry"
OUTPUT = Path("app/build/screenshots")
OUTPUT.mkdir(parents=True, exist_ok=True)
EXAMPLE_FILE = "example-readings.csv"
EXPORT_FILE = "dish-dry-example-readings.csv"
dump_number = 0


def adb(*args):
    return subprocess.check_output(["adb", *args], text=True).strip()


def hierarchy():
    global dump_number
    dump_number += 1
    remote = f"/sdcard/dishdry-ui-{dump_number}.xml"
    assert "dumped to" in adb("shell", "uiautomator", "dump", remote)
    adb("pull", remote, str(OUTPUT / "last-ui.xml"))
    return ET.parse(OUTPUT / "last-ui.xml").getroot()


def node(view_id):
    for element in hierarchy().iter("node"):
        if element.get("resource-id") == f"{PACKAGE}:id/{view_id}":
            return element
    raise AssertionError(f"View is not visible: {view_id}")


def wait_node(view_id, text=None, enabled=None):
    for _ in range(10):
        element = node(view_id)
        if (text is None or element.get("text") == text) and (
                enabled is None or element.get("enabled") == str(enabled).lower()):
            return element
        time.sleep(0.4)
    raise AssertionError(f"Unexpected view state: {element.attrib}")


def tap(element):
    assert element.get("enabled") == "true", element.attrib
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", element.get("bounds")))
    adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
    time.sleep(0.7)


def screenshot(name):
    target = OUTPUT / name
    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open("wb") as output:
        subprocess.run(["adb", "exec-out", "screencap", "-p"],
                       stdout=output, check=True)


def screen_size():
    return tuple(map(int, re.findall(r"(\d+)x(\d+)", adb("shell", "wm", "size"))[-1]))


def scroll_to_views(required):
    width, height = screen_size()
    for _ in range(35):
        visible = {n.get("resource-id", "").split("/")[-1]: n
                   for n in hierarchy().iter("node")}
        viewport = list(map(int, re.findall(r"\d+", visible["root"].get("bounds"))))
        if all(name in visible for name in required):
            boxes = [list(map(int, re.findall(r"\d+", visible[name].get("bounds"))))
                     for name in required]
            if all(box[1] >= viewport[1]+24 and box[3] <= viewport[3]-24 for box in boxes):
                return
        adb("shell", "input", "swipe", str(width//2), str(height*3//4),
            str(width//2), str(height*7//12), "500")
        time.sleep(0.3)
    raise AssertionError(f"Could not show the complete controls: {required}")


def scroll_to_top():
    width, height = screen_size()
    for _ in range(7):
        adb("shell", "input", "swipe", str(width//2), str(height//4),
            str(width//2), str(height*3//4), "350")
    time.sleep(0.4)


def scroll_to_records():
    scroll_to_views(("record_count", "save", "export"))


def launch(example=False):
    adb("shell", "am", "force-stop", PACKAGE)
    args = ["shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity"]
    if example:
        args += ["--ez", "example_preview", "true"]
    adb(*args)
    time.sleep(1)


def start_example():
    scroll_to_views(("toggle",))
    assert node("toggle").get("text") == "측정 시작"
    tap(node("toggle"))
    wait_node("toggle", text="측정 중지")


def verify_download_examples(csv):
    rows = csv.splitlines()
    assert len(rows) == 4
    for index, row in enumerate(rows[1:], 1):
        name = f"dish-dry-example-0{index}.csv"
        body = adb("shell", "cat", f"/sdcard/Download/{name}")
        sample_rows = body.splitlines()
        assert len(sample_rows) == 2 and sample_rows[0] == rows[0], body
        assert sample_rows[1].split(",")[1:] == row.split(",")[1:], body


assert adb("shell", "getprop", "ro.kernel.qemu") == "1", "Use a fresh emulator only"
adb("install", "-r", "app/build/outputs/apk/debug/app-debug.apk")

# The ordinary launch must keep real measurement mode and its CSV separate.
launch()
scroll_to_views(("toggle", "connection_badge"))
assert node("connection_badge").get("text") == "대기"
assert not any(n.get("resource-id") == f"{PACKAGE}:id/example_notice"
               for n in hierarchy().iter("node"))
screenshot("checks/normal-mode.png")

launch(example=True)
assert node("example_notice").get("text") == "샘플 데이터"
start_example()
scroll_to_top()
wait_node("drying", text="57")
screenshot("01-overview.png")
scroll_to_views(("toggle", "connection_badge"))
screenshot("02-measuring.png")
scroll_to_views(("temperature", "humidity", "ldr", "light", "motion"))
assert node("temperature").get("text") == "25.0"
assert node("humidity").get("text") == "50.0"
assert node("ldr").get("text") == "1000"
assert node("light").get("text") == "320.0"
assert node("motion").get("text") == "0.12"
screenshot("09-sensors.png")
scroll_to_records()
assert node("example_record_notice").get("text") == "샘플 기록"
assert node("record_count").get("text") == "0개"
for count in range(1, 4):
    tap(node("save"))
    wait_node("record_count", text=f"{count}개")
screenshot("03-record-saved.png")
csv = adb("shell", "run-as", PACKAGE, "cat", f"files/{EXAMPLE_FILE}")
assert len(csv.splitlines()) == 4
(OUTPUT / "emulator-record.txt").write_text(csv+"\n", encoding="utf-8")
verify_download_examples(csv)

tap(node("export"))
buttons = []
for _ in range(12):
    tree = hierarchy()
    buttons = [n for n in tree.iter("node")
               if n.get("text", "").upper() in ("SAVE", "저장") and n.get("clickable") == "true"]
    if buttons:
        names = [n.get("text", "") for n in tree.iter("node")]
        if all(f"dish-dry-example-0{i}.csv" in names for i in range(1, 4)):
            break
    time.sleep(0.4)
assert buttons, "System save button not found"
assert all(f"dish-dry-example-0{i}.csv" in names for i in range(1, 4)), names
# List view keeps all three filenames readable in the original capture.
list_switches = [n for n in tree.iter("node")
                 if "list" in n.get("content-desc", "").lower()
                 and n.get("clickable") == "true"]
if list_switches:
    tap(list_switches[0])
else:
    width, height = screen_size()
    adb("shell", "input", "tap", str(width*91//100), str(height*23//100))
    time.sleep(0.5)
tree = hierarchy()
assert all(any(n.get("text") == f"dish-dry-example-0{i}.csv" for n in tree.iter("node"))
           for i in range(1, 4))
screenshot("04-system-export.png")
tap(buttons[-1])
wait_node("record_count", text="3개")
wait_node("export", enabled=True)
assert adb("shell", "cat", f"/sdcard/Download/{EXPORT_FILE}") == csv
screenshot("05-export-return.png")

tap(node("export"))
adb("shell", "input", "keyevent", "KEYCODE_BACK")
time.sleep(0.7)
assert node("record_count").get("text") == "3개"
assert adb("shell", "run-as", PACKAGE, "cat", f"files/{EXAMPLE_FILE}") == csv
launch(example=True)
scroll_to_records()
assert node("record_count").get("text") == "3개"
assert node("save").get("enabled") == "false"
screenshot("06-records-reopened.png")

adb("shell", "am", "force-stop", PACKAGE)
adb("shell", "wm", "size", "960x1920")
adb("shell", "wm", "density", "480")
adb("shell", "settings", "put", "system", "font_scale", "1.3")
launch(example=True)
start_example()
scroll_to_top()
screenshot("07-small-screen.png")
scroll_to_records()
assert node("record_count").get("text") == "3개"
assert node("save").get("text") == "현재 값 기록"
assert node("export").get("text") == "CSV 내보내기"
screenshot("08-large-text-records.png")
launch()
scroll_to_records()
assert node("record_count").get("text") == "0개"
assert node("save").get("enabled") == "false"
assert adb("shell", "run-as", PACKAGE, "ls", "files") == EXAMPLE_FILE
screenshot("checks/normal-records.png")
runtime = adb("logcat", "-d", "-s", "AndroidRuntime:E")
assert "FATAL EXCEPTION" not in runtime, runtime
result = (
    "Android API 30 emulator\n"
    "Data source: explicitly labeled debug example preview\n"
    "ESP32 hardware: not connected\n"
    "Ordinary launch remains real sensor mode: passed\n"
    "Product illustration and example values 25/50/1000/320/0.12/index 57: passed\n"
    "Three example CSV records created through app buttons: passed\n"
    "Downloads contains three labeled example CSV files: passed\n"
    "CSV saved file equality and cancellation: passed\n"
    "Example record persistence after restart: passed\n"
    "Normal and example CSV storage isolation: passed\n"
    "320dp screen with 130% font scale: passed\n"
    "Runtime fatal exception: none\n"
)
(OUTPUT / "run-result.txt").write_text(result, encoding="utf-8")
print(result)
