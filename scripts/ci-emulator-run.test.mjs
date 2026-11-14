import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, copyFileSync, chmodSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';

function run({ event = '', stale = '', status = 0, collectorFails = false, collectorDies = false, noAck = false, streamEvent = '' } = {}) {
  const root = mkdtempSync(join(tmpdir(), 'parem-incidents-'));
  try {
    mkdirSync(join(root, 'bin'));
    writeFileSync(join(root, 'events'), stale);
    copyFileSync(resolve('scripts/ci-emulator-run.sh'), join(root, 'wrapper.sh'));
    writeFileSync(join(root, 'bin/adb'), `#!/usr/bin/env bash
if [[ $1 == devices ]]; then printf 'List of devices attached\\nemulator-5554 device\\n'; exit; fi
shift 2
case "$*" in
 'shell getprop ro.kernel.qemu') echo 1 ;;
 'shell settings get global hide_error_dialogs') echo 1 ;;
 'logcat -b events -c') : > events ;;
 'logcat -b events -d '*) cat events ;;
 'shell log -p i -t ParemCiIncidentMonitor '*) echo "I ParemCiIncidentMonitor: $7" >> stream-input ;;
 'logcat -b main -b events -v '*) [[ \${TEST_COLLECTOR_FAIL:-0} == 1 ]] && exit 1; start=$SECONDS; seen=0; while :; do if [[ \${TEST_COLLECTOR_DIES:-0} == 1 && $((SECONDS-start)) -ge 2 ]]; then exit 1; fi; if [[ \${TEST_NO_ACK:-0} != 1 && -f stream-input ]]; then lines=$(wc -l < stream-input); if (( lines > seen )); then sed -n "$((seen+1)),\${lines}p" stream-input; seen=$lines; fi; fi; sleep 0.1; done ;;
esac
`);
    chmodSync(join(root, 'bin/adb'), 0o755);
    writeFileSync(join(root, 'work'), `#!/usr/bin/env bash\nprintf '%s' "$TEST_EVENT" >> events\nprintf '%s' "$TEST_STREAM_EVENT" >> stream-input\n[[ \${TEST_COLLECTOR_DIES:-0} == 1 ]] && sleep 3\nexit "$TEST_STATUS"\n`);
    chmodSync(join(root, 'work'), 0o755);
    const env = { ...process.env, GITHUB_ACTIONS: 'true', PATH: `${join(root, 'bin')}:${process.env.PATH}`, TEST_EVENT: event, TEST_STATUS: String(status), TEST_COLLECTOR_FAIL: collectorFails ? '1' : '0', TEST_COLLECTOR_DIES: collectorDies ? '1' : '0', TEST_NO_ACK: noAck ? '1' : '0', TEST_STREAM_EVENT: streamEvent };
    const result = spawnSync('bash', ['wrapper.sh', 'com.parem.launcher.debug', './work'], { cwd: root, env, encoding: 'utf8', timeout: 10000 });
    return { result, events: readFileSync(join(root, 'events'), 'utf8') };
  } finally { rmSync(root, { recursive: true, force: true }); }
}

const row = (tag, process, details = 'reason') => `11-14 06:00:00.000  100  200 I ${tag}: [0,123,${process},0,${details}]\n`;

test('Parem crash, ANR and subprocess events fail a passing workload', () => {
  for (const event of [row('am_crash', 'com.parem.launcher.debug', 'native_crash'), row('am_anr', 'com.parem.launcher.debug'), row('am_crash', 'com.parem.launcher.debug:worker'), row('am_crash', 'com.parem.launcher.debug', 'am_anr: [0,123,com.android.launcher,0,reason]')]) {
    const { result } = run({ event });
    assert.equal(result.status, 1, result.stderr);
    assert.match(result.stderr, /Parem crash\/ANR/);
  }
});

test('unrelated, near-prefix and test package events do not match', () => {
  const event = row('am_anr', 'com.android.launcher', 'com.parem.launcher.debug mentioned') + row('am_crash', 'com.parem.launcher.debug.test') + row('am_crash', 'com.parem.launcher.debugger');
  assert.equal(run({ event }).result.status, 0);
});

test('stale events are cleared before workload, malformed event fails closed', () => {
  assert.equal(run({ stale: row('am_crash', 'com.parem.launcher.debug') }).result.status, 0);
  assert.equal(run({ event: '11-14 06:00:00.000 I am_anr: broken\n' }).result.status, 1);
});

test('a failed workload stays failed and an unavailable collector cannot pass', () => {
  assert.equal(run({ status: 7 }).result.status, 7);
  assert.equal(run({ collectorFails: true }).result.status, 1);
  assert.equal(run({ collectorDies: true }).result.status, 1);
  assert.equal(run({ noAck: true }).result.status, 1);
  const streamOnly = run({ streamEvent: row('am_crash', 'com.parem.launcher.debug') });
  assert.equal(streamOnly.result.status, 1);
  assert.match(streamOnly.result.stderr, /Parem crash\/ANR/);
});
