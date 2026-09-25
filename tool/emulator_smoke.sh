#!/usr/bin/env bash
# Emulator smoke test: install the debug APK on the running emulator, launch it the way a new user
# would, then again with the Bluetooth permission already granted, and fail if the app crashes.
# Screenshots, logcat and the Bluetooth state land in OUT_DIR so a person can look at them.
#
# Usage: tool/emulator_smoke.sh <app-debug.apk> <out-dir>
set -euo pipefail

APK="$1"
OUT="$2"
PKG="com.backyardbrains.bybbackpack"
mkdir -p "$OUT"

sdk="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
echo "Emulator API level: $sdk"

shot() {
  sleep 5
  adb exec-out screencap -p > "$OUT/$1.png"
}

# Tap the first on-screen element whose UI-dump node matches $1 (e.g. 'text="Settings"').
# With HOLD_MS set, press and hold instead (a long-press).
tap_node() {
  local node bounds x y
  for _ in 1 2 3; do
    adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 || true
    node="$(adb shell cat /sdcard/ui.xml | tr '>' '\n' | grep -m1 -- "$1" || true)"
    if [ -n "$node" ]; then
      bounds="$(echo "$node" | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p')"
      set -- $bounds
      if [ "$#" -ne 4 ]; then sleep 2; continue; fi
      x=$(( ($1 + $3) / 2 )); y=$(( ($2 + $4) / 2 ))
      if [ -n "${HOLD_MS:-}" ]; then
        adb shell input swipe "$x" "$y" "$x" "$y" "$HOLD_MS"
      else
        adb shell input tap "$x" "$y"
      fi
      sleep 2
      return 0
    fi
    sleep 2
  done
  return 1
}

adb install -r "$APK"
adb logcat -c
adb logcat -b crash -c || true

# 1. First launch: a new user sees the permission prompt (or, on an emulator without Bluetooth,
#    the "Bluetooth Low Energy is required" message and the app closes on purpose).
adb shell am start -W -n "$PKG/.MainActivity"
shot 1-first-launch

# 2. Relaunch with the permission granted: what the user sees after tapping Allow.
#    -S stops the app first; NEW_TASK|CLEAR_TASK drops the leftover permission dialog from step 1,
#    which would otherwise swallow the launch.
if [ "$sdk" -ge 31 ]; then
  perms="android.permission.BLUETOOTH_SCAN android.permission.BLUETOOTH_CONNECT"
else
  perms="android.permission.ACCESS_FINE_LOCATION"
fi
for p in $perms; do
  adb shell pm grant "$PKG" "$p"
done
adb shell am start -W -S -f 0x10008000 -n "$PKG/.MainActivity"
shot 2-permission-granted
if ! adb shell pidof "$PKG" > /dev/null; then
  adb logcat -d > "$OUT/logcat.txt"
  echo "::error::BYB Backpack is not running after a launch with permission granted (API $sdk)"
  exit 1
fi

# 3. Settings are reachable before connecting.
tap_node 'text="Settings"' || { tap_node 'content-desc="More options"' && tap_node 'text="Settings"'; } \
  || { echo "::error::Could not open Settings (API $sdk)"; exit 1; }
shot 3-settings
adb shell input keyevent KEYCODE_BACK

# 4. Retro mode is hidden: long-press the "BYB Backpack" title.
HOLD_MS=1200 tap_node 'text="BYB Backpack"' || { echo "::error::Could not long-press the title (API $sdk)"; exit 1; }
shot 4-retro-mode
adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 || true
ui="$(adb shell cat /sdcard/ui.xml || true)"
if ! grep -q -e 'text="Find RoboRoach"' -e 'content-desc="RoboRoach"' <<< "$ui"; then
  echo "::error::Long-pressing the title did not turn on Retro mode (API $sdk)"
  exit 1
fi

# 5. Landscape layout (Retro mode is still on: the choice is saved).
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
shot 5-landscape
adb shell settings put system user_rotation 0

adb logcat -d > "$OUT/logcat.txt"
adb logcat -d -b crash > "$OUT/crash.txt" || true
adb shell dumpsys bluetooth_manager > "$OUT/bluetooth.txt" 2>&1 || true
adb shell pidof "$PKG" > "$OUT/pid.txt" 2>&1 || echo "not running" > "$OUT/pid.txt"

if grep -q "Process: $PKG" "$OUT/crash.txt" "$OUT/logcat.txt"; then
  echo "::error::BYB Backpack crashed on API $sdk"
  grep -A 30 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -60
  exit 1
fi
echo "No crash on API $sdk (process: $(cat "$OUT/pid.txt"))"
