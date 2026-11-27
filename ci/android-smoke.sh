#!/usr/bin/env bash
set -euo pipefail
mkdir -p smoke
capture_final() {
  adb logcat -d > smoke/final-logcat.txt || true
  adb exec-out screencap -p > smoke/final-screen.png || true
}
trap capture_final EXIT
adb install -r apk/*.apk
adb shell pm grant com.qubevm.main android.permission.POST_NOTIFICATIONS || true
# Limit this integration test to a complete two-token generation in the original OS.
# Normal app sessions keep the guest's unchanged default generation length.
adb shell run-as com.qubevm.main mkdir -p shared_prefs
adb shell "run-as com.qubevm.main sh -c 'cat > shared_prefs/llmos.xml'" <<'PREFS'
<?xml version="1.0" encoding="utf-8" standalone="yes" ?>
<map><string name="extra">-append max_tokens=2</string></map>
PREFS
adb shell svc wifi disable
adb shell svc data disable
adb shell settings put global airplane_mode_on 1
adb logcat -c
adb shell am start -W -n com.qubevm.main/.QubeEmuActivity | tee smoke/launch.txt
sleep 30
adb shell pidof com.qubevm.main | tee smoke/pid.txt
adb logcat -d > smoke/logcat.txt
adb shell uiautomator dump /sdcard/window.xml
adb pull /sdcard/window.xml smoke/window.xml
adb exec-out screencap -p > smoke/screen.png
grep -q 'libqemu-system-x86_64.so.*: ok' smoke/logcat.txt
if grep -E 'FATAL EXCEPTION|UnsatisfiedLinkError|dlopen failed|Fatal signal' smoke/logcat.txt | grep -v 'Load libqemu-system-i386.so.*not found'; then
  exit 1
fi
printf 'APK installed and process survived startup; guest boot is not asserted.\n'
if adb shell run-as com.qubevm.main test -f files/llmos/ready || adb shell run-as com.qubevm.main test -f files/guest.iso; then
  # Change both hardware controls through the actual UI before boot.
  ram_coordinates=$(python3 - <<'PYRAM'
import xml.etree.ElementTree as E,re
for e in E.parse('smoke/window.xml').iter('node'):
    if e.get('class')=='android.widget.EditText' and e.get('text')=='2048':
        x1,y1,x2,y2=map(int,re.findall(r'\d+',e.get('bounds')))
        print((x1+x2)//2,(y1+y2)//2)
        break
PYRAM
)
  test -n "$ram_coordinates"
  adb shell input tap $ram_coordinates
  adb shell input keyevent 123 67 67 67 67
  adb shell input text 1536
  adb shell input keyevent 4
  adb shell uiautomator dump /sdcard/window.xml >/dev/null
  adb pull /sdcard/window.xml smoke/hardware-window.xml >/dev/null
  cpu_coordinates=$(python3 - <<'PYCPU'
import xml.etree.ElementTree as E,re
for e in E.parse('smoke/hardware-window.xml').iter('node'):
    if e.get('text')=='1' and e.get('class','').endswith('TextView'):
        x1,y1,x2,y2=map(int,re.findall(r'\d+',e.get('bounds')))
        print((x1+x2)//2,(y1+y2)//2)
        break
PYCPU
)
  test -n "$cpu_coordinates"
  adb shell input tap $cpu_coordinates
  adb shell uiautomator dump /sdcard/window.xml >/dev/null
  adb pull /sdcard/window.xml smoke/cpu-options.xml >/dev/null
  two=$(python3 - <<'PYTWO'
import xml.etree.ElementTree as E,re
for e in E.parse('smoke/cpu-options.xml').iter('node'):
    if e.get('text')=='2':
        x1,y1,x2,y2=map(int,re.findall(r'\d+',e.get('bounds')))
        print((x1+x2)//2,(y1+y2)//2)
        break
PYTWO
)
  test -n "$two"
  adb shell input tap $two
  # Find the actual Boot button through Android accessibility XML, including scrolling.
  for attempt in 1 2 3 4 5; do
    adb shell uiautomator dump /sdcard/window.xml >/dev/null
    adb pull /sdcard/window.xml smoke/boot-window.xml >/dev/null
    coordinates=$(python3 - <<'PY'
import xml.etree.ElementTree as E,re
for e in E.parse('smoke/boot-window.xml').iter('node'):
    if e.get('text','').casefold().startswith('boot system'):
        x1,y1,x2,y2=map(int,re.findall(r'\d+',e.get('bounds')))
        if y2-y1 >= 40 and y1 >= 100 and y2 <= 1750:
            print((x1+x2)//2,(y1+y2)//2)
            break
PY
)
    if [ -n "$coordinates" ]; then
      adb shell input tap $coordinates
      break
    fi
    adb shell input swipe 400 1450 400 400 400
  done
  test -n "$coordinates"
  sleep 2
  adb shell uiautomator dump /sdcard/window.xml >/dev/null
  adb pull /sdcard/window.xml smoke/confirm-window.xml >/dev/null
  confirm=$(python3 - <<'PYCONFIRM'
import xml.etree.ElementTree as E,re
for e in E.parse('smoke/confirm-window.xml').iter('node'):
    if e.get('text','').casefold()=='boot':
        x1,y1,x2,y2=map(int,re.findall(r'\d+',e.get('bounds')))
        print((x1+x2)//2,(y1+y2)//2)
        break
PYCONFIRM
)
  if [ -n "$confirm" ]; then adb shell input tap $confirm; fi
  sleep 2
  adb shell uiautomator dump /sdcard/window.xml >/dev/null
  adb pull /sdcard/window.xml smoke/fullscreen-window.xml >/dev/null
  fullscreen=$(python3 - <<'PYFULL'
import xml.etree.ElementTree as E,re
for e in E.parse('smoke/fullscreen-window.xml').iter('node'):
    if e.get('text','').casefold()=='got it':
        x1,y1,x2,y2=map(int,re.findall(r'\d+',e.get('bounds')))
        print((x1+x2)//2,(y1+y2)//2)
        break
PYFULL
)
  if [ -n "$fullscreen" ]; then adb shell input tap $fullscreen; fi
  ready=false
  boot_started=$(date +%s)
  for attempt in $(seq 1 60); do
    adb exec-out run-as com.qubevm.main cat files/guest-serial.log > smoke/guest-serial.log
    if grep -q '\[FATAL\]' smoke/guest-serial.log; then cat smoke/guest-serial.log; exit 1; fi
    if grep -q 'Model.*Qwen3' smoke/guest-serial.log; then ready=true; break; fi
    sleep 10
  done
  adb exec-out screencap -p > smoke/boot-readiness-screen.png
  test "$ready" = true
  printf 'Guest model ready within %s seconds (10-second polling granularity).\n' "$(( $(date +%s) - boot_started ))" | tee smoke/boot-ready-time.txt
  adb exec-out screencap -p > smoke/guest-screen.png
  adb logcat -d > smoke/guest-logcat.txt
  adb shell pidof com.qubevm.main
  if grep -E 'FATAL EXCEPTION|UnsatisfiedLinkError|dlopen failed|Fatal signal' smoke/guest-logcat.txt | grep -v 'Load libqemu-system-i386.so.*not found'; then exit 1; fi
  adb exec-out run-as com.qubevm.main cat files/guest-serial.log > smoke/guest-serial.log
  grep -q 'asm-llm OS' smoke/guest-serial.log
  grep -q 'Model.*Qwen3' smoke/guest-serial.log
  grep -q 'SMP.*2/2' smoke/guest-serial.log
  grep -q 'RAM.*1535 MiB' smoke/guest-serial.log
  adb shell input text /think
  adb shell input keyevent 66
  sleep 5
  adb exec-out run-as com.qubevm.main cat files/guest-serial.log > smoke/guest-interactive.log
  grep -q '\[mode\] think=1' smoke/guest-interactive.log
  adb shell input text /nothink
  adb shell input keyevent 66
  sleep 1
  adb shell input text Hi
  adb shell input keyevent 66
  started=$(date +%s)
  generated=false
  for attempt in $(seq 1 60); do
    sleep 10
    adb exec-out run-as com.qubevm.main cat files/guest-serial.log > smoke/guest-inference.log
    if python3 - <<'PYINFERENCE'
import pathlib,sys
b=pathlib.Path('smoke/guest-inference.log').read_bytes()
assert not any(e in b for e in [b'[FATAL]',b'[EXCEPTION]',b'PANIC'])
marker=b'asm-llm> Hi'
if marker not in b: sys.exit(1)
output=b.split(marker,1)[1].lstrip(b'\r\n ')
sys.exit(0 if output and b'decode : 2 tok' in output else 1)
PYINFERENCE
    then
      generated=true
      break
    fi
  done
  test "$generated" = true
  printf 'Completed two-token inference observed within %s seconds (10-second polling granularity).\n' "$(( $(date +%s) - started ))" | tee smoke/first-output-time.txt
  adb exec-out screencap -p > smoke/inference-screen.png
  printf 'Boot button and real guest keyboard input exercised. Inspect guest-screen.png and logs for actual guest state.\n'
else
  printf 'Bundled guest media was not prepared.\n' >&2
  exit 1
fi
