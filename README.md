# BYB Remote for Android

Bluetooth remote control for the Backyard Brains signal generator: find the nearest one, swipe
left or right to send a pulse train, and set frequency, pulse width, duration, gain and random
mode. Package `com.backyardbrains.bybremote`. It matches the iOS app of the same name
([App Store](https://apps.apple.com/us/app/byb-remote/id792968848)), whose source lives in
[BackyardBrains/RoboRoach](https://github.com/BackyardBrains/RoboRoach) under `Software/iOS`.

This repo started from the RoboRoach Android 1.2 source (2018, the first commit here) and was
rebuilt for current phones: targets Android 16 (API 36, Google Play's requirement), runs on
Android 6.0 and newer, and uses the modern Bluetooth permissions.

## Build

Requirements: JDK 17 and the Android SDK (Android Studio installs both).

```bash
./gradlew lint testDebugUnitTest assembleDebug   # what CI runs, plus the release build
./gradlew installDebug                            # onto a connected phone
```

A real signal generator is needed to test anything past the scan; emulators have no BLE peripheral.

## How this repo runs

| What | Where |
|---|---|
| Rules for agents and contributors | [`CLAUDE.md`](CLAUDE.md) |
| Who does what, what is automated | [`docs/OPERATIONS.md`](docs/OPERATIONS.md) |
| Test builds and store releases | [`docs/RELEASING.md`](docs/RELEASING.md) |
| Bluetooth protocol (fixed by firmware) | [`docs/PROTOCOL.md`](docs/PROTOCOL.md) |
| CI | [`.github/workflows/ci.yml`](.github/workflows/ci.yml): lint, unit tests, debug + release builds, launch smoke test on Android 11 and 16 emulators |

Bugs: open an issue with the bug report form. Testers retest with a real signal generator.
