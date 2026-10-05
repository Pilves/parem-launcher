#!/usr/bin/env bash
# Claim a work package before starting it, so two contributors cannot pick the
# same id. A claim is a ref on origin named after the work package id, so the
# name is the lock: two racers push commits that are siblings rather than a
# chain, git refuses the second as a non-fast-forward, and whoever pushed first
# wins. Releasing writes a `release <id>` commit on the ref rather than deleting
# it, because a session's token may be allowed to create refs and refused a
# delete; the ref's tip, not its existence, is what says whether an id is free.
#
#   scripts/claim-wp.sh next              take the first package you can win
#   scripts/claim-wp.sh list              what is claimed right now
#   scripts/claim-wp.sh check M0-WP5      is this one free? (exit 1 if taken)
#   scripts/claim-wp.sh claim M0-WP5      take it (exit 1 if someone was faster)
#   scripts/claim-wp.sh release M0-WP5    give it back (merged, or abandoned);
#                                         deletes the ref too where allowed
#
# Claim before reading any code: a refused claim then costs a second, not a
# day's work thrown away because someone else shipped the same package.

set -euo pipefail

remote=${CLAIM_REMOTE:-origin}
prefix=refs/heads/claim

usage() {
	# The indented command lines of the header block, found rather than counted:
	# a hardcoded line range silently truncates the moment the prose above it
	# gains a line.
	sed -n '1,/^set -/p' "$0" | grep -E '^#   ' | sed 's/^# \{0,1\}//'
	exit 2
}

require_id() {
	[ $# -ge 1 ] || usage
	case "$1" in
	M[0-9]-WP[0-9] | M[0-9]*-WP[0-9]*[a-z] | M[0-9]*-WP[0-9]*) ;;
	*)
		echo "not a work package id: $1 (expected M0-WP5)" >&2
		exit 2
		;;
	esac
}

# Prints the claim commit's message, or nothing when the id is free.
#
# Free means either no ref at all or a ref whose tip is a release marker. The
# ref can outlive the claim because deleting it is not always possible: the
# token a session pushes with may create refs and still be refused a delete
# (GitHub answers 403), so `release` records the release in the ref instead and
# only deletes as a tidy-up. Reading the tip rather than the ref's existence is
# what makes the two paths identical to every caller.
claim_holder() {
	local sha
	sha=$(git ls-remote "$remote" "$prefix/$1" | cut -f1)
	[ -n "$sha" ] || return 0
	git fetch --quiet "$remote" "$prefix/$1" 2>/dev/null || true
	local body
	body=$(git log -1 --format=%B "$sha" 2>/dev/null) || {
		echo "claimed (run git fetch for details)"
		return 0
	}
	case "$body" in
	"release $1"*) return 0 ;;
	esac
	printf '%s\n' "$body"
}

cmd_list() {
	local out
	out=$(git ls-remote "$remote" "$prefix/*" | sed "s#.*$prefix/##")
	if [ -z "$out" ]; then
		echo "no work package is claimed on $remote"
		return 0
	fi
	local id holder listed=0 released=0
	while read -r id; do
		holder=$(claim_holder "$id")
		if [ -z "$holder" ]; then
			released=$((released + 1))
			continue
		fi
		listed=$((listed + 1))
		echo "$id"
		printf '%s\n' "$holder" | sed 's/^/  /'
	done <<<"$out"
	[ "$listed" -gt 0 ] || echo "no work package is claimed on $remote"
	[ "$released" -eq 0 ] ||
		echo "($released released claim ref(s) kept on $remote; they lock nothing)"
}

cmd_check() {
	require_id "$@"
	local holder
	holder=$(claim_holder "$1")
	if [ -n "$holder" ]; then
		echo "$1 is already claimed:" >&2
		echo "$holder" | sed 's/^/  /' >&2
		echo "Pick the next unclaimed id in docs/ROADMAP.md." >&2
		return 1
	fi
	echo "$1 is unclaimed"
}

# Pushes the claim. Returns 1 when someone else holds the id; prints nothing.
try_claim() {
	local id=$1 branch=$2
	local who
	who=$(git config user.name || echo unknown)
	who="$who <$(git config user.email || echo unknown)>"

	local msg tip new parent=()
	msg="claim $id

Claimed-By: $who
Branch: $branch
Claimed-At: $(date -u +%Y-%m-%dT%H:%M:%SZ)
Nonce: $(head -c 16 /dev/urandom | od -An -tx1 | tr -d ' \n')"

	tip=$(git ls-remote "$remote" "$prefix/$id" | cut -f1)
	if [ -n "$tip" ]; then
		# A released ref is reclaimed by committing on top of its marker, which
		# means the push is a fast-forward and would also succeed over a LIVE
		# claim. Refusing that is this check's whole job; without a parent the
		# push itself refused it.
		[ -z "$(claim_holder "$id")" ] || return 1
		git fetch --quiet "$remote" "$prefix/$id" 2>/dev/null || true
		parent=(-p "$tip")
	fi

	# Two racers reading the same tip build siblings, not a chain, so the second
	# push is not a fast-forward of the first and git rejects it. With no tip an
	# orphan commit cannot fast-forward a ref that now exists. Either way the
	# ref name stays the lock and the loser is told rather than overwriting.
	new=$(git commit-tree "$(git hash-object -w -t tree /dev/null)" "${parent[@]}" -m "$msg")
	git push --quiet "$remote" "$new:$prefix/$id" 2>/dev/null
}

cmd_claim() {
	require_id "$@"
	local id=$1
	local branch=${2:-$(git rev-parse --abbrev-ref HEAD)}
	if ! try_claim "$id" "$branch"; then
		echo "$id was claimed by someone else while you were starting:" >&2
		claim_holder "$id" | sed 's/^/  /' >&2
		echo "Run 'scripts/claim-wp.sh next' to take the first one you can win." >&2
		return 1
	fi
	echo "$id claimed for $branch. Release it with:"
	echo "  scripts/claim-wp.sh release $id"
}

# Work package ids in roadmap order, skipping the ones marked Done.
roadmap_ids() {
	local roadmap
	roadmap=$(git rev-parse --show-toplevel)/docs/ROADMAP.md
	[ -f "$roadmap" ] || {
		echo "no docs/ROADMAP.md to read ids from" >&2
		return 1
	}
	# Only ids that open a queue row (`| M0-WP2 |`) or a package heading
	# (`**M1-WP2 — ...`), never the ones mentioned in prose or examples.
	grep -v '\*\*Done' "$roadmap" |
		grep -oE '^(\| *|\*\*)M[0-9]+-WP[0-9]+[a-z]?' |
		grep -oE 'M[0-9]+-WP[0-9]+[a-z]?' |
		awk '!seen[$0]++'
}

# Walks the roadmap and stops at the first package this session actually wins,
# so nobody starts work on a package whose claim would have been refused.
cmd_next() {
	local branch=${1:-$(git rev-parse --abbrev-ref HEAD)}
	local id
	while read -r id; do
		[ -z "$(claim_holder "$id")" ] || continue
		if try_claim "$id" "$branch"; then
			echo "$id claimed for $branch (first unclaimed package in docs/ROADMAP.md)."
			echo "Check its wave's dependencies before you start; release it with:"
			echo "  scripts/claim-wp.sh release $id"
			return 0
		fi
		echo "  $id went to someone else, trying the next one" >&2
	done < <(roadmap_ids)
	echo "every package in docs/ROADMAP.md is claimed or done." >&2
	echo "Ask the owner what to pick up." >&2
	return 1
}

cmd_release() {
	require_id "$@"
	local id=$1
	local tip
	tip=$(git ls-remote "$remote" "$prefix/$id" | cut -f1)
	if [ -z "$tip" ]; then
		echo "$id is not claimed; nothing to release"
		return 0
	fi
	if [ -z "$(claim_holder "$id")" ]; then
		echo "$id was already released"
		return 0
	fi

	local who msg new
	who=$(git config user.name || echo unknown)
	who="$who <$(git config user.email || echo unknown)>"
	msg="release $id

Released-By: $who
Released-At: $(date -u +%Y-%m-%dT%H:%M:%SZ)"

	# The marker is the release. It is a fast-forward, so it needs only the
	# ref-create permission every session already has to claim in the first
	# place — unlike a delete, which the token may be refused.
	git fetch --quiet "$remote" "$prefix/$id" 2>/dev/null || true
	new=$(git commit-tree "$(git hash-object -w -t tree /dev/null)" -p "$tip" -m "$msg")
	if ! git push --quiet "$remote" "$new:$prefix/$id" 2>/dev/null; then
		echo "could not release $id: the claim moved while releasing." >&2
		echo "Re-run 'scripts/claim-wp.sh release $id'." >&2
		return 1
	fi

	# Tidy-up only: the id is already free above whether or not this works, so a
	# refused delete is not a failed release.
	if git push --quiet "$remote" --delete "$prefix/$id" 2>/dev/null; then
		echo "$id released, claim ref deleted"
	else
		echo "$id released (ref kept: this token may not delete refs)"
	fi
	return 0
}

[ $# -ge 1 ] || usage
action=$1
shift
case "$action" in
next) cmd_next "$@" ;;
list) cmd_list "$@" ;;
check) cmd_check "$@" ;;
claim) cmd_claim "$@" ;;
release) cmd_release "$@" ;;
*) usage ;;
esac
