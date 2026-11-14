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
  const { readFileSync } = await import('node:fs');
  const actual = ['PrefsRoundTripTest', 'SmokeTest'].flatMap(name => {
    const source = readFileSync(`app/src/androidTest/java/com/parem/launcher/${name}.kt`, 'utf8');
    return [...source.matchAll(/@SingleDevice\s+@Test\s+fun\s+(\w+)\s*\(/g)]
      .map(m => `com.parem.launcher.${name}#${m[1]}`);
  }).sort();
  const reviewed = readFileSync('scripts/test-inventory/single-device.txt','utf8').trim().split(/\r?\n/).sort();
  assert.deepEqual(actual, reviewed);
});
