#!/usr/bin/env node
// Jev auto-merge gate (Greg's ruling 2026-09-24: "Auto merge. I don't want to babysit. Use Jev to decide").
// Runs from .github/workflows/auto-merge.yml after CI passes on a pull request. It reads the PR only
// through the GitHub API and never executes PR code.
//
//   node tool/automerge_gate.mjs --pr <number> --sha <head sha>   # decide, then merge or label
//   node tool/automerge_gate.mjs --pr <number> --dry-run           # print Jev's answers and the decision only
//
// Code checks the hard rules; Jev (TypeSafe) answers three yes/no questions; code decides.
// Fails closed: if Jev can't be reached, nothing merges.
//
// Thresholds were calibrated 2026-09-24 on this repo's PRs #2-#7 plus synthetic bad PRs:
// routine PRs scored approve 0.75-0.93 / danger <= 0.07; bad ones (Bluetooth UUID change, deleted
// emulator tests, hidden app-ID change, hard-coded password) scored approve <= 0.10 / danger >= 0.97.
// Every decision comment lists the scores, so the thresholds can be re-tuned on real data.
import { execFileSync } from 'node:child_process';

const REPO = process.env.GITHUB_REPOSITORY || 'BackyardBrains/RoboRoach-Android-App';
const MAX_DIFF_CHARS = 60000;

// PRs that change the gate or the repo rules never merge on their own: otherwise an innocent-looking
// PR could weaken the rules that every later PR is judged by.
const PROTECTED = [/^\.github\/workflows\/auto-merge\.yml$/, /^tool\/automerge_gate\.mjs$/, /^CLAUDE\.md$/];
const THRESHOLDS = { approveMin: 0.7, dangerMax: 0.2, matchesMin: 0.6 };

const REPO_RULES = [
  'The Bluetooth protocol is fixed by firmware already in the field: the advertised-name match (RoboRoach / RoboHuman), the service and characteristic UUIDs, and the one-byte encodings in RemoteProtocol.java must not change unless a matching firmware change is linked.',
  'The application ID stays com.backyardbrains.bybbackpack.',
  'targetSdk must meet Google Play\'s current requirement (API 36 or higher).',
  'Never commit build outputs (APK or AAB files), keystores, passwords, API keys, or service-account files. Images and fonts under app/src/main/res are normal app resources.',
  'Tests and CI checks must not be deleted or weakened to make a change pass.',
];

function parseArgs(argv) {
  const out = { dryRun: false };
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === '--pr') out.pr = Number(argv[++i]);
    else if (argv[i] === '--sha') out.sha = argv[++i];
    else if (argv[i] === '--dry-run') out.dryRun = true;
  }
  if (!out.pr) throw new Error('usage: automerge_gate.mjs --pr <number> [--sha <head sha>] [--dry-run]');
  return out;
}

const gh = (...a) => execFileSync('gh', a, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });

async function askJev(state, questions) {
  const key = process.env.TYPESAFE_API_KEY;
  if (!key) throw new Error('TYPESAFE_API_KEY is not set');
  for (let attempt = 1; ; attempt++) {
    const res = await fetch('https://api.typesafe.ai/v1/systemone', {
      method: 'POST',
      headers: { Authorization: `Bearer ${key}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ state, model: 'jev-latest', questions }),
    });
    if (res.ok) return res.json();
    const retryable = res.status === 429 || res.status >= 500;
    if (!retryable || attempt >= 4) throw new Error(`TypeSafe HTTP ${res.status}: ${(await res.text()).slice(0, 300)}`);
    await new Promise((r) => setTimeout(r, 2000 * 2 ** attempt));
  }
}

function hardRuleFailures(pr, args, permission) {
  const fails = [];
  if (pr.state !== 'OPEN') fails.push(`the pull request is ${pr.state.toLowerCase()}`);
  if (pr.isDraft) fails.push('it is a draft');
  if (pr.baseRefName !== 'main') fails.push(`it targets ${pr.baseRefName}, not main`);
  if (pr.isCrossRepository) fails.push('it comes from a fork');
  if (args.sha && pr.headRefOid !== args.sha) fails.push('new commits arrived after CI ran');
  if (!args.sha) {
    // Started by hand: make sure CI actually passed on the current head.
    const checks = pr.statusCheckRollup || [];
    const notGreen = checks.filter((c) => !['SUCCESS', 'SKIPPED', 'NEUTRAL'].includes(c.conclusion || c.state));
    if (!checks.length || notGreen.length) fails.push('CI has not passed on the latest commit');
  }
  if (pr.labels.some((l) => l.name === 'hold')) fails.push('it has the hold label');
  if (!['admin', 'maintain', 'write'].includes(permission)) fails.push(`its author has ${permission || 'no'} access`);
  return fails;
}

function comment(pr, text) {
  gh('pr', 'comment', String(pr), '-R', REPO, '--body', text);
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const pr = JSON.parse(gh('pr', 'view', String(args.pr), '-R', REPO, '--json',
    'number,title,body,author,labels,files,headRefOid,isCrossRepository,isDraft,state,baseRefName,statusCheckRollup'));
  let permission = '';
  try {
    permission = gh('api', `repos/${REPO}/collaborators/${pr.author.login}/permission`, '--jq', '.permission').trim();
  } catch { /* unknown author access: the hard rule below declines */ }

  const hardFails = args.dryRun ? [] : hardRuleFailures(pr, args, permission);
  if (hardFails.length) {
    console.log(`Not merging #${pr.number}: ${hardFails.join('; ')}.`);
    return; // nothing to review: e.g. a draft or a fork. No label, no comment.
  }

  const diffFull = gh('pr', 'diff', String(pr.number), '-R', REPO);
  const truncated = diffFull.length > MAX_DIFF_CHARS;
  const files = pr.files.map((f) => ({ path: f.path, additions: f.additions, deletions: f.deletions }));
  const protectedTouched = files.filter((f) => PROTECTED.some((re) => re.test(f.path))).map((f) => f.path);

  const state = {
    repository: `${REPO}: BYB Backpack, a small native Java Android app that controls the Backyard Brains Bluetooth signal generator (RoboRoach backpack).`,
    repo_rules: REPO_RULES,
    pull_request: {
      title: pr.title,
      description: (pr.body || '').slice(0, 6000),
      author: pr.author.login,
      files,
      diff: truncated ? diffFull.slice(0, MAX_DIFF_CHARS) : diffFull,
      diff_truncated: truncated,
      automated_checks: 'Lint, unit tests, debug and release builds, and launch tests on Android 11 and 16 emulators all passed.',
    },
  };
  const questions = {
    approve: {
      type: 'noul',
      instructions: 'Would a careful maintainer of this repository approve `pull_request` as written?',
      criteria: {
        true: 'Yes: a reasonable change that does what it says and keeps every rule in `repo_rules`.',
        false: 'No: it breaks a rule in `repo_rules`, hides changes the description does not mention, weakens tests or checks, or leaks a secret.',
      },
    },
    danger: {
      type: 'noul',
      instructions: 'Does `pull_request.diff` do any of these: change a Bluetooth UUID, the advertised device names, or the byte encoding in RemoteProtocol.java; delete or weaken tests or CI checks; add a password, key, token or other secret; or change the application ID away from com.backyardbrains.bybbackpack?',
      criteria: { true: 'Yes, at least one of these happens in the diff.', false: 'None of these happen in the diff.' },
    },
    matches: {
      type: 'noul',
      instructions: 'Does `pull_request.diff` do what `pull_request.title` and `pull_request.description` say, without unrelated changes?',
      criteria: { true: 'The diff matches the stated purpose.', false: 'The diff does something else, or more than it says.' },
    },
  };

  let answers;
  try {
    answers = (await askJev(state, questions)).answers;
    // Fail closed on anything but three real probabilities: a missing score must never read as a pass.
    for (const id of Object.keys(questions)) {
      const v = answers?.[id]?.noul;
      if (typeof v !== 'number' || !Number.isFinite(v) || v < 0 || v > 1) {
        throw new Error(`Jev returned no usable answer for "${id}"`);
      }
    }
  } catch (e) {
    console.log(`Jev unavailable: ${e.message}`);
    if (!args.dryRun) {
      gh('pr', 'edit', String(pr.number), '-R', REPO, '--add-label', 'needs-review');
      comment(pr.number, `Auto-merge gate: Jev could not be reached, so this was not merged (the gate fails closed). Re-run the "Auto-merge (Jev)" workflow or merge by hand.\n\n\`${e.message.slice(0, 200)}\``);
    }
    process.exitCode = args.dryRun ? 1 : 0;
    return;
  }

  const sc = { approve: answers.approve.noul, danger: answers.danger.noul, matches: answers.matches.noul };
  const reasons = [];
  if (protectedTouched.length) reasons.push(`it changes the merge gate or the repo rules (${protectedTouched.join(', ')}), which always needs a person`);
  if (sc.danger > THRESHOLDS.dangerMax) reasons.push(`Jev sees a dangerous change (${sc.danger.toFixed(2)} > ${THRESHOLDS.dangerMax}): Bluetooth protocol, tests/CI weakened, a secret, or the app ID`);
  if (sc.approve < THRESHOLDS.approveMin) reasons.push(`Jev's approval ${sc.approve.toFixed(2)} is below ${THRESHOLDS.approveMin}`);
  if (sc.matches < THRESHOLDS.matchesMin) reasons.push(`the diff may not match the description (${sc.matches.toFixed(2)} < ${THRESHOLDS.matchesMin})`);
  const merge = reasons.length === 0;

  const table = `| Jev question | Answer | Needed |\n|---|---|---|\n| Would a careful maintainer approve it? | ${sc.approve.toFixed(2)} | ≥ ${THRESHOLDS.approveMin} |\n| Dangerous change (protocol, tests, secrets, app ID)? | ${sc.danger.toFixed(2)} | ≤ ${THRESHOLDS.dangerMax} |\n| Diff matches the description? | ${sc.matches.toFixed(2)} | ≥ ${THRESHOLDS.matchesMin} |`;
  const sensLine = truncated ? `\n\nThe diff was longer than ${MAX_DIFF_CHARS} characters; Jev saw the first part only.` : '';
  console.log(JSON.stringify({ pr: pr.number, scores: sc, protectedTouched, truncated, merge, reasons }));
  if (args.dryRun) return;

  if (merge) {
    comment(pr.number, `Auto-merge gate: merging.\n\n${table}${sensLine}`);
    gh('pr', 'merge', String(pr.number), '-R', REPO, '--squash', '--delete-branch',
      '--match-head-commit', pr.headRefOid, '--subject', `${pr.title} (#${pr.number})`);
    // Merges made with the workflow token don't trigger push workflows, so start CI on main explicitly.
    gh('workflow', 'run', 'ci.yml', '-R', REPO, '--ref', 'main');
  } else {
    gh('pr', 'edit', String(pr.number), '-R', REPO, '--add-label', 'needs-review');
    comment(pr.number, `Auto-merge gate: not merging, because ${reasons.join('; ')}. A person or an agent session should look at this; merge by hand when it's right.\n\n${table}${sensLine}`);
  }
}

main().catch((e) => { console.error(e); process.exit(1); });
