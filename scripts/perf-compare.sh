#!/usr/bin/env bash
# Disposable CI emulator only. Run after functional tests, once Android is warm.
set -euo pipefail
mkdir -p app/build/perf/reference
if adb shell pm path com.parem.launcher.debug | grep -q '^package:'; then
  adb uninstall com.parem.launcher.debug
fi
adb install ../perf-reference/app/build/outputs/apk/debug/app-debug.apk
node --input-type=module <<'JS'
import {measure, measurementProblems} from './scripts/perf-budget.mjs';
import {writeFileSync} from 'node:fs';
const result = measure('com.parem.launcher.debug', 'app/build/perf/reference');
result.source = '996ef0cf0b1b130d07c1d035bfcc456438c8ebff; same runner, emulator and workload as candidate';
writeFileSync('app/build/perf/reference/result.json', JSON.stringify(result, null, 2));
const problems = measurementProblems(result);
if (problems.length) throw new Error(problems.join('\n'));
JS
adb uninstall com.parem.launcher.debug
adb install app/build/outputs/apk/debug/app-debug.apk
node scripts/perf-budget.mjs --baseline app/build/perf/reference/result.json --out app/build/perf/result.json
