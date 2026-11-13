import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, chmodSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';

const script = resolve('scripts/claim-wp.sh');

test('a new claim survives a concurrent release cleanup', () => {
  const dir = mkdtempSync(join(tmpdir(), 'parem-claim-'));
  const repo = join(dir, 'repo');
  const remote = join(dir, 'remote.git');
  const bin = join(dir, 'bin');
  const realGit = spawnSync('which', ['git'], { encoding: 'utf8' }).stdout.trim();
  const run = (cmd, args, env = {}) => {
    const result = spawnSync(cmd, args, { cwd: repo, env: { ...process.env, ...env }, encoding: 'utf8' });
    assert.equal(result.status, 0, `${cmd} ${args.join(' ')}\n${result.stdout}\n${result.stderr}`);
    return result.stdout;
  };
  try {
    mkdirSync(repo); mkdirSync(bin);
    run(realGit, ['init', '--bare', remote]);
    run(realGit, ['init']);
    run(realGit, ['config', 'user.name', 'Test']);
    run(realGit, ['config', 'user.email', 'test@example.invalid']);
    run(realGit, ['remote', 'add', 'origin', remote]);
    run('bash', [script, 'claim', 'M6-WP99', 'first']);

    // Interleave a new claim after the release marker push completes but before
    // the release process can perform its cleanup push.
    const wrapper = join(bin, 'git');
    writeFileSync(wrapper, `#!/bin/bash\n"$REAL_GIT" "$@"\nstatus=$?\nif [[ $status == 0 && $1 == push && -n $RACE_TRIGGER && \"\${*: -1}\" == *:refs/heads/claim/M6-WP99 ]]; then\n  message=$("$REAL_GIT" --git-dir="$RACE_REMOTE" log -1 --format=%s refs/heads/claim/M6-WP99)\n  if [[ $message == 'release M6-WP99' ]]; then\n    env -u RACE_TRIGGER PATH="$RACE_ORIGINAL_PATH" bash "$RACE_SCRIPT" claim M6-WP99 second || exit 90\n  fi\nfi\nexit $status\n`);
    chmodSync(wrapper, 0o755);
    run('bash', [script, 'release', 'M6-WP99'], {
      PATH: `${bin}:${process.env.PATH}`, REAL_GIT: realGit, RACE_TRIGGER: '1',
      RACE_REMOTE: remote, RACE_ORIGINAL_PATH: process.env.PATH, RACE_SCRIPT: script,
    });
    const holder = run('bash', [script, 'list']);
    assert.match(holder, /Branch: second/);
    assert.notEqual(spawnSync('bash', [script, 'check', 'M6-WP99'], { cwd: repo }).status, 0);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});
