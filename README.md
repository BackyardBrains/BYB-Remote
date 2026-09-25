# BYB Backpack (Android)

BYB Backpack is the Bluetooth remote for the Backyard Brains RoboRoach backpack (a tiny signal
generator): find the nearest one, swipe left or right to send a pulse train, and set frequency,
pulse width, duration, gain and random mode. Long-pressing the **BYB Backpack** title toggles
Retro mode, the classic roach-and-backpack look. Package `com.backyardbrains.bybbackpack`.

The iOS counterpart is called BYB Remote
([App Store](https://apps.apple.com/us/app/byb-remote/id792968848)); its source lives in
[BackyardBrains/RoboRoach](https://github.com/BackyardBrains/RoboRoach) under `Software/iOS`.

This repo started from the RoboRoach Android 1.2 source (2018, the first commit here) and was
rebuilt for current phones: targets Android 16 (API 36, Google Play's requirement), runs on
Android 7.0 and newer (Play automatic protection needs API 24), and uses the modern Bluetooth
permissions.

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
| Merging | [`.github/workflows/auto-merge.yml`](.github/workflows/auto-merge.yml): once CI passes, Jev decides; routine PRs merge themselves, the rest get `needs-review` |

Bugs: open an issue with the bug report form. Testers retest with a real signal generator.
