#!/usr/bin/env node
// Performance baseline + regression gate (M3-WP10). Drives an attached device
// or emulator over adb: cold start and return-to-home via `am start -W`, drawer
// scroll and omnibox typing frame timing via `dumpsys gfxinfo`.
//
//   node scripts/perf-budget.mjs [--package <id>] [--out <file>] [--write-baseline]
//
// --out also saves a screenshot of each frame-timing phase next to the file.
//
// Compares against scripts/perf-baseline.json and exits 1 when a gated metric
// regresses by more than 20%. The absolute budgets (BUDGETS) are for a
// mid-range phone and are only reported: an emulator is not one.
// Expects the APK installed and the screen unlocked. A debuggable build gets
// its first-run prefs seeded through run-as; any other build must already be
// past onboarding.
import { execFileSync } from 'node:child_process';
import { readFileSync, writeFileSync, mkdirSync, appendFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const root = resolve(import.meta.dirname, '..');
const BASELINE_FILE = resolve(root, 'scripts/perf-baseline.json');
const ACTIVITY = 'com.parem.launcher.MainActivity';
const RUNS = 10;
export const TOLERANCE = 0.2;

// Metrics the CI gate checks. A regression must also exceed `floor` in
// absolute terms, so a 0.4% → 0.6% jank wobble on a shared runner is not one.
// The worst single frame is reported but not gated: one outlier is noise.
export const GATED = [
  { key: 'coldStartMs', floor: 10 },
  { key: 'returnHomeMs', floor: 10 },
  { key: 'drawerScroll.jankyPct', floor: 1 },
  { key: 'drawerScroll.p99Ms', floor: 5 },
  { key: 'omniboxTyping.jankyPct', floor: 1 },
  { key: 'omniboxTyping.p99Ms', floor: 5 },
];

export const BUDGETS = [
  { key: 'coldStartMs', max: 300 },
  { key: 'returnHomeMs', max: 100 },
  { key: 'drawerScroll.jankyPct', max: 1 },
  { key: 'drawerScroll.maxFrameMs', max: 32 },
  { key: 'omniboxTyping.jankyPct', max: 1 },
  { key: 'omniboxTyping.maxFrameMs', max: 32 },
];

export function parseAmStart(text) {
  const field = (name) => text.match(new RegExp(`^\\s*${name}: (\\S+)`, 'm'))?.[1];
  const total = field('TotalTime');
  return {
    status: field('Status') ?? null,
    launchState: field('LaunchState') ?? null,
    totalTimeMs: total === undefined ? null : Number(total),
  };
}

// The package summary at the top of `dumpsys gfxinfo <pkg>`; the first
// "Janky frames:" line is the current one, "(legacy)" is the old heuristic.
export function parseGfxinfo(text) {
  const num = (re) => {
    const m = text.match(re);
    return m ? Number(m[1]) : null;
  };
  const histogram = text.match(/^\s*HISTOGRAM:(.*)$/m)?.[1] ?? '';
  let maxFrameMs = null;
  for (const [, ms, count] of histogram.matchAll(/(\d+)ms=(\d+)/g)) {
    if (Number(count) > 0) maxFrameMs = Math.max(maxFrameMs ?? 0, Number(ms));
  }
  return {
    frames: num(/^\s*Total frames rendered: (\d+)/m),
    jankyPct: num(/^\s*Janky frames: \d+ \(([\d.]+)%\)/m),
    p50Ms: num(/^\s*50th percentile: (\d+)ms/m),
    p99Ms: num(/^\s*99th percentile: (\d+)ms/m),
    maxFrameMs,
  };
}

export function median(values) {
  if (values.length === 0) return null;
  const s = [...values].sort((a, b) => a - b);
  const mid = Math.floor(s.length / 2);
  return s.length % 2 ? s[mid] : (s[mid - 1] + s[mid]) / 2;
}

export const metric = (result, key) => key.split('.').reduce((o, k) => o?.[k], result) ?? null;

// Returns one line per problem; empty means the gate passes. A metric missing
// from the current run fails (the measurement broke); one missing from the
// baseline fails too, so the gate can't silently stop checking it.
export function compare(baseline, current, tolerance = TOLERANCE) {
  const problems = [];
  for (const { key, floor } of GATED) {
    const base = metric(baseline, key);
    const cur = metric(current, key);
    if (cur === null) problems.push(`${key}: not measured`);
    else if (base === null) problems.push(`${key}: no baseline (run with --write-baseline)`);
    else if (cur > base * (1 + tolerance) && cur - base > floor) {
      problems.push(`${key}: ${cur} vs baseline ${base} (+${Math.round((cur / base - 1) * 100)}%)`);
    }
  }
  return problems;
}

export function overBudget(current) {
  return BUDGETS.filter(({ key, max }) => {
    const v = metric(current, key);
    return v !== null && v > max;
  }).map(({ key, max }) => `${key}: ${metric(current, key)} > ${max}`);
}

export function summaryTable(baseline, current) {
  const keys = [...new Set([...GATED, ...BUDGETS].map((m) => m.key))];
  const budget = (key) => BUDGETS.find((b) => b.key === key)?.max ?? '';
  const rows = keys.map((key) =>
    `| ${key} | ${metric(current, key) ?? '—'} | ${metric(baseline, key) ?? '—'} | ${budget(key)} | ${GATED.some((g) => g.key === key) ? 'yes' : 'no'} |`);
  return ['| metric | this run | baseline | budget (mid-range) | gated |', '|---|---|---|---|---|', ...rows].join('\n');
}

// --- device side -----------------------------------------------------------

const sleepMs = (ms) => Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, ms);
const adb = (...args) => execFileSync('adb', ['shell', ...args], { encoding: 'utf8' });

function seedPrefs(pkg) {
  // Skip the role chooser and onboarding, as SmokeTest does; never overwrite
  // existing prefs. Fails harmlessly on a non-debuggable build.
  const version = readFileSync(resolve(root, 'app/src/main/java/com/parem/launcher/data/Constants.kt'), 'utf8')
    .match(/const val ONBOARDING_VERSION = (\d+)/)[1];
  const file = 'shared_prefs/com.parem.launcher.xml';
  const xml = `<?xml version=\\"1.0\\" encoding=\\"utf-8\\" standalone=\\"yes\\" ?><map>`
    + `<boolean name=\\"FIRST_OPEN\\" value=\\"false\\" />`
    + `<int name=\\"ONBOARDING_VERSION_SEEN\\" value=\\"${version}\\" /></map>`;
  try {
    adb(`run-as ${pkg} sh -c 'mkdir -p shared_prefs && (test -f ${file} || printf %s "${xml}" > ${file})'`);
  } catch {
    console.log(`run-as failed for ${pkg}: not seeding prefs`);
  }
}

const startHome = () => parseAmStart(adb('am start -W -a android.intent.action.MAIN -c android.intent.category.HOME'));
const startSettings = () => adb('am start -W -a android.settings.SETTINGS');

function coldStart(pkg) {
  startSettings();
  adb(`am force-stop ${pkg}`);
  sleepMs(1000);
  return parseAmStart(adb(`am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n ${pkg}/${ACTIVITY}`));
}

function returnHome() {
  startSettings();
  sleepMs(1000);
  return startHome();
}

function screenSize() {
  const sizes = [...adb('wm size').matchAll(/(\d+)x(\d+)/g)];
  const [, w, h] = sizes.at(-1);
  return { w: Number(w), h: Number(h) };
}

// Fewer frames than this and one slow frame moves jank by several points:
// treat the phase as not measured rather than gate on noise.
export const MIN_FRAMES = 30;
export const enoughFrames = (f) => (f.frames !== null && f.frames >= MIN_FRAMES ? f
  : { ...f, jankyPct: null, p99Ms: null, maxFrameMs: null });

// shot: where to save a screenshot of what the phase ended on, so a run with
// too few frames can be diagnosed from the CI artifact
function frames(pkg, open, act, shot) {
  startHome();
  sleepMs(1500);
  open();
  sleepMs(1500);
  adb(`dumpsys gfxinfo ${pkg} reset`);
  act();
  sleepMs(1000);
  const out = parseGfxinfo(adb(`dumpsys gfxinfo ${pkg}`));
  if (shot) writeFileSync(shot, execFileSync('adb', ['exec-out', 'screencap', '-p']));
  startHome();
  return enoughFrames(out);
}

function samples(label, runs, fn, accept) {
  fn(); // warm-up: first start after install pays for verification and JIT
  const times = [];
  for (let i = 0; i < runs; i++) {
    const r = fn();
    if (accept(r) && r.totalTimeMs !== null) times.push(r.totalTimeMs);
    else console.log(`${label}: dropped run ${i + 1} (${JSON.stringify(r)})`);
  }
  console.log(`${label}: ${times.join(' ')} ms`);
  return times.length >= runs / 2 ? median(times) : null;
}

export function measure(pkg, shotDir) {
  seedPrefs(pkg);
  console.log(adb(`cmd package set-home-activity ${pkg}/${ACTIVITY}`).trim());
  const { w, h } = screenSize();
  const x = Math.round(w / 2);
  const openDrawer = () => adb(`input swipe ${x} ${Math.round(h * 0.75)} ${x} ${Math.round(h * 0.25)} 200`);

  const coldStartMs = samples('cold start', RUNS, () => coldStart(pkg), (r) => r.launchState === 'COLD');
  const returnHomeMs = samples('return home', RUNS, returnHome, (r) => r.status === 'ok');
  const shot = (name) => shotDir && resolve(shotDir, name);
  const drawerScroll = frames(pkg, openDrawer, () => {
    for (let round = 0; round < 3; round++) {
      for (const [from, to] of [[0.8, 0.2], [0.8, 0.2], [0.2, 0.8], [0.2, 0.8]]) {
        adb(`input swipe ${x} ${Math.round(h * from)} ${x} ${Math.round(h * to)} 150`);
        sleepMs(700);
      }
    }
  }, shot('drawer-scroll.png'));
  // A leading space turns off auto-launch on a single match
  const omniboxTyping = frames(pkg, openDrawer, () => {
    for (let round = 0; round < 3; round++) {
      for (const c of ['%s', ...'settings']) {
        adb(`input text ${c}`);
        sleepMs(300);
      }
      for (let i = 0; i < 9; i++) {
        adb('input keyevent KEYCODE_DEL');
        sleepMs(150);
      }
    }
  }, shot('omnibox-typing.png'));
  return { package: pkg, coldStartMs, returnHomeMs, drawerScroll, omniboxTyping };
}

function main(argv) {
  const opt = (name) => {
    const i = argv.indexOf(name);
    return i >= 0 ? argv[i + 1] : undefined;
  };
  const pkg = opt('--package') ?? 'com.parem.launcher.debug';
  const outDir = opt('--out') && dirname(resolve(opt('--out')));
  if (outDir) mkdirSync(outDir, { recursive: true });
  const current = measure(pkg, outDir);
  const json = JSON.stringify(current, null, 2) + '\n';
  if (outDir) writeFileSync(opt('--out'), json);
  if (argv.includes('--write-baseline')) {
    writeFileSync(BASELINE_FILE, json);
    console.log(`wrote ${BASELINE_FILE}`);
    return 0;
  }
  let baseline = {};
  try {
    baseline = JSON.parse(readFileSync(BASELINE_FILE, 'utf8'));
  } catch {
    console.log(`no baseline at ${BASELINE_FILE}`);
  }
  const table = summaryTable(baseline, current);
  const problems = compare(baseline, current);
  const budget = overBudget(current);
  const report = [table, '', ...problems.map((p) => `REGRESSION ${p}`), ...budget.map((b) => `over budget (not gated) ${b}`)].join('\n');
  console.log(json + '\n' + report);
  if (process.env.GITHUB_STEP_SUMMARY) appendFileSync(process.env.GITHUB_STEP_SUMMARY, `## Performance\n\n${report}\n`);
  return problems.length ? 1 : 0;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  process.exitCode = main(process.argv.slice(2));
}
