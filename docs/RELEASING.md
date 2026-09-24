# Releasing BYB Remote

Two kinds of build leave this repo. Both are GitHub Actions workflows that you start by hand from
**main** (Actions tab → workflow → Run workflow).

| Build | Workflow | Who can start it |
|---|---|---|
| **Test build for testers**: signed APK on a GitHub pre-release | `Release - Android test APK (sideload)` | anyone; always OK |
| **Play internal testing**: testers install from Google Play | `Release - Google Play`, track `internal`, `dry_run` off | anyone; always OK |
| **Play production**: the public app | `Release - Google Play`, track `production` | **Greg only** |

The version name comes from `versionName` in `app/build.gradle.kts`. The version code is
`100000 + number of commits on main` (`tool/version_code.sh`), so the same commit gets the same code
in both lanes and every new commit gets a higher one.

## Test build (sideload APK)

1. Run **Release - Android test APK (sideload)**. Fill in "For testers" with a plain-English line
   about what changed and what to try.
2. The run publishes pre-release `test-<code>` with `BYB-Remote-<version>-<code>.apk` attached, and
   keeps a copy as a run artifact for 30 days.
3. Testers download the APK on the phone and allow the install when asked. Builds signed with the
   same upload key install over each other.

The repo is public, so the release page link works for anyone. There is no GitHub login needed.

## Google Play

Run **Release - Google Play**:

- `dry_run` (the default) builds the AAB and asks Play to validate it. Nothing is published.
- `track=internal`, `dry_run=false`: goes to the internal testing track (the tester list set in
  Play Console).
- To promote a tested build without rebuilding, set `promote_from` (for example internal →
  production).
- `production` with `rollout=0.1` and `release_status=inProgress` does a staged rollout.

The listing text and icon come from `fastlane/metadata/android/en-US/`. `changelogs/default.txt`
becomes the "What's new" text for every upload, so update it for each production release.

### Before the first production release (Play Console checklist)

- [ ] Target API level meets Google's current rule (API 36 until the August 2027 bump).
- [ ] Data safety form: the app collects and shares no data. There is no network access, analytics
      or accounts.
- [ ] Privacy policy URL set. Play requires one because the app asks for location on Android 11
      and older.
- [ ] Content rating questionnaire done; category Education (or Tools).
- [ ] Screenshots (at least 2 phone screenshots) and a 1024×500 feature graphic uploaded. They are
      not in the repo yet; the emulator smoke artifacts show the current screens.

## One-time setup

These secrets go in the **`release` environment** (Settings → Environments → release), never in
files or repo-level secrets. That environment only accepts runs from `main`, so a workflow edited on
another branch cannot read them:

| Secret | What |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | upload keystore (`.jks`), base64-encoded |
| `ANDROID_KEYSTORE_PASSWORD` | keystore password |
| `ANDROID_KEY_ALIAS` | key alias |
| `ANDROID_KEY_PASSWORD` | key password |
| `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` | Play Console service account with release rights for this app |

About the signing key:

- **If `com.backyardbrains.bybremote` already exists in Play Console** (the old BYB Remote
  listing), its updates must be signed with the key Play knows. With Play App Signing, that is
  the upload key registered there. Use that key, or ask Google for an upload-key reset from
  Play Console. Without Play App Signing, it has to be the app's original signing key.
- **If the app is new in Play Console**, create the app first and upload the first AAB by hand
  (the Play API cannot create apps). Enroll in Play App Signing, then any new upload key works.

Keep the keystore and passwords in the BYB password manager too. GitHub secrets cannot be read
back.
