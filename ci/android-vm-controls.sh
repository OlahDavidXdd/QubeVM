#!/usr/bin/env bash
set -euo pipefail
# Runs after the original guest has completed inference in the same emulator.
adb shell am start -W --activity-clear-top -n com.qubevm.main/.QubeEmuActivity
python3 ci/android-tap.py 'restart system'
restarted=false
for attempt in $(seq 1 30); do
  sleep 5
  adb exec-out run-as com.qubevm.main cat files/guest-serial.log > smoke/restart-serial.log
  count=$(grep -c 'Model.*Qwen3' smoke/restart-serial.log || true)
  if [ "$count" -ge 2 ]; then restarted=true; break; fi
done
test "$restarted" = true
python3 ci/android-tap.py 'stop system'
stopped=false
for attempt in $(seq 1 20); do
  sleep 1
  if ! adb shell pidof com.qubevm.main; then stopped=true; break; fi
done
test "$stopped" = true
adb logcat -d > smoke/stop-logcat.txt
grep -q 'Exiting Qube' smoke/stop-logcat.txt
# QubeVM intentionally exits its process on stop to release native global state.
adb shell run-as com.qubevm.main rm files/guest-serial.log
adb shell am start -W --activity-clear-top -n com.qubevm.main/.QubeEmuActivity
sleep 5
adb shell uiautomator dump /sdcard/persisted.xml
adb pull /sdcard/persisted.xml smoke/persisted-window.xml
python3 - <<'PY'
import xml.etree.ElementTree as E
texts=[n.get('text') for n in E.parse('smoke/persisted-window.xml').iter('node')]
assert '1536' in texts and '2' in texts, 'Hardware configuration did not persist'
PY
python3 ci/android-tap.py 'boot system'
recovered=false
for attempt in $(seq 1 30); do
  sleep 5
  adb exec-out run-as com.qubevm.main cat files/guest-serial.log > smoke/recovery-serial.log
  if grep -q 'Model.*Qwen3' smoke/recovery-serial.log; then recovered=true; break; fi
done
test "$recovered" = true
grep -q 'SMP.*2/2' smoke/recovery-serial.log
grep -q 'RAM.*1535 MiB' smoke/recovery-serial.log
adb exec-out screencap -p > smoke/recovery-screen.png
printf 'Warm restart, clean stop, persisted hardware, and new-session boot verified.\n'
