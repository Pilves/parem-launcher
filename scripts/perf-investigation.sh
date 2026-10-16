#!/usr/bin/env bash
# Manual CI diagnosis only. Installs into the disposable workflow emulator.
set -euo pipefail
for variant in candidate reference without-strictmode; do
  if adb shell pm path com.parem.launcher.debug | grep -q '^package:'; then
    adb uninstall com.parem.launcher.debug
  fi
  adb install "app/build/perf-investigation/$variant.apk"
  adb logcat -c
  PERF_VARIANT="$variant" node --input-type=module <<'JS'
import {measure, measurementProblems} from './scripts/perf-budget.mjs';
import {mkdirSync, writeFileSync} from 'node:fs';
const dir = `app/build/perf-investigation/${process.env.PERF_VARIANT}`;
mkdirSync(dir, {recursive: true});
const result = measure('com.parem.launcher.debug', dir);
writeFileSync(`${dir}/result.json`, JSON.stringify(result, null, 2));
console.log(process.env.PERF_VARIANT, JSON.stringify(result), measurementProblems(result));
JS
  adb logcat -d -t 5000 > "app/build/perf-investigation/$variant/logcat.txt"
done
