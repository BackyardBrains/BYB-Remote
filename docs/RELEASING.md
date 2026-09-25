# Releasing BYB Backpack

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
2. The run publishes pre-release `test-<code>` with `BYB-Backpack-<version>-<code>.apk` attached, and
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

**First upload of a new app:** Play only accepts the very first bundle through Play Console. Run
**Release - Google Play** with `build_only` checked, download the `byb-backpack-aab-…` artifact,
and upload `app-release.aab` in Play Console under Testing → Internal testing → Create release.
Keep "Google-generated app signing key" (Play App Signing). The key that signed this bundle
becomes the app's upload key. `build_only` never contacts Play, so it also works before the
service-account secret exists.

**While the app is a draft:** until BYB Backpack's first release goes live in Play Console, Play
calls it a "draft app" and only accepts API releases with `release_status: draft`. Run the
workflow with `draft`, then roll the release out from Play Console. After the first release is
live, `completed` works.

**Listing only:** run **Release - Google Play** with `listing_only` checked and `dry_run`
unchecked to push just the store text and icon from `fastlane/metadata/android/en-US/`, with no
build and no release. `dry_run` alone only asks Play to check the listing and saves nothing.

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

**Upload key:** run `bash tool/create_upload_key.sh` once in Terminal. It creates BYB Backpack's
upload key, loads the four `ANDROID_*` secrets below into the release environment without printing
them, and leaves a backup folder on the Desktop. Put that folder in 1Password, then delete it from
the Desktop. It refuses to replace a key that is already loaded.

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

About the signing key: BYB Backpack is a new app in Play Console (`com.backyardbrains.bybbackpack`).
The old BYB Remote listing (`com.backyardbrains.bybremote`) is tied to a signing key we no longer
have, so it is not used. Create the app in Play Console, upload the first AAB by hand (the Play API
cannot create apps), and enroll in Play App Signing. After that, the upload key in these secrets is
the one Play expects. Reusing SpikerBot's upload key is fine, since Play allows one upload key
across several apps.

Keep the keystore and passwords in the BYB password manager too. GitHub secrets cannot be read
back.
