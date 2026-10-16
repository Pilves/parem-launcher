// Parsing and gate logic of perf-budget.mjs; the adb side needs a device.
// Run: node --test scripts/perf-budget.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { drawerScrollGesture, compare, enoughFrames, hasScreen, measurementProblems, median, overBudget, parseAmStart, parseGfxinfo } from './perf-budget.mjs';

test('parses am start -W output', () => {
  const out = 'Starting: Intent { act=android.intent.action.MAIN }\nStatus: ok\nLaunchState: COLD\n'
    + 'Activity: com.parem.launcher.debug/com.parem.launcher.MainActivity\nTotalTime: 412\nWaitTime: 420\nComplete\n';
  assert.deepEqual(parseAmStart(out), { status: 'ok', launchState: 'COLD', totalTimeMs: 412 });
  assert.equal(parseAmStart('Warning: Activity not started\nStatus: ok\nComplete\n').totalTimeMs, null);
});

test('parses the gfxinfo package summary', () => {
  const out = `Applications Graphics Acceleration Info:
Uptime: 1 Realtime: 1

** Graphics info for pid 123 [com.parem.launcher.debug] **

Stats since: 1ns
Total frames rendered: 240
Janky frames: 6 (2.50%)
Janky frames (legacy): 30 (12.50%)
50th percentile: 7ms
90th percentile: 12ms
95th percentile: 16ms
99th percentile: 38ms
HISTOGRAM: 5ms=100 6ms=80 38ms=5 150ms=1 200ms=0 4950ms=0
50th gpu percentile: 1ms
GPU HISTOGRAM: 1ms=200 4950ms=3
`;
  assert.deepEqual(parseGfxinfo(out), { frames: 240, jankyPct: 2.5, p50Ms: 7, p99Ms: 38, maxFrameMs: 150 });
  assert.deepEqual(parseGfxinfo('nothing'), { frames: null, jankyPct: null, p50Ms: null, p99Ms: null, maxFrameMs: null });
});

test('median handles odd, even and empty', () => {
  assert.equal(median([3, 1, 2]), 2);
  assert.equal(median([4, 1, 2, 3]), 2.5);
  assert.equal(median([]), null);
});

const run = (cold, home, jank, p99) => ({
  coldStartMs: cold,
  returnHomeMs: home,
  drawerScroll: { jankyPct: jank, p99Ms: p99, maxFrameMs: 40 },
  omniboxTyping: { jankyPct: jank, p99Ms: p99, maxFrameMs: 20 },
});

test('gate fails only past 20% and the absolute floor', () => {
  const base = run(500, 100, 20, 30);
  assert.deepEqual(compare(base, run(600, 120, 24, 300)), []);
  assert.deepEqual(compare(base, run(601, 100, 20, 30)), ['coldStartMs: 601 vs baseline 500 (+20%)']);
  // +50% but only 3 points of jank: under the floor
  assert.deepEqual(compare(run(500, 100, 6, 30), run(500, 100, 9, 30)), []);
  assert.equal(compare(run(500, 100, 6, 30), run(500, 100, 12, 30)).length, 2);
});

test('gate fails on a missing measurement or baseline', () => {
  const base = run(500, 100, 5, 30);
  assert.deepEqual(compare(base, { ...base, returnHomeMs: null }), ['returnHomeMs: not measured']);
  assert.equal(compare({}, base).length, 4);
});

test('budgets are reported per metric', () => {
  assert.deepEqual(overBudget(run(250, 90, 0.5, 20)), ['drawerScroll.maxFrameMs: 40 > 32']);
});

test('a phase with too few frames counts as not measured', () => {
  const few = { frames: 12, jankyPct: 50, p50Ms: 16, p99Ms: 105, maxFrameMs: 105 };
  assert.deepEqual(enoughFrames(few), { frames: 12, jankyPct: null, p50Ms: 16, p99Ms: null, maxFrameMs: null });
  assert.deepEqual(enoughFrames({ ...few, frames: 30 }), { ...few, frames: 30 });
});


test('screen guard requires the requested resource inside the measured package', () => {
  const node = '<node package="com.parem.launcher.debug" resource-id="com.parem.launcher:id/search_src_text" />';
  assert.equal(hasScreen(node, 'com.parem.launcher.debug', 'search_src_text'), true);
  assert.equal(hasScreen(node, 'com.parem.launcher.debug', 'homeAppsLayout'), false);
  assert.equal(hasScreen(node.replace('com.parem.launcher.debug', 'com.android.systemui'),
    'com.parem.launcher.debug', 'search_src_text'), false);
  assert.equal(hasScreen('<node package="com.parem.launcher.debug" /><node resource-id="com.parem.launcher:id/search_src_text" />',
    'com.parem.launcher.debug', 'search_src_text'), false);
});

test('invalid screen or insufficient frames cannot become a valid measurement', () => {
  const valid = { drawerScroll: { frames: 30 }, omniboxTyping: { frames: 31 } };
  assert.deepEqual(measurementProblems(valid), []);
  assert.deepEqual(measurementProblems({ ...valid, drawerScroll: { error: 'expected drawer' } }),
    ['drawerScroll: expected drawer']);
  assert.deepEqual(measurementProblems({ ...valid, omniboxTyping: { frames: 1 } }),
    ['omniboxTyping: only 1 frames (need 30)']);
  // Invalid frame phases must not conceal independently measured start regressions.
  assert.ok(compare(run(500, 100, 5, 30), { coldStartMs: 1000, returnHomeMs: 200 })
    .some((problem) => problem.startsWith('coldStartMs: 1000')));
});

test('drawer reverse drags cannot pull past the first row', () => {
  const xml = (label) => `<node package="test.app" resource-id="test.app:id/recyclerView" bounds="[0,500][1080,1500]" />
    <node package="test.app" resource-id="test.app:id/appTitle" text="${label}" bounds="[0,500][1080,660]" />`;
  const top = drawerScrollGesture(xml('Calendar'), 'test.app', 'Calendar', true, 2400);
  assert.ok(top.to < top.from, 'turn upward when the first row is visible');
  const middle = drawerScrollGesture(xml('Drive'), 'test.app', 'Calendar', true, 2400);
  assert.equal(middle.to - middle.from, 80, 'reverse only half a visible row');
  assert.ok(middle.from > 500 && middle.to < 1500);
  assert.throws(() => drawerScrollGesture(xml('Drive'), 'other.app', 'Calendar', true, 2400));
});
