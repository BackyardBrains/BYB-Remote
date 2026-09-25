#!/usr/bin/env node
// Upload BYB Backpack's Google Play store listing (en-US title, short and full description, icon)
// straight through the Play Developer API. Used by release-play.yml's listing_only mode.
// fastlane supply's metadata-only path looks up a release even when there is none, so it fails.
//
//   GOOGLE_PLAY_SERVICE_ACCOUNT_JSON='{...}' node tool/play_listing.mjs [--dry-run]
//
// --dry-run asks Play to validate the edit and saves nothing.
import { createSign } from 'node:crypto';
import { readFileSync } from 'node:fs';

const PACKAGE = process.env.ANDROID_PACKAGE_NAME || 'com.backyardbrains.bybbackpack';
const LANG = 'en-US';
const DIR = `fastlane/metadata/android/${LANG}`;
const API = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${PACKAGE}`;
const UPLOAD = `https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/${PACKAGE}`;
const dryRun = process.argv.includes('--dry-run');

const b64url = (b) => Buffer.from(b).toString('base64url');

async function accessToken(sa) {
  const now = Math.floor(Date.now() / 1000);
  const head = b64url(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
  const claims = b64url(JSON.stringify({
    iss: sa.client_email,
    scope: 'https://www.googleapis.com/auth/androidpublisher',
    aud: 'https://oauth2.googleapis.com/token',
    iat: now,
    exp: now + 3600,
  }));
  const signer = createSign('RSA-SHA256');
  signer.update(`${head}.${claims}`);
  const jwt = `${head}.${claims}.${b64url(signer.sign(sa.private_key))}`;
  const res = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion: jwt }),
  });
  if (!res.ok) throw new Error(`token: HTTP ${res.status} ${await res.text()}`);
  return (await res.json()).access_token;
}

async function call(token, method, url, body, contentType = 'application/json') {
  const res = await fetch(url, {
    method,
    headers: { Authorization: `Bearer ${token}`, ...(body !== undefined ? { 'Content-Type': contentType } : {}) },
    body: body === undefined ? undefined : contentType === 'application/json' ? JSON.stringify(body) : body,
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`${method} ${url.replace(API, '').replace(UPLOAD, '')}: HTTP ${res.status} ${text.slice(0, 500)}`);
  return text ? JSON.parse(text) : {};
}

const read = (name) => readFileSync(`${DIR}/${name}`, 'utf8').trim();

async function main() {
  const raw = process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON;
  if (!raw) throw new Error('GOOGLE_PLAY_SERVICE_ACCOUNT_JSON is not set');
  const token = await accessToken(JSON.parse(raw));

  const listing = {
    language: LANG,
    title: read('title.txt'),
    shortDescription: read('short_description.txt'),
    fullDescription: read('full_description.txt'),
  };
  if (listing.title.length > 30 || listing.shortDescription.length > 80 || listing.fullDescription.length > 4000) {
    throw new Error('listing text is over Play\'s limits (title 30, short 80, full 4000)');
  }

  const edit = await call(token, 'POST', `${API}/edits`, {});
  console.log(`Opened edit ${edit.id} for ${PACKAGE}`);
  await call(token, 'PUT', `${API}/edits/${edit.id}/listings/${LANG}`, listing);
  console.log(`Set ${LANG} title, short and full description`);
  await call(token, 'DELETE', `${API}/edits/${edit.id}/listings/${LANG}/icon`);
  await call(token, 'POST', `${UPLOAD}/edits/${edit.id}/listings/${LANG}/icon?uploadType=media`,
    readFileSync(`${DIR}/images/icon.png`), 'image/png');
  console.log('Uploaded icon');

  if (dryRun) {
    await call(token, 'POST', `${API}/edits/${edit.id}:validate`);
    await call(token, 'DELETE', `${API}/edits/${edit.id}`);
    console.log('Dry run: Play validated the listing; nothing was saved.');
  } else {
    await call(token, 'POST', `${API}/edits/${edit.id}:commit`);
    console.log('Committed: the store listing is updated on Google Play.');
  }
}

main().catch((e) => { console.error(`::error::${e.message}`); process.exit(1); });
