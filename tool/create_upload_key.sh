#!/usr/bin/env bash
# One-time setup: create BYB Backpack's Google Play upload key and load it into this repo's
# "release" environment. Run it yourself in Terminal:
#
#   bash tool/create_upload_key.sh
#
# It prints no secrets. It leaves a backup folder on the Desktop (the keystore, its password
# and the certificate): put that folder in 1Password, then delete it from the Desktop.
# BYB Backpack is a new Play app, so whatever key signs its first upload becomes its upload key.
set -euo pipefail

REPO="${REPO:-BackyardBrains/RoboRoach-Android-App}"
OUT="${1:-$HOME/Desktop/BYB-Backpack-upload-key}"
ALIAS="upload"

for tool in gh openssl base64; do
  command -v "$tool" > /dev/null || { echo "Missing '$tool'. Install it and try again."; exit 1; }
done
if [ -e "$OUT" ]; then
  echo "$OUT already exists. Move it away first; this script never overwrites a key."
  exit 1
fi
if [ "${FORCE:-0}" != "1" ] && gh secret list --env release -R "$REPO" | grep -q '^ANDROID_KEYSTORE_BASE64'; then
  echo "The release environment already has an upload key. If Play already knows that key,"
  echo "replacing it breaks uploads. To replace it anyway: FORCE=1 bash tool/create_upload_key.sh"
  exit 1
fi

umask 077
mkdir -p "$OUT"
PASS="$(openssl rand -base64 48 | tr -d '/+=\n' | cut -c1-32)"
export PASS

# RSA 4096, valid 30 years (Play wants upload keys valid well past 2033).
openssl req -x509 -newkey rsa:4096 -sha256 -days 10950 -noenc \
  -subj "/CN=Backyard Brains/O=Backyard Brains, Inc./L=Ann Arbor/ST=Michigan/C=US" \
  -keyout "$OUT/private-key.pem" -out "$OUT/upload-certificate.pem" 2> /dev/null \
  || { echo "openssl could not create the key."; exit 1; }
openssl pkcs12 -export -name "$ALIAS" \
  -inkey "$OUT/private-key.pem" -in "$OUT/upload-certificate.pem" \
  -out "$OUT/byb-backpack-upload.p12" -passout env:PASS
rm -f "$OUT/private-key.pem"
printf '%s\n' "$PASS" > "$OUT/password.txt"

base64 < "$OUT/byb-backpack-upload.p12" | tr -d '\n' | gh secret set ANDROID_KEYSTORE_BASE64 --env release -R "$REPO"
printf '%s' "$PASS" | gh secret set ANDROID_KEYSTORE_PASSWORD --env release -R "$REPO"
printf '%s' "$PASS" | gh secret set ANDROID_KEY_PASSWORD --env release -R "$REPO"
printf '%s' "$ALIAS" | gh secret set ANDROID_KEY_ALIAS --env release -R "$REPO"

sha1="$(openssl x509 -in "$OUT/upload-certificate.pem" -noout -fingerprint -sha1 | cut -d= -f2)"
cat > "$OUT/README.txt" <<INFO
BYB Backpack (com.backyardbrains.bybbackpack) Google Play upload key
Created: $(date '+%Y-%m-%d')
Keystore: byb-backpack-upload.p12 (PKCS12)
Alias: $ALIAS
Store and key password: in password.txt (both the same)
Certificate SHA-1: $sha1
Loaded into GitHub: $REPO, environment "release"
If this key is ever lost, Play Console can reset the upload key (Setup > App signing).
INFO

echo
echo "Done. The upload key is in GitHub ($REPO, release environment)."
echo "Certificate SHA-1: $sha1"
echo "Backup folder: $OUT"
echo "Put that folder in 1Password now, then delete it from the Desktop."
