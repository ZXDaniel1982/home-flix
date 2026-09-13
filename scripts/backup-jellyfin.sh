#!/usr/bin/env bash
set -euo pipefail

# Back up the Jellyfin configuration off the Orange Pi.
#
# Runs on the development machine. It SSHs to the board, snapshots the live
# SQLite database with SQLite's online backup API (safe while Jellyfin runs),
# tars the essentials, pulls the tarball into a local backups directory, and
# prunes old backups. No downtime.
#
# Usage:
#   scripts/backup-jellyfin.sh
#   BACKUP_DIR=~/backups KEEP=14 scripts/backup-jellyfin.sh
#
# Env vars:
#   SERVER         Orange Pi hostname (default orangepi3b.local)
#   SSH_USER       SSH user on the board (default dzhang)
#   REMOTE_CONFIG  Jellyfin config dir on the board (default /mnt/ssd/jellyfin/config)
#   BACKUP_DIR     Local directory to store backups (default ~/backups)
#   KEEP           Number of most-recent backups to keep (default 7; 0 keeps all)

SERVER="${SERVER:-orangepi3b.local}"
SSH_USER="${SSH_USER:-dzhang}"
REMOTE_CONFIG="${REMOTE_CONFIG:-/mnt/ssd/jellyfin/config}"
BACKUP_DIR="${BACKUP_DIR:-$HOME/backups}"
KEEP="${KEEP:-7}"

usage() {
	cat <<'EOF'
Back up the Jellyfin configuration off the Orange Pi.

SSHs to the board, snapshots the live SQLite database with SQLite's online
backup API (safe while Jellyfin runs), tars the essentials (database, config/,
plugins/, root/), pulls the tarball into a local backups directory, and prunes
old backups. metadata/ and log/ are excluded.

Usage:
  scripts/backup-jellyfin.sh
  BACKUP_DIR=~/backups KEEP=14 scripts/backup-jellyfin.sh

Env vars:
  SERVER         Orange Pi hostname (default orangepi3b.local)
  SSH_USER       SSH user on the board (default dzhang)
  REMOTE_CONFIG  Jellyfin config dir on the board (default /mnt/ssd/jellyfin/config)
  BACKUP_DIR     Local directory to store backups (default ~/backups)
  KEEP           Number of most-recent backups to keep (default 7; 0 keeps all)
EOF
}

case "${1:-}" in
-h | --help)
	usage
	exit 0
	;;
"") ;;
*)
	echo "ERROR: unknown argument '$1' (try --help)." >&2
	exit 2
	;;
esac

for cmd in ssh rsync; do
	command -v "$cmd" >/dev/null 2>&1 || {
		echo "ERROR: '$cmd' is not installed." >&2
		exit 1
	}
done

[[ "$KEEP" =~ ^[0-9]+$ ]] || {
	echo "ERROR: KEEP must be a non-negative integer (got '$KEEP')." >&2
	exit 1
}

mkdir -p "$BACKUP_DIR"

echo "== Building backup on $SSH_USER@$SERVER =="
remote_config_q="$(printf '%q' "$REMOTE_CONFIG")"
remote_tar="$(
	ssh "$SSH_USER@$SERVER" "bash -s -- $remote_config_q" <<'REMOTE' | tail -n1
set -euo pipefail
umask 077
config="${1:?config dir required}"

[ -d "$config" ] || { echo "ERROR: no such config dir: $config" >&2; exit 1; }
command -v python3 >/dev/null 2>&1 || {
	echo "ERROR: python3 is required on the board." >&2
	exit 1
}

ts="$(date +%Y%m%d-%H%M%S)"
stage="$(mktemp -d)"
trap 'rm -rf "$stage"' EXIT

mkdir -p "$stage/data"

# Consistent snapshot of the live SQLite database(s).
for db in "$config"/data/*.db; do
	[ -f "$db" ] || continue
	name="$(basename "$db")"
	python3 - "$db" "$stage/data/$name" <<'PY'
import sqlite3, sys
src, dst = sys.argv[1], sys.argv[2]
s = sqlite3.connect(f"file:{src}?mode=ro", uri=True)
d = sqlite3.connect(dst)
with d:
    s.backup(d)
check = d.execute("PRAGMA quick_check").fetchone()
if not check or check[0] != "ok":
    raise SystemExit(f"snapshot integrity check failed: {check}")
d.close(); s.close()
PY
done

# Everything else under data/ except transient WAL/SHM and Jellyfin's own
# database backups (which we are already taking).
for entry in "$config"/data/*; do
	[ -e "$entry" ] || continue
	base="$(basename "$entry")"
	case "$base" in
	*.db | *.db-wal | *.db-shm | SQLiteBackups) continue ;;
	esac
	cp -a "$entry" "$stage/data/"
done

# Settings, plugins, and per-user root data.
for dir in config plugins root; do
	[ -d "$config/$dir" ] && cp -a "$config/$dir" "$stage/$dir"
done
[ -f "$config/.jellyfin-data" ] && cp -a "$config/.jellyfin-data" "$stage/"

tarball="/tmp/jellyfin-config-$ts.tar.gz"
tar -czf "$tarball" -C "$stage" .
chmod 600 "$tarball"
echo "$tarball"
REMOTE
)"

if [[ -z "$remote_tar" ]]; then
	echo "ERROR: remote backup did not produce a tarball." >&2
	exit 1
fi

cleanup_remote() {
	ssh "$SSH_USER@$SERVER" "rm -f '$remote_tar'" >/dev/null 2>&1 || true
}
trap cleanup_remote EXIT

local_tar="$BACKUP_DIR/$(basename "$remote_tar")"
echo "== Pulling $(basename "$remote_tar") =="
rsync -az "$SSH_USER@$SERVER:$remote_tar" "$local_tar"
cleanup_remote
trap - EXIT

listing="$(mktemp)"
tar -tzf "$local_tar" >"$listing"
grep -q '^\./data/jellyfin\.db$' "$listing" || {
	rm -f "$listing"
	echo "ERROR: backup is missing data/jellyfin.db." >&2
	exit 1
}
rm -f "$listing"

if [[ "$KEEP" -gt 0 ]]; then
	find "$BACKUP_DIR" -maxdepth 1 -name 'jellyfin-config-*.tar.gz' -printf '%T@ %p\0' |
		sort -zrn |
		tail -z -n "+$((KEEP + 1))" |
		cut -z -d' ' -f2- |
		xargs -0r rm -f
fi

echo
echo "Backup written: $local_tar ($(du -h "$local_tar" | cut -f1))"
echo "Kept: $(find "$BACKUP_DIR" -maxdepth 1 -name 'jellyfin-config-*.tar.gz' | wc -l) backup(s) in $BACKUP_DIR"
