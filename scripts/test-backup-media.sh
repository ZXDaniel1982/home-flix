#!/usr/bin/env bash
set -euo pipefail

# Smoke test for backup-media.sh using a throwaway fixture under /tmp.
# Run from anywhere: scripts/test-backup-media.sh

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
backup="$script_dir/backup-media.sh"

# Prefer a work dir on the root filesystem so the --delete mount guard is
# exercisable (a /tmp that is a separate tmpfs would defeat that check).
root_dev="$(findmnt -no SOURCE -T / 2>/dev/null || true)"
base="/tmp"
for cand in /var/tmp "$HOME"; do
	if [[ -d "$cand" && "$(findmnt -no SOURCE -T "$cand" 2>/dev/null || true)" == "$root_dev" ]]; then
		base="$cand"
		break
	fi
done
work="$(mktemp -d "$base/backup-media-test.XXXXXX")"
trap 'rm -rf "$work"' EXIT

ssd="$work/ssd-src"
hdd="$work/hdd-src"
dest="$work/dest"
mkdir -p "$ssd/movies" "$hdd/Entertainment" "$hdd/\$RECYCLE.BIN" \
	"$hdd/System Volume Information" "$dest"
touch "$ssd/movies/a.mp4" "$hdd/Entertainment/x.mkv" \
	"$hdd/\$RECYCLE.BIN/junk" "$hdd/System Volume Information/y" "$hdd/junk.tmp"

fails=0
expect_rc() {
	local desc="$1" want="$2" got="$3"
	if [[ "$want" == "$got" ]]; then
		echo "ok   - $desc"
	else
		echo "FAIL - $desc (want rc=$want, got rc=$got)"
		fails=$((fails + 1))
	fi
}
expect_exists() {
	if [[ -e "$2" ]]; then
		echo "ok   - $1"
	else
		echo "FAIL - $1 (missing $2)"
		fails=$((fails + 1))
	fi
}
expect_absent() {
	if [[ ! -e "$2" ]]; then
		echo "ok   - $1"
	else
		echo "FAIL - $1 (unexpected $2)"
		fails=$((fails + 1))
	fi
}

rc=0
SSD_MEDIA="$ssd" HDD_ARCHIVE="$hdd" "$backup" >/dev/null 2>&1 || rc=$?
expect_rc "missing DEST exits 1" 1 "$rc"

rc=0
SSD_MEDIA="$ssd" HDD_ARCHIVE="$hdd" DEST="$dest" "$backup" --bogus >/dev/null 2>&1 || rc=$?
expect_rc "unknown argument exits 2" 2 "$rc"

rc=0
SSD_MEDIA="$ssd" HDD_ARCHIVE="$hdd" DEST="$work/missing" "$backup" >/dev/null 2>&1 || rc=$?
expect_rc "nonexistent local DEST exits 1" 1 "$rc"

rc=0
SSD_MEDIA="$ssd" HDD_ARCHIVE="$hdd" DEST="user@host:" "$backup" >/dev/null 2>&1 || rc=$?
expect_rc "remote DEST with no path exits 1" 1 "$rc"

mkdir -p "$ssd/nested"
rc=0
SSD_MEDIA="$ssd" HDD_ARCHIVE="$hdd" DEST="$ssd/nested" "$backup" >/dev/null 2>&1 || rc=$?
expect_rc "DEST inside a source exits 1" 1 "$rc"

rc=0
DRY_RUN=1 SSD_MEDIA="$ssd" HDD_ARCHIVE="$hdd" DEST="$dest" "$backup" >/dev/null 2>&1 || rc=$?
expect_rc "dry run exits 0" 0 "$rc"
expect_absent "dry run writes nothing" "$dest/ssd-media"

rc=0
SSD_MEDIA="$ssd" HDD_ARCHIVE="$hdd" DEST="$dest" "$backup" >/dev/null 2>&1 || rc=$?
expect_rc "real run exits 0" 0 "$rc"
expect_exists "copies served media" "$dest/ssd-media/movies/a.mp4"
expect_exists "copies HDD data" "$dest/hdd/Entertainment/x.mkv"
expect_absent "skips \$RECYCLE.BIN" "$dest/hdd/\$RECYCLE.BIN"
expect_absent "skips System Volume Information" "$dest/hdd/System Volume Information"
expect_absent "skips *.tmp" "$dest/hdd/junk.tmp"

rm -f "$ssd/movies/a.mp4"
rc=0
SSD_MEDIA="$ssd" HDD_ARCHIVE="$hdd" DEST="$dest" "$backup" >/dev/null 2>&1 || rc=$?
expect_rc "second run exits 0" 0 "$rc"
expect_exists "without DELETE, removed source file is retained" "$dest/ssd-media/movies/a.mp4"

if [[ "$(findmnt -no SOURCE -T "$ssd" 2>/dev/null || true)" == "$root_dev" ]]; then
	rc=0
	DELETE=1 SSD_MEDIA="$ssd" HDD_ARCHIVE="$hdd" DEST="$dest" "$backup" >/dev/null 2>&1 || rc=$?
	expect_rc "DELETE on a source sharing the root filesystem is refused" 1 "$rc"
else
	echo "skip - DELETE guard (fixture is not on the root filesystem)"
fi

echo
if [[ "$fails" -eq 0 ]]; then
	echo "All checks passed."
else
	echo "$fails check(s) failed."
	exit 1
fi
