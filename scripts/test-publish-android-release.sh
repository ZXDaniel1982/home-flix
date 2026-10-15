#!/usr/bin/env bash
set -euo pipefail

# Smoke test for publish-android-release.sh. Skips the Gradle build (SKIP_BUILD=1),
# uses a stub ./gradlew for version info, and replaces rsync with a shim that
# copies locally instead of connecting to the Pi. Run from anywhere:
#   scripts/test-publish-android-release.sh

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
publish="$script_dir/publish-android-release.sh"

work="$(mktemp -d "${TMPDIR:-/tmp}/publish-android-test.XXXXXX")"
trap 'rm -rf "$work"' EXIT

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
expect_contains() {
	local desc="$1" needle="$2" haystack="$3"
	if grep -qF "$needle" <<<"$haystack"; then
		echo "ok   - $desc"
	else
		echo "FAIL - $desc (missing '$needle')"
		fails=$((fails + 1))
	fi
}

# --- Guard rails (checked before any build, so these are fast) -------------
rc=0
"$publish" --bogus >/dev/null 2>&1 || rc=$?
expect_rc "unknown argument exits 2" 2 "$rc"

rc=0
REMOTE_DIR=relative "$publish" >/dev/null 2>&1 || rc=$?
expect_rc "relative REMOTE_DIR exits 1" 1 "$rc"

rc=0
REMOTE_DIR=/mnt/ssd "$publish" >/dev/null 2>&1 || rc=$?
expect_rc "REMOTE_DIR outside the release dir exits 1" 1 "$rc"

# A plausible-but-broader destination: the mirror would delete the web frontend.
rc=0
REMOTE_DIR=/mnt/ssd/web-frontend "$publish" >/dev/null 2>&1 || rc=$?
expect_rc "web root as REMOTE_DIR exits 1" 1 "$rc"

# --- Manifest generation with a fixture APK, stub gradlew, fake rsync ------
android_dir="$work/android-app"
mkdir -p "$android_dir/app/build/outputs/apk/release"
printf 'not-a-real-apk-but-fine-for-hashing' >"$android_dir/app/build/outputs/apk/release/app-release.apk"

cat >"$android_dir/gradlew" <<'EOF'
#!/usr/bin/env bash
for arg in "$@"; do
	if [[ "$arg" == ":app:printVersionInfo" ]]; then
		echo "2 0.2.0 24"
		exit 0
	fi
done
exit 0
EOF
chmod +x "$android_dir/gradlew"

shim="$work/bin"
mkdir -p "$shim"
rsync_real="$(command -v rsync || true)"
rsync_real="$(cd "$(dirname "$rsync_real")" && pwd)/$(basename "$rsync_real")"
cat >"$shim/rsync" <<EOF
#!/usr/bin/env bash
# Rewrite the remote destination to a local path, then run the real rsync so
# --delete/--exclude semantics are genuinely exercised.
args=("\$@")
dest="\${args[-1]}"
local_dest="\${dest#*:}"
exec "$rsync_real" "\${args[@]:0:\${#args[@]}-1}" "\$local_dest"
EOF
chmod +x "$shim/rsync"

stage="$work/stage"
# REMOTE_DIR must end with /updates/android (the mirror is destructive).
remote="$work/srv/updates/android"
# Pre-seed the destination with a previously published release, so the mirror
# step has something to prune.
mkdir -p "$remote"
printf 'old-apk\n' >"$remote/home-flix-0.1.0-1.apk"
printf '{"schemaVersion":1,"versionCode":1}\n' >"$remote/manifest.json"

rc=0
PATH="$shim:$PATH" SKIP_BUILD=1 ANDROID_DIR="$android_dir" STAGE_DIR="$stage" \
	SERVER=localhost SSH_USER=test REMOTE_DIR="$remote" \
	"$publish" >"$work/out.log" 2>&1 || rc=$?
expect_rc "publish with SKIP_BUILD exits 0" 0 "$rc"

manifest="$stage/manifest.json"
if [[ -f "$manifest" ]]; then
	echo "ok   - manifest.json written"
	body="$(cat "$manifest")"
	expect_contains "manifest has versionCode 2" '"versionCode": 2' "$body"
	expect_contains "manifest has versionName 0.2.0" '"versionName": "0.2.0"' "$body"
	expect_contains "manifest has minSdk 24" '"minSdk": 24' "$body"
	expect_contains "manifest points at the versioned APK" 'home-flix-0.2.0-2.apk' "$body"
	expect_contains "manifest carries a sha256" '"sha256"' "$body"
else
	echo "FAIL - manifest.json was not written to $stage"
	fails=$((fails + 1))
fi

if [[ -f "$remote/home-flix-0.2.0-2.apk" && -f "$remote/manifest.json" ]]; then
	echo "ok   - APK and manifest were synced"
else
	echo "FAIL - APK and manifest were not synced to $remote"
	fails=$((fails + 1))
fi

# The remote is mirrored, so only the current release survives.
if [[ ! -e "$remote/home-flix-0.1.0-1.apk" ]]; then
	echo "ok   - previous APK was pruned"
else
	echo "FAIL - previous APK still present ($remote/home-flix-0.1.0-1.apk)"
	fails=$((fails + 1))
fi

apk_count="$(find "$remote" -maxdepth 1 -name '*.apk' | wc -l | tr -d ' ')"
if [[ "$apk_count" == "1" ]]; then
	echo "ok   - exactly one APK is hosted"
else
	echo "FAIL - expected exactly one APK, found $apk_count"
	fails=$((fails + 1))
fi

echo
if [[ "$fails" -eq 0 ]]; then
	echo "All checks passed."
else
	echo "$fails check(s) failed."
	exit 1
fi
