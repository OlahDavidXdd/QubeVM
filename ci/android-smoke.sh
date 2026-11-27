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
if rg 'FATAL EXCEPTION|UnsatisfiedLinkError|dlopen failed|Fatal signal' smoke/logcat.txt; then
  exit 1
fi
printf 'APK installed and process survived startup; guest boot is not asserted.\n'
