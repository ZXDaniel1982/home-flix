#!/usr/bin/env bash
set -euo pipefail

# Back up media from the Orange Pi to a target (local mount or SSH host).
#
# Run this ON the Orange Pi, since that is where the media lives and where a
# cron job would invoke it. It rsyncs the served media (/mnt/ssd/media) and the
# HDD (/mnt/hdd) into DEST/ssd-media and DEST/hdd.
#
# Usage:
#   DEST=/mnt/backup/ scripts/backup-media.sh
#   DEST=user@nas.local:/volume1/backups/ scripts/backup-media.sh
#   DRY_RUN=1 DEST=user@nas.local:/volume1/backups/ scripts/backup-media.sh
#
# Env vars:
#   DEST         required; a local dir that exists, or [user@]host:/path
#   SSD_MEDIA    served media dir (default /mnt/ssd/media)
#   HDD_ARCHIVE  HDD archive dir (default /mnt/hdd)
#   DELETE       set to 1 to delete target files missing from the source (off by default)
#   DRY_RUN      set to 1 to preview without writing
#
# --delete is OFF by default on purpose: if a source is ever lost or remounted
# empty, --delete would erase the backup too. Even with DELETE=1 the script
# refuses to run unless each source is on a separate mounted filesystem, so an
# unmounted disk cannot wipe the backup. rsync honours RSYNC_RSH
# (e.g. RSYNC_RSH='ssh -p 2222'). Prefer a POSIX destination (ext4/xfs/btrfs);
# on exFAT/NTFS/SMB add RSYNC_EXTRA='--no-owner --no-group --no-perms'.

usage() {
	cat <<'EOF'
Back up media from the Orange Pi to a target.

Run on the Orange Pi. rsyncs /mnt/ssd/media and /mnt/hdd into DEST/ssd-media and
DEST/hdd.

Usage:
  DEST=/mnt/backup/ scripts/backup-media.sh
  DEST=user@nas.local:/volume1/backups/ scripts/backup-media.sh
  DRY_RUN=1 DEST=user@nas.local:/volume1/backups/ scripts/backup-media.sh

Env vars:
  DEST         required; a local dir that exists, or [user@]host:/path
  SSD_MEDIA    served media dir (default /mnt/ssd/media)
  HDD_ARCHIVE  HDD archive dir (default /mnt/hdd)
  DELETE       set to 1 to delete target files missing from the source (off by default)
  DRY_RUN      set to 1 to preview without writing
  RSYNC_EXTRA  extra rsync flags, word-split (e.g. '--no-owner --no-group')
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

SSD_MEDIA="${SSD_MEDIA:-/mnt/ssd/media}"
HDD_ARCHIVE="${HDD_ARCHIVE:-/mnt/hdd}"
DELETE="${DELETE:-0}"
DRY_RUN="${DRY_RUN:-0}"
RSYNC_EXTRA="${RSYNC_EXTRA:-}"

if [[ -z "${DEST:-}" ]]; then
	echo "ERROR: DEST is required (a local dir or [user@]host:/path)." >&2
	echo "       Example: DEST=user@nas.local:/volume1/backups/ $0" >&2
	exit 1
fi

command -v rsync >/dev/null 2>&1 || {
	echo "ERROR: 'rsync' is not installed." >&2
	echo "       Pi: sudo apt install -y rsync" >&2
	exit 1
}

while [[ "$DEST" == */ ]]; do
	DEST="${DEST%/}"
done

# rsync treats a target as remote when it has a colon before the first slash.
if [[ "$DEST" == *:* && "${DEST%%:*}" != */* ]]; then
	[[ "$DEST" == *: ]] && {
		echo "ERROR: remote DEST '$DEST' needs an explicit path after ':'." >&2
		exit 1
	}
else
	[[ -d "$DEST" ]] || {
		echo "ERROR: local DEST '$DEST' does not exist (is the backup drive mounted?)." >&2
		exit 1
	}
fi

for src in "$SSD_MEDIA" "$HDD_ARCHIVE"; do
	[[ -d "$src" ]] || {
		echo "ERROR: source '$src' does not exist (is this running on the Orange Pi?)." >&2
		exit 1
	}
done

# Refuse a local destination inside a source, which would nest deeper every run.
if [[ ! ("$DEST" == *:* && "${DEST%%:*}" != */*) ]]; then
	dest_real="$(readlink -f "$DEST")"
	for src in "$SSD_MEDIA" "$HDD_ARCHIVE"; do
		src_real="$(readlink -f "$src")"
		if [[ "$dest_real" == "$src_real" || "$dest_real" == "$src_real"/* ]]; then
			echo "ERROR: DEST '$DEST' is inside source '$src'." >&2
			exit 1
		fi
	done
fi

# With --delete, a source that is really an unmounted mountpoint (an empty dir
# on the eMMC) would erase the backup. Require a separate, non-empty filesystem.
if [[ "$DELETE" == "1" ]]; then
	root_dev="$(findmnt -no SOURCE -T / 2>/dev/null || true)"
	for src in "$SSD_MEDIA" "$HDD_ARCHIVE"; do
		src_dev="$(findmnt -no SOURCE -T "$src" 2>/dev/null || true)"
		if [[ -z "$src_dev" || "$src_dev" == "$root_dev" ]]; then
			echo "ERROR: refusing --delete: '$src' is not on a separate mounted filesystem." >&2
			exit 1
		fi
		if [[ -z "$(find "$src" -mindepth 1 -maxdepth 1 -print -quit)" ]]; then
			echo "ERROR: refusing --delete: source '$src' is empty." >&2
			exit 1
		fi
	done
fi

RSYNC_ARGS=(
	-aH
	--partial
	--human-readable
	--exclude='$RECYCLE.BIN'
	--exclude='System Volume Information'
	--exclude='*.tmp'
	--exclude='.Trash-*'
	--exclude='.DS_Store'
)
if [[ -t 1 ]]; then
	RSYNC_ARGS+=(--info=progress2)
fi
# shellcheck disable=SC2206
[[ -n "$RSYNC_EXTRA" ]] && RSYNC_ARGS+=($RSYNC_EXTRA)
[[ "$DELETE" == "1" ]] && RSYNC_ARGS+=(--delete)
[[ "$DRY_RUN" == "1" ]] && RSYNC_ARGS+=(--dry-run)

if [[ "$DRY_RUN" == "1" ]]; then
	echo "== Dry run: no files will be written =="
fi
if [[ "$DELETE" != "1" ]]; then
	echo "== Note: --delete is off; files removed from the source stay in the target =="
fi

echo "== $SSD_MEDIA/ -> $DEST/ssd-media/ =="
rsync "${RSYNC_ARGS[@]}" "$SSD_MEDIA/" "$DEST/ssd-media/"

echo "== $HDD_ARCHIVE/ -> $DEST/hdd/ =="
rsync "${RSYNC_ARGS[@]}" "$HDD_ARCHIVE/" "$DEST/hdd/"

echo
echo "Media backup complete."
