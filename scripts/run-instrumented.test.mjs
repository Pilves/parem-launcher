import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync, copyFileSync, chmodSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';

function run(state, stress = false, hangDiagnostic = false) {
  const root = mkdtempSync(join(tmpdir(), 'parem-ci-'));
  try {
    mkdirSync(join(root, 'scripts'));
    mkdirSync(join(root, 'bin'));
    copyFileSync(resolve('scripts/run-instrumented.sh'), join(root, 'scripts/run-instrumented.sh'));
    writeFileSync(join(root, 'gradlew'), '#!/bin/sh\necho "$*" >> phases\n[ "$1" != connectedPlayDebugAndroidTest ]\n');
    writeFileSync(join(root, 'bin/adb'), '#!/bin/sh\nif [ "$1" = get-state ]; then echo "$TEST_DEVICE_STATE"; fi\nif [ "$1 $2 $3" = "shell pm clear" ] && [ "$TEST_DEVICE_STATE" = offline ]; then exit 1; fi\nif [ "$TEST_HANG_DIAGNOSTIC" = yes ] && [ "$1 $2 $3" = "shell dumpsys dropbox" ] && [ ! -f hung ]; then touch hung; sleep 30; fi\n');
    writeFileSync(join(root, 'scripts/perf-compare.sh'), '#!/bin/sh\necho perf >> phases\n');
    chmodSync(join(root, 'gradlew'), 0o755);
    chmodSync(join(root, 'bin/adb'), 0o755);
    const result = spawnSync('bash', ['scripts/run-instrumented.sh', stress ? '2' : '0', 'true'], {
      cwd: root, env: { ...process.env, PATH: `${join(root, 'bin')}:${process.env.PATH}`, TEST_DEVICE_STATE: state, TEST_HANG_DIAGNOSTIC: hangDiagnostic ? 'yes' : 'no' }, encoding: 'utf8', timeout: 15000,
    });
    return { result, phases: readFileSync(join(root, 'phases'), 'utf8') };
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
}

test('a failed test remains failed; unavailable emulator skips later phases', () => {
  const { result, phases } = run('offline');
  assert.equal(result.status, 1, result.stderr);
  assert.match(phases, /connectedPlayDebugAndroidTest/);
  assert.doesNotMatch(phases, /connectedFullDebugAndroidTest|perf/);
  assert.match(result.stderr, /Emulator unavailable/);
});

test('an online emulator continues full and performance checks after test failure', () => {
  const { result, phases } = run('device');
  assert.equal(result.status, 1, result.stderr);
  assert.match(phases, /connectedFullDebugAndroidTest/);
  assert.match(phases, /perf/);
});

test('stress run fails immediately when package clear cannot reach the emulator', () => {
  const { result } = run('offline', true);
  assert.equal(result.status, 1, result.stderr);
  assert.match(result.stderr, /package clear failed/);
  assert.match(result.stdout, /DRAWER_STRESS failed=1 of=2/);
});

test('a stuck diagnostic is bounded and cannot suppress the original failure', () => {
  const { result } = run('offline', false, true);
  assert.equal(result.status, 1, result.stderr);
  assert.match(result.stderr, /Emulator unavailable/);
});
