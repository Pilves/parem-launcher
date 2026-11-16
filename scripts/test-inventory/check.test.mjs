import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseDiscovery, verifyInventory } from './check.mjs';
const entry = (c, t, status = 1) => `INSTRUMENTATION_STATUS: class=${c}\nINSTRUMENTATION_STATUS: test=${t}\nINSTRUMENTATION_STATUS_CODE: ${status}\n`;
const result = (...entries) => entries.join('') + 'INSTRUMENTATION_CODE: -1\n';
test('requires exact reviewed single-device set and complete disjoint partition', () => {
  const a = entry('a', 'one'), b = entry('b', 'two');
  const all = parseDiscovery(result(a,b)), single = parseDiscovery(result(a)), rest = parseDiscovery(result(b));
  assert.deepEqual(verifyInventory(all,single,rest,new Set(['a#one'])).all,['a#one','b#two']);
  assert.throws(() => verifyInventory(all,single,new Set(),new Set(['a#one'])), /partition/);
  assert.throws(() => verifyInventory(all,single,all,new Set(['a#one'])), /partition/);
  assert.throws(() => verifyInventory(all,single,rest,new Set(['other'])), /reviewed/);
});
test('rejects incomplete, empty and duplicate discovery and includes ignored tests', () => {
  assert.throws(() => parseDiscovery('INSTRUMENTATION_CODE: -1\n'), /no tests/);
  assert.throws(() => parseDiscovery(entry('a','x')), /did not finish/);
  assert.throws(() => parseDiscovery(result(entry('a','x'),entry('a','x'))), /duplicate/);
  // AndroidX emits a start followed by ignored status for the same test.
  assert.ok(parseDiscovery(result(entry('a','ignored'),entry('a','ignored',-3))).has('a#ignored'));
});

test('reviewed inventory matches exactly the annotated source methods', async () => {
  const { readFileSync, readdirSync } = await import('node:fs');
  const dir = 'app/src/androidTest/java/com/parem/launcher';
  const actual = readdirSync(dir).filter(file => file.endsWith('.kt')).flatMap(file => {
    const name = file.slice(0, -3), source = readFileSync(`${dir}/${file}`, 'utf8');
    const tagged = [...source.matchAll(/(?:@SingleDevice\s+@Test|@Test\s+@SingleDevice)\s+fun\s+(\w+)\s*\(/g)];
    // An annotation in any other position would be missed here but still filter the CI run.
    assert.equal(tagged.length, source.split('@SingleDevice').length - 1, `unrecognised @SingleDevice placement in ${file}`);
    return tagged.map(m => `com.parem.launcher.${name}#${m[1]}`);
  }).sort();
  const reviewed = readFileSync('scripts/test-inventory/single-device.txt','utf8').trim().split(/\r?\n/).sort();
  assert.deepEqual(actual, reviewed);
});

test('the pull request leg runs every Play test; master legs together run every test in both flavours', async () => {
  const { readFileSync } = await import('node:fs');
  const workflow = readFileSync('.github/workflows/smoke.yml', 'utf8');
  assert.ok(workflow.includes('"${{ matrix.single_device }}" "${{ matrix.full }}"'), 'smoke.yml must pass the matrix to run-instrumented.sh');
  assert.equal([...workflow.matchAll(/'\{"include":/g)].length, 2);
  const legs = re => JSON.parse(workflow.match(re)[1]).include;
  const pr = legs(/github\.event_name == 'pull_request'\s*&&\s*'(\{"include":[^']*\})'/);
  const master = legs(/\|\|\s*'(\{"include":[^']*\})'/);
  // One leg with no @SingleDevice filter and no Full suite; perf would need the Full APK.
  assert.equal(pr.length, 1);
  assert.deepEqual([pr[0].single_device, pr[0].full, pr[0].perf], [true, false, false]);
  // Every master leg runs both flavours, and at least one runs the @SingleDevice tests.
  assert.ok(master.length > 1 && master.every(leg => leg.full === true));
  assert.ok(master.some(leg => leg.single_device === true));
  assert.ok(master.every(leg => typeof leg.single_device === 'boolean' && typeof leg.perf === 'boolean'));
});
