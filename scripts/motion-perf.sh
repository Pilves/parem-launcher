#!/usr/bin/env bash
# Same emulator and driver: release branch before motion, then motion candidate.
set -euo pipefail
out=app/build/motion-perf
mkdir -p "$out/before" "$out/after"
for label in before after; do
  if [[ "$label" == before ]]; then root=../motion-before; else root=.; fi
  adb uninstall com.parem.launcher.debug >/dev/null 2>&1 || true
  adb install "$root/app/build/outputs/apk/full/debug/app-full-debug.apk"
  node --input-type=module - "$out/$label" <<'JS'
import {measure, measurementProblems} from './scripts/perf-budget.mjs';
import {writeFileSync} from 'node:fs';
const dir = process.argv[2];
const result = measure('com.parem.launcher.debug', dir);
writeFileSync(`${dir}/result.json`, JSON.stringify(result, null, 2));
const problems = measurementProblems(result);
if (problems.length) throw new Error(problems.join('\n'));
JS
done
node --input-type=module - "$out" <<'JS'
import {readFileSync} from 'node:fs';
import {compare, summaryTable} from './scripts/perf-budget.mjs';
const dir = process.argv[2];
const before = JSON.parse(readFileSync(`${dir}/before/result.json`));
const after = JSON.parse(readFileSync(`${dir}/after/result.json`));
console.log(summaryTable(before, after));
const failures = compare(before, after);
for (const key of ['coldStartMs', 'returnHomeMs']) {
  if (after[key] > before[key]) failures.push(`${key}: ${after[key]} > ${before[key]} ms before motion`);
}
if (failures.length) { console.error(failures.join('\n')); process.exit(1); }
JS
