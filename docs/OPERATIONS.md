# How BYB Backpack runs

BYB Backpack uses the same repo system as SpikerBot, scaled down for a small app. The GitHub repo is
the operating system: issues are the queue, labels are the state, PRs are the work, and CI plus a
reviewer are the gate.

## What happens automatically

- **Every PR and every push to main** runs CI: Android lint, unit tests (the Bluetooth protocol
  lock), debug and release builds, a launch smoke test on Android 11 and Android 16 emulators
  (screenshots and logs kept as artifacts), and actionlint. Branch protection on `main` requires
  the build and both emulator checks before a PR can merge.
- New bug reports from the form arrive labelled `bug`, `needs-triage` and `platform:android`.

## What someone starts by hand

- **Test builds** (sideload APK or Play internal track) are always OK to start; no approval needed.
  See `docs/RELEASING.md`.
- **Production releases** are Greg's call.

## Who is involved

| Situation | Who |
|---|---|
| Triage, fixes, review, merges with green CI, test builds | agents and maintainers; no one to ask |
| Anything needing a real signal generator or phone | testers, through a `needs-verification` issue with numbered steps |
| Play production release, money, anything irreversible | Greg |

## Not wired up yet

SpikerBot's always-on pieces (the linux-orchestrator drain that picks up issues and merges PRs,
the Jev issue triage workflow, the mac-builder test-build autopilot, and the SpikerBot Door chat
front door) are not connected to this repo. Until they are, issues are worked in agent sessions
started by hand.
