# BYB Backpack (Android) — Agent Operating Rules

BYB Backpack is a small native Java Android app that controls the Backyard Brains Bluetooth signal
generator. Keep it small and native: no framework rewrites, no new dependencies unless the task
needs them.

## Hard invariants — no exceptions

1. **The Bluetooth protocol is fixed by firmware already in people's hands.** Never change the
   advertised-name match, the service/characteristic UUIDs, or the byte encodings in
   `RemoteProtocol.java` unless a matching firmware change is linked. `RemoteProtocolTest` must
   stay green. Reference: `docs/PROTOCOL.md`.
2. **The application ID stays `com.backyardbrains.bybbackpack`.** A different ID is a different
   Google Play app. (The old `com.backyardbrains.bybremote` Play listing is tied to a signing key
   we no longer have, so it is not used.)
3. **The app is BYB Backpack** (the iOS counterpart is called BYB Remote). The default look is
   the board picture and "Find Signal Generator". The classic RoboRoach skin (roach and backpack
   pictures, "Find RoboRoach") is a switch in Settings, off by default and remembered between
   launches. Protocol names (for example `ROBOROACH_FREQUENCY` and the advertised device name) stay
   as the firmware defines them.
4. **`targetSdk` meets Google Play's current requirement** (API 36 since 2026-08-31; Google raises
   it every August). Raising it is its own PR with emulator smoke screenshots checked.
5. **One change = one PR. Surgical diffs.** No drive-by refactors, reformatting, or dependency
   bumps outside the task.
6. **Never commit build outputs or secrets.** No APK/AAB, keystores, or service-account JSON.
   The only binaries are app resources (`app/src/main/res`), the Play icon under `fastlane/`, and
   the Gradle wrapper jar (checksum-validated in CI).

## Working on an issue

- Issues are the queue and labels are the state (`needs-triage`, `needs-verification`,
  `human-input`, `platform:*`, `agent:*`). Anything not on GitHub did not happen.
- Branch `fix/<issue>-<slug>` (or `task/<slug>`) off `main`; push early. Commit messages are
  imperative summaries; reference the issue with `Refs #N`.
- Open the PR with the template. The **For testers (plain English)** section is required: one or
  two sentences a non-programmer understands, or exactly `Nothing to test.`
- CI must be green before merge. Branch protection on `main` requires the build and both
  emulator smoke checks.
- Nobody reviews their own work: a different agent or person reviews before merge.
- Anything that needs a real signal generator or phone goes to the testers as a
  `needs-verification` issue with numbered plain-English steps. Never to Greg.

## Who decides

- **Always OK, no one to ask:** fixes, reviews, merges with green CI, test builds (sideload APK,
  Play internal track).
- **Greg only:** production releases (Play production), anything that costs money, anything
  irreversible.
- Messages to Greg or testers are plain English: what changed and what to try. No commit hashes,
  PR numbers, file names or internal jargon.

## Useful commands

```bash
./gradlew lint testDebugUnitTest assembleDebug assembleRelease
./gradlew installDebug
bash tool/emulator_smoke.sh app/build/outputs/apk/debug/app-debug.apk smoke-out   # with an emulator running
```
