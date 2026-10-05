# Handoff fragments

`HANDOFF.md` is what every agent reads first, so it must not rot. It is
generated: the "What exists" section between `<!-- handoff:begin -->` and
`<!-- handoff:end -->` is built from the files in this directory by
`node scripts/build-handoff.mjs`. Run it and commit its output with the
fragment. `.github/workflows/handoff.yml` regenerates the files on every PR and
master push and fails on stale output; it never pushes. **Never edit that
section, or the `Last updated` line, by hand.**

One file per work package, named after its id: `docs/handoff/M1-WP5.md`. Each
PR adds its own fragment. If generated files conflict during integration, preserve
both fragments and rerun the generator after resolving the roadmap source.

## Format

Front matter, then a body of 3–8 lines:

```markdown
---
wp: M1-WP5
pr: 32
date: 2026-09-19
---
What now exists, in one or two lines — the state, not the diff.
Contract: the signatures, routes, columns or enum values other packages build on.
Not verified: what the checks did not cover, so the next person plans around it.
Easy to get wrong: the fact that will burn whoever touches this next.
```

- `wp` — the roadmap id, exactly as `docs/ROADMAP.md` spells it (`M1-WP3a`).
- `pr` — the PR number, digits only.
- `date` — the merge date, `YYYY-MM-DD`.
- Body lines are prose, one thought per line, no bullets and no headings. The
  generator joins them into a single bullet under the package's id.

## Template

Copy this, fill it in, keep it under eight lines.

```markdown
---
wp: <M1-WP6>
pr: <number>
date: <YYYY-MM-DD>
---
<What exists now that did not before.>
Contract: <what is frozen for other packages.>
Not verified: <what nobody checked.>
Easy to get wrong: <the non-obvious fact.>
```

## What the generator does

`node scripts/build-handoff.mjs` sorts the fragments by id, rewrites the generated section of
`HANDOFF.md`, stamps the latest fragment date (independent of Git HEAD and the
current clock), and flips the
package's row or heading in `docs/ROADMAP.md` to `**Done** (PR #n)` if it is
not already. It is idempotent: running it twice changes nothing the second
time.

### Finished milestones are collapsed to one line

`HANDOFF.md` is read before every task, so it must not grow with every package
that lands. After flipping the rows, the generator groups the fragments by
milestone (`M0`, `M1`, …) and asks `docs/ROADMAP.md` whether that milestone has
any package left: any row or heading for it that does not carry a `**Done**`
marker. A milestone with none is history — each of its fragments contributes
**only its first body line** to `HANDOFF.md`, and the full text stays here in
`docs/handoff/<WP-id>.md`. A milestone with one open package keeps every line,
because that is live state an agent needs.

**Write the first line so it can stand alone.** It is the one that survives:
what now exists, in one sentence, state and not diff. Contract, "not verified"
and "easy to get wrong" belong on the lines after it and are read from this
directory once the milestone closes.

The roadmap is the source, not this directory. A milestone can have fragments
for three packages and a fourth row nobody has built yet; counting fragments
would collapse it while work is still open. Anything the scan cannot account
for — a milestone with no roadmap entries, a fragment whose id the roadmap does
not list — is treated as open and emitted in full, because one milestone kept
verbose costs a few lines and one collapsed too early hides live state.

Work before this roadmap (PAREM-101…122, through the v5.7.0 candidate) has no
fragments; `HANDOFF.md` summarises it above the markers and
`docs/archive/TODO-pre-roadmap.md` keeps the ticket specs. The PR bodies remain
the long form; this directory is the distilled state.
