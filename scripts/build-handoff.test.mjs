// Port of platvorm's tests/handoff-generation.test.ts to node:test, so the
// Android repo needs no npm dependencies. Run: node --test scripts/build-handoff.test.mjs
import { execFileSync } from 'node:child_process';
import { copyFileSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import assert from 'node:assert/strict';

test('generates stable handoff docs without Git metadata and refreshes completed milestones', () => {
  const root = mkdtempSync(join(tmpdir(), 'parem-handoff-'));
  try {
    mkdirSync(join(root, 'scripts'));
    mkdirSync(join(root, 'docs/handoff'), { recursive: true });
    copyFileSync(join(import.meta.dirname, 'build-handoff.mjs'), join(root, 'scripts/build-handoff.mjs'));
    writeFileSync(join(root, 'HANDOFF.md'), '# Handoff\n**Last updated:** old checkout\n\n<!-- handoff:begin -->\nstale\n<!-- handoff:end -->\nKeep this note.\n');
    writeFileSync(join(root, 'docs/ROADMAP.md'), '| id | Outcome |\n|---|---|\n| M1-WP1 | First task |\n| M1-WP2 | Second task |\n');
    const fragment = (wp, pr, date, body) =>
      writeFileSync(join(root, `docs/handoff/${wp}.md`), `---\nwp: ${wp}\npr: ${pr}\ndate: ${date}\n---\n${body}\n`);
    const generate = () => execFileSync(process.execPath, [join(root, 'scripts/build-handoff.mjs')], { cwd: root });
    const output = () => ['HANDOFF.md', 'docs/ROADMAP.md'].map((file) => readFileSync(join(root, file), 'utf8'));

    fragment('M1-WP1', 1, '2026-09-26', 'First task exists.\nContract: keep details while the milestone is open.');
    generate();
    const partial = output();
    assert.ok(partial[0].includes('2026-09-26 — latest handoff date'));
    assert.ok(partial[0].includes('Contract: keep details'));
    assert.ok(partial[0].includes('Keep this note.'));
    assert.ok(partial[1].includes('**Done** (PR #1)'));
    assert.ok(partial[1].includes('| M1-WP2 | Second task |'));
    generate();
    assert.deepEqual(output(), partial);

    fragment('M1-WP2', 2, '2026-09-27', 'Second task exists.\nContract: another detail.');
    generate();
    const complete = output();
    assert.ok(complete[0].includes('2026-09-27 — latest handoff date'));
    assert.ok(complete[0].includes('First task exists.'));
    assert.ok(complete[0].includes('Second task exists.'));
    assert.ok(!complete[0].includes('Contract:'));
    assert.ok(complete[1].includes('**Done** (PR #2)'));
    generate();
    assert.deepEqual(output(), complete);

    // A PR can be created after the first generation. Only the provisional
    // Done marker changes, not a later PR mention or an established marker.
    writeFileSync(join(root, 'docs/ROADMAP.md'), complete[1]
      + '| M2-WP1 | **Done** (PR #0). See PR #0 for context. | PR #0 owner |\n'
      + '| M2-WP2 | **Done** (PR #7). Historical marker |\n'
      + '| M2-WP3 | **Done** (PR #0). Branch merge |\n'
      + '**M3-WP1 — Heading** — **Done** (PR #0) More context\n');
    fragment('M2-WP1', 21, '2026-09-28', 'New PR.');
    fragment('M2-WP2', 8, '2026-09-28', 'Existing PR.');
    fragment('M2-WP3', 0, '2026-09-28', 'Branch merge.');
    fragment('M3-WP1', 22, '2026-09-28', 'Heading PR.');
    generate();
    const synchronized = output();
    assert.ok(synchronized[1].includes('| M2-WP1 | **Done** (PR #21). See PR #0 for context. | PR #0 owner |'));
    assert.ok(synchronized[1].includes('| M2-WP2 | **Done** (PR #7). Historical marker |'));
    assert.ok(synchronized[1].includes('| M2-WP3 | **Done** (PR #0). Branch merge |'));
    assert.ok(synchronized[1].includes('**M3-WP1 — Heading** — **Done** (PR #22) More context'));
    generate();
    assert.deepEqual(output(), synchronized);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
