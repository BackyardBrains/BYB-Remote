#!/usr/bin/env bash
# Emulator smoke test: install the debug APK on the running emulator, launch it the way a new user
# would, then again with the Bluetooth permission already granted, and fail if the app crashes.
# Screenshots, logcat and the Bluetooth state land in OUT_DIR so a person can look at them.
#
# Usage: tool/emulator_smoke.sh <app-debug.apk> <out-dir>
set -euo pipefail

APK="$1"
OUT="$2"
PKG="com.backyardbrains.bybremote"
mkdir -p "$OUT"

sdk="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
echo "Emulator API level: $sdk"

shot() {
  sleep 5
  adb exec-out screencap -p > "$OUT/$1.png"
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
  echo "::error::BYB Remote is not running after a launch with permission granted (API $sdk)"
  exit 1
fi

# 3. Landscape layout.
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
shot 3-landscape
adb shell settings put system user_rotation 0

adb logcat -d > "$OUT/logcat.txt"
adb logcat -d -b crash > "$OUT/crash.txt" || true
adb shell dumpsys bluetooth_manager > "$OUT/bluetooth.txt" 2>&1 || true
adb shell pidof "$PKG" > "$OUT/pid.txt" 2>&1 || echo "not running" > "$OUT/pid.txt"

if grep -q "Process: $PKG" "$OUT/crash.txt" "$OUT/logcat.txt"; then
  echo "::error::BYB Remote crashed on API $sdk"
  grep -A 30 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -60
  exit 1
fi
echo "No crash on API $sdk (process: $(cat "$OUT/pid.txt"))"
