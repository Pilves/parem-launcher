#!/usr/bin/env node
// Performance baseline + regression gate (M3-WP10). Drives an attached device
// or emulator over adb: cold start and return-to-home via `am start -W`, drawer
// scroll and omnibox typing frame timing via `dumpsys gfxinfo`.
//
//   node scripts/perf-budget.mjs [--package <id>] [--out <file>] [--baseline <file>] [--write-baseline]
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
import { readFileSync, writeFileSync, mkdirSync, appendFileSync, openSync, closeSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const root = resolve(import.meta.dirname, '..');
const BASELINE_FILE = resolve(root, 'scripts/perf-baseline.json');
const ACTIVITY = 'com.parem.launcher.MainActivity';
const RUNS = 10;
export const TOLERANCE = 0.2;

// Metrics the CI gate checks. A regression must also exceed `floor` in
// absolute terms: a phase renders ~70-120 frames, so a few slow frames on a
// shared runner move jank by several points. p99 and the worst frame are
// reported but not gated: at this frame count they are one or two outliers
// (p99 swung 81 → 101 ms between two runs of the same commit).
export const GATED = [
  { key: 'coldStartMs', floor: 10 },
  { key: 'returnHomeMs', floor: 10 },
  { key: 'drawerScroll.jankyPct', floor: 5 },
  { key: 'omniboxTyping.jankyPct', floor: 5 },
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

// Require visible app content, not a stale package name behind System UI.
export function hasScreen(xml, pkg, resource) {
  return [...xml.matchAll(/<node\b[^>]*>/g)].some(([node]) =>
    node.includes(`package="${pkg}"`)
    && new RegExp(`resource-id="[^" ]+:id/${resource}"`).test(node));
}

// Pulling beyond the first row intentionally dismisses Parem's drawer. Keep
// reverse drags below one row of travel and turn upward when the first row is visible.
export function drawerScrollGesture(xml, pkg, topLabel, down, screenHeight) {
  const nodes = [...xml.matchAll(/<node\b[^>]*>/g)].map(([node]) => node)
    .filter((node) => node.includes(`package="${pkg}"`));
  const list = nodes.find((node) => /resource-id="[^" ]+:id\/recyclerView"/.test(node));
  const rows = nodes.filter((node) => /resource-id="[^" ]+:id\/appTitle"/.test(node));
  const bounds = (node) => node?.match(/bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/)?.slice(1).map(Number);
  const box = bounds(list);
  if (!box || !rows.length) throw new Error('drawer has no measurable app rows');
  const firstLabel = rows[0].match(/\btext="([^"]*)"/)?.[1];
  const rowHeight = Math.max(...rows.map((row) => { const b = bounds(row); return b ? b[3] - b[1] : 0; }));
  const reverse = down && firstLabel !== topLabel;
  const clamp = (y) => Math.max(box[1] + 1, Math.min(box[3] - 1, Math.round(y)));
  const from = clamp(screenHeight * (reverse ? 0.3 : 0.6));
  const to = reverse ? clamp(from + rowHeight / 2) : clamp(screenHeight * 0.3);
  if (from === to) throw new Error('drawer is too short for a scroll gesture');
  return { firstLabel, x: Math.round((box[0] + box[2]) / 2), from, to };
}

export function measurementProblems(result) {
  return ['drawerScroll', 'omniboxTyping'].flatMap((phase) => {
    const value = result[phase];
    if (value?.error) return [`${phase}: ${value.error}`];
    return value?.frames >= MIN_FRAMES ? [] : [`${phase}: only ${value?.frames ?? 0} frames (need ${MIN_FRAMES})`];
  });
}

function hierarchy() {
  adb('rm -f /data/local/tmp/parem-perf.xml');
  adb('uiautomator dump /data/local/tmp/parem-perf.xml');
  return adb('cat /data/local/tmp/parem-perf.xml');
}

function requireScreen(pkg, resource) {
  const xml = hierarchy();
  if (!hasScreen(xml, pkg, resource)) throw new Error(`expected ${pkg} ${resource}; hierarchy: ${xml}`);
  return xml;
}

function seedPrefs(pkg) {
  // Skip the role chooser and onboarding, as SmokeTest does; never overwrite
  // existing prefs. Fails harmlessly on a non-debuggable build.
  const version = readFileSync(resolve(root, 'app/src/main/java/com/parem/launcher/data/Constants.kt'), 'utf8')
    .match(/const val ONBOARDING_VERSION = (\d+)/)[1];
  const file = 'shared_prefs/com.parem.launcher.xml';
  const xml = `<?xml version=\\"1.0\\" encoding=\\"utf-8\\" standalone=\\"yes\\" ?><map>`
    + `<boolean name=\\"FIRST_OPEN\\" value=\\"false\\" />`
    + `<int name=\\"ONBOARDING_VERSION_SEEN\\" value=\\"${version}\\" />`
    + `<boolean name=\\"AUTO_LAUNCH_SINGLE_MATCH\\" value=\\"false\\" /></map>`;
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
  let out;
  let stage = 'open home';
  try {
    startHome();
    sleepMs(1500);
    requireScreen(pkg, 'homeAppsLayout');
    stage = 'open drawer';
    open();
    sleepMs(1500);
    const initialScreen = requireScreen(pkg, 'search_src_text');
    if (shot) writeFileSync(shot.replace(/\.png$/, '.start.xml'), initialScreen);
    adb(`dumpsys gfxinfo ${pkg} reset`);
    stage = 'exercise drawer';
    act(initialScreen);
    sleepMs(1000);
    out = parseGfxinfo(adb(`dumpsys gfxinfo ${pkg}`));
    requireScreen(pkg, 'search_src_text');
  } catch (error) {
    out = { error: `${stage}: ${error.message}` };
  } finally {
    // Preserve the failed screen instead of silently measuring another app.
    if (shot) {
      const screenshot = openSync(shot, 'w');
      try {
        execFileSync('adb', ['exec-out', 'screencap', '-p'], { stdio: ['ignore', screenshot, 'pipe'] });
      } finally {
        closeSync(screenshot);
      }
      writeFileSync(shot.replace(/\.png$/, '.xml'), hierarchy());
      if (out?.error) writeFileSync(shot.replace(/\.png$/, '.events.txt'), adb('logcat -b events -d -t 300'));
    }
    startHome();
  }
  return out.error ? out : enoughFrames(out);
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
  const drawerScroll = frames(pkg, openDrawer, (initialScreen) => {
    const topLabel = drawerScrollGesture(initialScreen, pkg, null, false, h).firstLabel;
    let xml = initialScreen;
    for (let round = 0; round < 3; round++) {
      for (const down of [false, false, true, true]) {
        const { x, from, to } = drawerScrollGesture(xml, pkg, topLabel, down, h);
        adb(`input swipe ${x} ${from} ${x} ${to} 150`);
        sleepMs(700);
        xml = requireScreen(pkg, 'search_src_text');
      }
    }
  }, shot('drawer-scroll.png'));
  // Seeded debug prefs disable auto-launch; focus the actual input explicitly.
  const omniboxTyping = frames(pkg, () => {
    openDrawer();
    sleepMs(1500);
    const xml = requireScreen(pkg, 'search_src_text');
    const node = [...xml.matchAll(/<node\b[^>]*>/g)].find(([n]) => /resource-id="[^" ]+:id\/search_src_text"/.test(n))?.[0];
    const bounds = node?.match(/bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/);
    if (!bounds) throw new Error('search field has no bounds');
    adb(`input tap ${Math.round((Number(bounds[1]) + Number(bounds[3])) / 2)} ${Math.round((Number(bounds[2]) + Number(bounds[4])) / 2)}`);
    const focused = [...hierarchy().matchAll(/<node\b[^>]*>/g)].some(([n]) =>
      /resource-id="[^" ]+:id\/search_src_text"/.test(n) && n.includes('focused="true"'));
    if (!focused) throw new Error('search text field is not focused');
  }, () => {
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
  const invalid = measurementProblems(current);
  if (argv.includes('--write-baseline')) {
    if (invalid.length) {
      console.error(json + invalid.join('\n'));
      return 1;
    }
    writeFileSync(BASELINE_FILE, json);
    console.log(`wrote ${BASELINE_FILE}`);
    return 0;
  }
  let baseline = {};
  const baselineFile = opt('--baseline') ?? BASELINE_FILE;
  try {
    baseline = JSON.parse(readFileSync(baselineFile, 'utf8'));
  } catch {
    console.log(`no baseline at ${baselineFile}`);
  }
  const table = summaryTable(baseline, current);
  const problems = [...measurementProblems(baseline).map((p) => `invalid baseline: ${p}`), ...compare(baseline, current)];
  const budget = overBudget(current);
  const report = [table, '', ...invalid.map((p) => `INVALID MEASUREMENT ${p}`), ...problems.map((p) => `REGRESSION ${p}`), ...budget.map((b) => `over budget (not gated) ${b}`)].join('\n');
  console.log(json + '\n' + report);
  if (process.env.GITHUB_STEP_SUMMARY) appendFileSync(process.env.GITHUB_STEP_SUMMARY, `## Performance\n\n${report}\n`);
  return problems.length || invalid.length ? 1 : 0;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  process.exitCode = main(process.argv.slice(2));
}
