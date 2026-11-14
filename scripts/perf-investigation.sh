#!/usr/bin/env bash
if [[ ${PAREM_INCIDENT_MONITORED:-} != 1 ]]; then exec bash scripts/ci-emulator-run.sh com.parem.launcher.debug bash "$0" "$@"; fi
# Manual CI diagnosis only. Installs into the disposable workflow emulator.
set -euo pipefail
for variant in candidate reference without-strictmode; do
  if adb shell pm path com.parem.launcher.debug | grep -q '^package:'; then
    adb uninstall com.parem.launcher.debug
  fi
  adb install "app/build/perf-investigation/$variant.apk"
  adb logcat -b main -b system -b crash -c
  status=0
  PERF_VARIANT="$variant" node --input-type=module <<'JS' || status=$?
import {measure, measurementProblems} from './scripts/perf-budget.mjs';
import {mkdirSync, writeFileSync} from 'node:fs';
const dir = `app/build/perf-investigation/${process.env.PERF_VARIANT}`;
mkdirSync(dir, {recursive: true});
const result = measure('com.parem.launcher.debug', dir);
writeFileSync(`${dir}/result.json`, JSON.stringify(result, null, 2));
const problems = measurementProblems(result);
console.log(process.env.PERF_VARIANT, JSON.stringify(result), problems);
if (problems.length) process.exitCode = 1;
JS
  adb logcat -d -t 5000 > "app/build/perf-investigation/$variant/logcat.txt"
  if (( status != 0 )); then exit "$status"; fi
done
