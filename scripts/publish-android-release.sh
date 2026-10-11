#!/usr/bin/env bash
set -euo pipefail

# Publish a signed Android release to the Orange Pi so installed apps can
# update themselves. Builds the release APK here, writes
# updates/android/manifest.json, then rsyncs both into the directory Caddy
# serves. No Caddy reload is needed (bind mount).
#
# Usage:
#   scripts/publish-android-release.sh
#   NOTES=release-notes.md scripts/publish-android-release.sh
#   DRY_RUN=1 scripts/publish-android-release.sh
#
# Env vars:
#   SERVER       Orange Pi hostname (default orangepi3b.local)
#   SSH_USER     SSH user on the board (default dzhang)
#   REMOTE_DIR   Directory Caddy serves releases from
#                (default /mnt/ssd/web-frontend/updates/android)
#   ANDROID_DIR  Gradle project dir (default <repo>/android-app)
#   NOTES        Optional release-notes file
#   SKIP_BUILD   set to 1 to reuse the existing release APK
#   STAGE_DIR    Staging dir (default <repo>/.update-stage)
#   DRY_RUN      set to 1 to skip remote writes

SERVER="${SERVER:-orangepi3b.local}"
SSH_USER="${SSH_USER:-dzhang}"
REMOTE_DIR="${REMOTE_DIR:-/mnt/ssd/web-frontend/updates/android}"
DRY_RUN="${DRY_RUN:-0}"
SKIP_BUILD="${SKIP_BUILD:-0}"

usage() {
	cat <<'EOF'
Publish a signed Android release to the Orange Pi.

Env vars:
  SERVER       Orange Pi hostname (default orangepi3b.local)
  SSH_USER     SSH user on the board (default dzhang)
  REMOTE_DIR   Directory Caddy serves releases from
               (default /mnt/ssd/web-frontend/updates/android)
  ANDROID_DIR  Gradle project dir (default <repo>/android-app)
  NOTES        Optional release-notes file
  SKIP_BUILD   set to 1 to reuse the existing release APK
  STAGE_DIR    Staging dir (default <repo>/.update-stage)
  DRY_RUN      set to 1 to skip remote writes
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
ANDROID_DIR="${ANDROID_DIR:-$REPO_ROOT/android-app}"
STAGE_DIR="${STAGE_DIR:-$REPO_ROOT/.update-stage}"
APK_PATH="$ANDROID_DIR/app/build/outputs/apk/release/app-release.apk"
STAGE="$STAGE_DIR"
DEST="$SSH_USER@$SERVER:$REMOTE_DIR/"

for cmd in ssh rsync sha256sum python3; do
	command -v "$cmd" >/dev/null 2>&1 || {
		echo "ERROR: '$cmd' is not installed." >&2
		exit 1
	}
done

if [[ "$REMOTE_DIR" != /* ]]; then
	echo "ERROR: REMOTE_DIR must be an absolute path (got '$REMOTE_DIR')." >&2
	exit 1
fi
case "$REMOTE_DIR" in
"" | "/" | "/mnt" | "/mnt/ssd" | "/home" | "/root" | "/etc" | "/var" | "/usr")
	echo "ERROR: refusing to write into unsafe REMOTE_DIR '$REMOTE_DIR'." >&2
	exit 1
	;;
esac

if [[ "$SKIP_BUILD" != "1" ]]; then
	echo "== Building signed release APK =="
	(cd "$ANDROID_DIR" && ./gradlew clean :app:assembleRelease)
fi

[[ -f "$APK_PATH" ]] || {
	echo "ERROR: no APK at $APK_PATH (build failed, or SKIP_BUILD=1 too early)." >&2
	exit 1
}

version_info="$(cd "$ANDROID_DIR" && ./gradlew -q :app:printVersionInfo)"
read -r version_code version_name min_sdk <<<"$version_info"
[[ -n "$version_code" && -n "$version_name" && -n "$min_sdk" ]] || {
	echo "ERROR: could not read version info (got '$version_info')." >&2
	exit 1
}

# The filename carries both the version name and the version code. Caddy serves
# APKs with `Cache-Control: immutable` for a year, so a name reused across two
# different builds (e.g. versionCode bumped without versionName) would let a
# cache hand out the old bytes, which then fail SHA-256 verification on the
# device. Including versionCode makes every release URL unique.
apk_name="home-flix-$version_name-$version_code.apk"
apk_url="/updates/android/$apk_name"

rm -rf "$STAGE"
mkdir -p "$STAGE"
cp "$APK_PATH" "$STAGE/$apk_name"
size="$(stat -c %s "$STAGE/$apk_name")"
sha="$(sha256sum "$STAGE/$apk_name" | awk '{print $1}')"

VERSION_CODE="$version_code" VERSION_NAME="$version_name" MIN_SDK="$min_sdk" \
	APK_URL="$apk_url" SHA256="$sha" SIZE="$size" NOTES="${NOTES:-}" \
	python3 - "$STAGE/manifest.json" <<'PY'
import json, os, sys

notes_path = os.environ.get("NOTES") or ""
notes = ""
if notes_path and os.path.exists(notes_path):
    with open(notes_path, encoding="utf-8") as handle:
        notes = handle.read().strip()

manifest = {
    "schemaVersion": 1,
    "versionCode": int(os.environ["VERSION_CODE"]),
    "versionName": os.environ["VERSION_NAME"],
    "minSdk": int(os.environ["MIN_SDK"]),
    "apkUrl": os.environ["APK_URL"],
    "sha256": os.environ["SHA256"],
    "size": int(os.environ["SIZE"]),
}
if notes:
    manifest["releaseNotes"] = notes

with open(sys.argv[1], "w", encoding="utf-8") as handle:
    json.dump(manifest, handle, indent=2)
    handle.write("\n")
PY

echo
echo "== Release $version_name ($version_code), minSdk $min_sdk =="
echo "   APK:      $apk_name ($size bytes)"
echo "   SHA-256:  $sha"

RSYNC_ARGS=(-avz)
if [[ "$DRY_RUN" == "1" ]]; then
	RSYNC_ARGS+=(--dry-run)
	echo "== Dry run: not writing to $DEST =="
fi

rsync "${RSYNC_ARGS[@]}" "$STAGE/" "$DEST"

if [[ "$DRY_RUN" == "1" ]]; then
	echo
	echo "Dry run complete. Re-run without DRY_RUN=1 to publish."
else
	echo
	echo "Published. Check for updates in the app, or fetch:"
	echo "  http://$SERVER/updates/android/manifest.json"
fi
