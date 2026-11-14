#!/usr/bin/env bash
# Experimental measurement on a disposable CI emulator. Never changes the smoke gate.
set -euo pipefail
order=${1:-ABBA}
case "$order" in ABBA|BAAB) ;; *) echo "invalid order: $order" >&2; exit 2 ;; esac
out=app/build/perf-counterbalance
mkdir -p "$out"
printf 'order=AA%s\nreference=f61a3324315f8ba94c7a73282f7d678626b3bce5\n' "$order" | tee "$out/order.txt"
adb shell getprop > "$out/device-properties.txt"
index=0
for variant in A A $(echo "$order" | fold -w1); do
  index=$((index + 1))
  dir="$out/$index-$variant"
  mkdir -p "$dir"
  case "$variant" in A) apk=reference ;; B) apk=candidate ;; esac
  printf 'variant=%s\napk=%s\nstart=%s\n' "$variant" "$apk" "$(date -u +%FT%TZ)" | tee "$dir/metadata.txt"
  if adb shell pm path com.parem.launcher.debug | grep -q '^package:'; then adb uninstall com.parem.launcher.debug; fi
  adb install "$out/$apk.apk"
  status=0
  PERF_BLOCK_DIR="$dir" timeout --kill-after=10s 12m node --input-type=module <<'JS' 2>&1 | tee "$dir/measurement.txt" || status=$?
import {measure, measurementProblems} from './scripts/perf-budget.mjs';
import {writeFileSync} from 'node:fs';
const dir = process.env.PERF_BLOCK_DIR;
const result = measure('com.parem.launcher.debug', dir);
const problems = measurementProblems(result);
writeFileSync(`${dir}/result.json`, JSON.stringify({result, problems}, null, 2) + '\n');
console.log('invalid measurements:', JSON.stringify(problems));
if (problems.length) process.exitCode = 1;
JS
  printf 'finish=%s\nstatus=%s\n' "$(date -u +%FT%TZ)" "$status" | tee -a "$dir/metadata.txt"
  if (( status != 0 )); then exit "$status"; fi
done
