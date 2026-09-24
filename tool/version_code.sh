#!/usr/bin/env bash
# Android versionCode for a release build: 100000 + number of commits on the checked-out branch.
# The same commit always gets the same code in every release lane (sideload APK and Google Play),
# and every new commit on main gets a higher one. Needs a full-history checkout (fetch-depth: 0).
set -euo pipefail
echo $(( 100000 + $(git rev-list --count HEAD) ))
