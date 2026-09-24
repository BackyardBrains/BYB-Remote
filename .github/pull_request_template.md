## For testers (plain English)
<!-- One or two short sentences a non-programmer tester understands: what changed for
     them and what to try. No issue/PR numbers, commit hashes, file names or internal
     terms. Build numbers are fine. If there is nothing a tester would notice or need
     to try, write exactly: Nothing to test. -->

## Linked issue
<!-- Refs #... -->
- [ ] `needs-verification` label is on the issue when closing it depends on a tester
      retesting with a real signal generator

### Tester steps
<!-- For fixes that need a device check: numbered, non-technical steps and what to report.
     Delete this section when no device check is needed. -->

## Checks
- [ ] `./gradlew lint testDebugUnitTest assembleDebug` passes locally or in CI
- [ ] CI is green, including both emulator smoke jobs
- [ ] Diff is limited to this change (no drive-by refactors or dependency bumps)

## Invariants (CLAUDE.md)
- [ ] Bluetooth protocol unchanged, or the matching firmware change is linked
- [ ] Application ID is still `com.backyardbrains.bybbackpack`
- [ ] No APK, AAB, keystore or other build output committed
