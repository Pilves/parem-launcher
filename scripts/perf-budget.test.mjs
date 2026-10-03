// Parsing and gate logic of perf-budget.mjs; the adb side needs a device.
// Run: node --test scripts/perf-budget.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { compare, median, overBudget, parseAmStart, parseGfxinfo } from './perf-budget.mjs';

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
  const base = run(500, 100, 5, 30);
  assert.deepEqual(compare(base, run(600, 120, 6, 36)), []);
  assert.deepEqual(compare(base, run(601, 100, 5, 30)), ['coldStartMs: 601 vs baseline 500 (+20%)']);
  // +50% but only 0.5 points of jank: under the floor
  assert.deepEqual(compare(run(500, 100, 1, 30), run(500, 100, 1.5, 30)), []);
  assert.deepEqual(compare(run(500, 100, 1, 30), run(500, 100, 2.5, 30)).length, 2);
});

test('gate fails on a missing measurement or baseline', () => {
  const base = run(500, 100, 5, 30);
  assert.deepEqual(compare(base, { ...base, returnHomeMs: null }), ['returnHomeMs: not measured']);
  assert.equal(compare({}, base).length, 6);
});

test('budgets are reported per metric', () => {
  assert.deepEqual(overBudget(run(250, 90, 0.5, 20)), ['drawerScroll.maxFrameMs: 40 > 32']);
});
