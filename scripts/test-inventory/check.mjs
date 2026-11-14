import { readFileSync, writeFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

export function parseDiscovery(text) {
  if (!/^INSTRUMENTATION_CODE: -1\s*$/m.test(text) || /INSTRUMENTATION_FAILED|shortMsg=|FAILURES!!!/i.test(text)) throw new Error('instrumentation discovery did not finish successfully');
  const tests = new Set();
  let name, method;
  for (const line of text.split(/\r?\n/)) {
    if (line.startsWith('INSTRUMENTATION_STATUS: class=')) name = line.slice('INSTRUMENTATION_STATUS: class='.length);
    if (line.startsWith('INSTRUMENTATION_STATUS: test=')) method = line.slice('INSTRUMENTATION_STATUS: test='.length);
    if (/^INSTRUMENTATION_STATUS_CODE: 1\s*$/.test(line)) {
      if (!name || !method) throw new Error('discovery entry missing class or method');
      const id = `${name}#${method}`;
      if (tests.has(id)) throw new Error(`duplicate discovery entry: ${id}`);
      tests.add(id); name = method = undefined;
    }
  }
  if (!tests.size) throw new Error('discovery returned no tests');
  return tests;
}

export function verifyInventory(full, single, filtered, expected) {
  const sorted = set => [...set].sort();
  const same = (a, b) => JSON.stringify(sorted(a)) === JSON.stringify(sorted(b));
  if (!expected.size || !same(single, expected)) throw new Error('single-device discovery differs from reviewed inventory');
  const intersection = new Set([...single].filter(x => filtered.has(x)));
  const union = new Set([...single, ...filtered]);
  if (intersection.size || !same(full, union)) throw new Error('filtered discovery does not partition the full inventory');
  return { all: sorted(full), single: sorted(single), device: sorted(filtered) };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const [allFile, singleFile, filteredFile, expectedFile, outputFile] = process.argv.slice(2);
    if (!outputFile) throw new Error('usage: node check.mjs all single filtered expected output');
    const expected = new Set(readFileSync(expectedFile, 'utf8').split(/\r?\n/).map(x => x.trim()).filter(Boolean));
    const result = verifyInventory(...[allFile, singleFile, filteredFile].map(x => parseDiscovery(readFileSync(x, 'utf8'))), expected);
    writeFileSync(outputFile, JSON.stringify(result, null, 2) + '\n');
    console.log(`discovered all=${result.all.length} single=${result.single.length} device=${result.device.length}`);
  } catch (error) { console.error(`inventory error: ${error.message}`); process.exitCode = 1; }
}
