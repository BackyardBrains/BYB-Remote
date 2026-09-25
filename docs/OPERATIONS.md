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
- **Merges decide themselves.** When CI passes on a PR, the "Auto-merge (Jev)" workflow runs
  `tool/automerge_gate.mjs`. Code checks the hard rules first: same repo, author has write access,
  not a draft, no `hold` label, CI passed on the exact head. Then Jev answers three yes/no
  questions: would a careful maintainer approve it; does it change the Bluetooth protocol, weaken
  tests or CI, add a secret, or change the app ID; does the diff match the description. It merges
  when approve ≥ 0.7, danger ≤ 0.2 and matches ≥ 0.6. Otherwise it labels the PR `needs-review`
  and comments with the scores. PRs that change the gate or `CLAUDE.md` always go to review. If
  Jev is unreachable, nothing merges. To re-run the gate, start the workflow by hand with the PR
  number. Calibration on 2026-09-24: routine PRs scored approve 0.75–0.93 and danger ≤ 0.07;
  synthetic bad PRs (UUID change, deleted emulator tests, hidden app-ID change, hard-coded
  password) scored approve ≤ 0.10 and danger ≥ 0.97.

## What someone starts by hand

- **Test builds** (sideload APK or Play internal track) are always OK to start; no approval needed.
  See `docs/RELEASING.md`.
- **Production releases** are Greg's call.

## Who is involved

| Situation | Who |
|---|---|
| Triage, fixes, test builds | agents and maintainers; no one to ask |
| Merging a PR after CI passes | the Jev gate; `needs-review` PRs go to an agent session or a maintainer |
| Anything needing a real signal generator or phone | testers, through a `needs-verification` issue with numbered steps |
| Play production release, money, anything irreversible | Greg |

## Not wired up yet

SpikerBot's always-on pieces (the linux-orchestrator drain that picks up issues and merges PRs,
the Jev issue triage workflow, the mac-builder test-build autopilot, and the SpikerBot Door chat
front door) are not connected to this repo. Until they are, issues are worked in agent sessions
started by hand.
