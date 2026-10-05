#!/usr/bin/env node
// Regenerates the "What exists" section of HANDOFF.md from the per-work-package
// fragments in docs/handoff/, and marks each of those packages Done in
// docs/ROADMAP.md. Fragments of a milestone whose roadmap rows are all Done are
// emitted as their first line only; the rest stays in docs/handoff/. Run by
// `npm run handoff` before committing; CI checks that the output is current.
// Output depends only on the fragments and roadmap, not the checkout or clock.
import { readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const fragmentDir = join(root, 'docs/handoff');
const BEGIN = '<!-- handoff:begin -->';
const END = '<!-- handoff:end -->';

function parseFragment(name) {
  const raw = readFileSync(join(fragmentDir, name), 'utf8');
  const parts = raw.match(/^---\r?\n([\s\S]*?)\r?\n---\r?\n([\s\S]*)$/);
  if (!parts) throw new Error(`docs/handoff/${name}: missing front matter`);
  const meta = {};
  for (const line of parts[1].split('\n')) {
    if (!line.trim()) continue;
    const colon = line.indexOf(':');
    if (colon < 0) throw new Error(`docs/handoff/${name}: bad front matter line "${line}"`);
    meta[line.slice(0, colon).trim()] = line.slice(colon + 1).trim();
  }
  for (const field of ['wp', 'pr', 'date']) {
    if (!meta[field]) throw new Error(`docs/handoff/${name}: front matter needs "${field}"`);
  }
  if (!/^\d+$/.test(meta.pr)) throw new Error(`docs/handoff/${name}: "pr" must be digits only`);
  const body = parts[2].trim().split('\n').map((line) => line.trim()).filter(Boolean);
  if (!body.length) throw new Error(`docs/handoff/${name}: empty body`);
  return { ...meta, body };
}

// M1-WP3a sorts after M1-WP3 and before M1-WP4.
function sortKey(wp) {
  const id = wp.match(/^M(\d+)-WP(\d+)([a-z]*)$/);
  if (!id) throw new Error(`unrecognised work package id: ${wp}`);
  return [Number(id[1]), Number(id[2]), id[3]];
}

const fragments = readdirSync(fragmentDir)
  .filter((name) => name.endsWith('.md') && name !== 'README.md')
  .map(parseFragment)
  .sort((a, b) => {
    const [x, y] = [sortKey(a.wp), sortKey(b.wp)];
    return x[0] - y[0] || x[1] - y[1] || x[2].localeCompare(y[2]);
  });

function write(path, next) {
  const current = readFileSync(path, 'utf8');
  if (current === next) return false;
  writeFileSync(path, next);
  return true;
}

const milestoneOf = (wp) => wp.split('-')[0];

// The roadmap is flipped first, so the collapsing below reads the same roadmap
// state on the first run as on every run after it. Doing it the other way round
// would emit the full text on the run that closes a milestone and the short form
// on the next one — CI would commit a churn diff on the following push.
const roadmapPath = join(root, 'docs/ROADMAP.md');
let roadmap = readFileSync(roadmapPath, 'utf8');
const unlisted = new Set();
for (const { wp, pr } of fragments) {
  const done = `**Done** (PR #${pr})`;
  // Two formats live in the roadmap: table rows `| M0-WP7 | outcome |` and
  // headings `**M1-WP5 — title**`. A cell or heading that already says Done is
  // left alone, which is what makes a second run a no-op.
  const row = new RegExp(`^(\\|\\s*${wp}\\s*\\|)(.*)$`, 'm');
  const heading = new RegExp(`^(\\*\\*${wp} — [^*]*\\*\\*)(.*)$`, 'm');
  const match = roadmap.match(row) ?? roadmap.match(heading);
  if (!match) {
    console.warn(`warning: ${wp} has a handoff fragment but no entry in docs/ROADMAP.md`);
    unlisted.add(milestoneOf(wp));
    continue;
  }
  if (/\*\*Done/.test(match[2].split('|')[0])) continue;
  roadmap = match[1].startsWith('|')
    ? roadmap.replace(row, (_, head, rest) => `${head} ${done}.${rest}`)
    : roadmap.replace(heading, (_, head, rest) => `${head} — ${done}${rest}`);
}

// A milestone is finished only when docs/ROADMAP.md lists no package for it that
// is still missing its Done marker. The roadmap is the only source that knows
// about packages nobody has built yet; "every fragment that exists is done" would
// call a milestone finished while a row sits there unclaimed. Anything the scan
// cannot account for — a milestone with no roadmap entries, or a fragment whose
// id is not in the roadmap at all — counts as open, because a milestone kept
// verbose by mistake costs lines and one collapsed by mistake hides live state.
const listed = new Set();
const open = new Set(unlisted);
for (const line of roadmap.split('\n')) {
  const entry = line.match(/^\|\s*(M\d+-WP\d+[a-z]*)\s*\|([^|]*)/)
    ?? line.match(/^\*\*(M\d+-WP\d+[a-z]*) — [^*]*\*\*(.*)$/);
  if (!entry) continue;
  listed.add(milestoneOf(entry[1]));
  if (!/\*\*Done/.test(entry[2])) open.add(milestoneOf(entry[1]));
}
const finished = (wp) => listed.has(milestoneOf(wp)) && !open.has(milestoneOf(wp));

const handoffPath = join(root, 'HANDOFF.md');
let handoff = readFileSync(handoffPath, 'utf8');
const begin = handoff.indexOf(BEGIN);
const end = handoff.indexOf(END);
if (begin < 0 || end < begin) throw new Error(`HANDOFF.md: missing the ${BEGIN} / ${END} markers`);

const bullets = fragments
  .map((f) => {
    const body = finished(f.wp) ? f.body.slice(0, 1) : f.body;
    return `- **${f.wp} (PR #${f.pr}):** ${body.join('\n  ')}`;
  })
  .join('\n');
handoff = `${handoff.slice(0, begin)}${BEGIN}\n\n${bullets}\n\n${handoff.slice(end)}`;

const latestDate = fragments.map((fragment) => fragment.date).sort().at(-1);
const stamp = `**Last updated:** ${latestDate} — latest handoff date`;
if (!/^\*\*Last updated:\*\* .*$/m.test(handoff)) throw new Error('HANDOFF.md: missing the "Last updated" line');
handoff = handoff.replace(/^\*\*Last updated:\*\* .*$/m, stamp);

const changed = [
  write(handoffPath, handoff) && 'HANDOFF.md',
  write(roadmapPath, roadmap) && 'docs/ROADMAP.md',
].filter(Boolean);
console.log(`${fragments.length} fragments — ${changed.length ? `rewrote ${changed.join(' and ')}` : 'nothing to do'}`);
