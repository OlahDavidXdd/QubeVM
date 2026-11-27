#!/usr/bin/env bash
set -euo pipefail
mkdir -p smoke
adb install -r apk/*.apk
adb shell pm grant com.qubevm.main android.permission.POST_NOTIFICATIONS || true
adb logcat -c
adb shell am start -W -n com.qubevm.main/.QubeEmuActivity | tee smoke/launch.txt
sleep 30
adb shell pidof com.qubevm.main | tee smoke/pid.txt
adb logcat -d > smoke/logcat.txt
adb shell uiautomator dump /sdcard/window.xml
adb pull /sdcard/window.xml smoke/window.xml
adb exec-out screencap -p > smoke/screen.png
if grep -E 'FATAL EXCEPTION|UnsatisfiedLinkError|dlopen failed|Fatal signal' smoke/logcat.txt; then
  exit 1
fi
printf 'APK installed and process survived startup; guest boot is not asserted.\n'
if adb shell run-as com.qubevm.main test -f files/guest.iso; then
  # Find the actual Boot button through Android accessibility XML, including scrolling.
  for attempt in 1 2 3 4 5; do
    adb shell uiautomator dump /sdcard/window.xml >/dev/null
    adb pull /sdcard/window.xml smoke/boot-window.xml >/dev/null
    coordinates=$(python3 - <<'PY'
import xml.etree.ElementTree as E,re
for e in E.parse('smoke/boot-window.xml').iter('node'):
    if e.get('text','').startswith('Boot System'):
        x1,y1,x2,y2=map(int,re.findall(r'\d+',e.get('bounds')))
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
    if e.get('text')=='Boot':
        x1,y1,x2,y2=map(int,re.findall(r'\d+',e.get('bounds')))
        print((x1+x2)//2,(y1+y2)//2)
        break
PYCONFIRM
)
  if [ -n "$confirm" ]; then adb shell input tap $confirm; fi
  sleep 45
  adb exec-out screencap -p > smoke/guest-screen.png
  adb logcat -d > smoke/guest-logcat.txt
  adb shell pidof com.qubevm.main
  if grep -E 'FATAL EXCEPTION|UnsatisfiedLinkError|dlopen failed|Fatal signal' smoke/guest-logcat.txt; then exit 1; fi
  adb exec-out run-as com.qubevm.main cat files/guest-serial.log > smoke/guest-serial.log
  grep -q 'asm-llm OS' smoke/guest-serial.log
  grep -q 'Model.*Qwen3' smoke/guest-serial.log
  adb shell input text /think
  adb shell input keyevent 66
  sleep 5
  adb exec-out run-as com.qubevm.main cat files/guest-serial.log > smoke/guest-interactive.log
  grep -q '\[mode\] think=1' smoke/guest-interactive.log
  printf 'Boot button and real guest keyboard input exercised. Inspect guest-screen.png and logs for actual guest state.\n'
else
  printf 'Bundled guest media was not prepared.\n' >&2
  exit 1
fi
