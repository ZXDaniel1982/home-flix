#!/usr/bin/env bash
set -euo pipefail

# Deploy the SvelteKit web frontend to the Orange Pi.
#
# Builds the static site on this machine and rsyncs it into the directory
# Caddy serves on the board. No Caddy reload is needed: the files are read
# directly from the bind mount, so changes take effect as soon as they land.
#
# Usage:
#   scripts/deploy-web.sh
#   SERVER=orangepi3b.local SSH_USER=dzhang scripts/deploy-web.sh
#   DRY_RUN=1 scripts/deploy-web.sh   # build + rsync dry-run, no remote writes
#
# Env vars:
#   SERVER       Orange Pi hostname (default orangepi3b.local)
#   SSH_USER     SSH user on the board (default dzhang)
#   REMOTE_DIR   Directory Caddy serves from (default /mnt/ssd/web-frontend)
#   WEB_DIR      SvelteKit project dir (default <repo>/web-frontend)
#   DRY_RUN      set to 1 to skip remote writes (rsync --dry-run)

SERVER="${SERVER:-orangepi3b.local}"
SSH_USER="${SSH_USER:-dzhang}"
REMOTE_DIR="${REMOTE_DIR:-/mnt/ssd/web-frontend}"
DRY_RUN="${DRY_RUN:-0}"

usage() {
	cat <<'EOF'
Deploy the SvelteKit web frontend to the Orange Pi.

Builds the static site on this machine and rsyncs it into the directory
Caddy serves on the board. No Caddy reload is needed: the files are read
directly from the bind mount, so changes take effect as soon as they land.

Usage:
  scripts/deploy-web.sh
  SERVER=orangepi3b.local SSH_USER=dzhang scripts/deploy-web.sh
  DRY_RUN=1 scripts/deploy-web.sh   # build + rsync dry-run, no remote writes

Env vars:
  SERVER       Orange Pi hostname (default orangepi3b.local)
  SSH_USER     SSH user on the board (default dzhang)
  REMOTE_DIR   Directory Caddy serves from (default /mnt/ssd/web-frontend)
  WEB_DIR      SvelteKit project dir (default <repo>/web-frontend)
  DRY_RUN      set to 1 to skip remote writes (rsync --dry-run)
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

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="${WEB_DIR:-$REPO_ROOT/web-frontend}"
BUILD_DIR="$WEB_DIR/build"
DEST="$SSH_USER@$SERVER:$REMOTE_DIR/"

for cmd in npm ssh; do
	command -v "$cmd" >/dev/null 2>&1 || {
		echo "ERROR: '$cmd' is not installed." >&2
		exit 1
	}
done

if ! command -v rsync >/dev/null 2>&1; then
	echo "ERROR: 'rsync' is required on this machine and on the Orange Pi." >&2
	echo "       Arch:  sudo pacman -S rsync" >&2
	echo "       Pi:    sudo apt install -y rsync" >&2
	exit 1
fi

[[ -f "$WEB_DIR/package.json" ]] || {
	echo "ERROR: no package.json in $WEB_DIR (set WEB_DIR?)." >&2
	exit 1
}

echo "== Building web frontend ($WEB_DIR) =="
(
	cd "$WEB_DIR"
	if [[ -f package-lock.json ]]; then
		npm ci
	else
		npm install
	fi
	npm run build
)

[[ -f "$BUILD_DIR/index.html" ]] || {
	echo "ERROR: build did not produce $BUILD_DIR/index.html." >&2
	exit 1
}

RSYNC_ARGS=(-avz --delete)
if [[ "$DRY_RUN" == "1" ]]; then
	RSYNC_ARGS+=(--dry-run)
	echo "== Dry run: not writing to $DEST =="
fi

echo
echo "== Syncing $BUILD_DIR/ -> $DEST =="
rsync "${RSYNC_ARGS[@]}" "$BUILD_DIR/" "$DEST"

if [[ "$DRY_RUN" == "1" ]]; then
	echo
	echo "Dry run complete. Re-run without DRY_RUN=1 to deploy."
else
	echo
	echo "Deployed. Open http://$SERVER/"
fi
