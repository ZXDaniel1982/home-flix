#!/usr/bin/env bash
set -euo pipefail

# Smoke test for deploy-web.sh: exercises the rsync step against a local
# destination (via a shim that rewrites the remote spec) and asserts that the
# Android release artifacts under updates/ survive the --delete, while stale
# frontend files are still removed. Run from anywhere:
#   scripts/test-deploy-web.sh
#
# Regression guard for: a routine web deploy must never wipe
# updates/android/manifest.json and the published APKs.

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
deploy="$script_dir/deploy-web.sh"

# Capture the real rsync before the shim shadows it on PATH.
rsync_real="$(command -v rsync || true)"
if [[ -z "$rsync_real" ]]; then
	echo "SKIP: rsync is not installed; cannot exercise the deploy step." >&2
	exit 0
fi
rsync_real="$(cd "$(dirname "$rsync_real")" && pwd)/$(basename "$rsync_real")"

work="$(mktemp -d "${TMPDIR:-/tmp}/deploy-web-test.XXXXXX")"
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

# --- Fixture: a prebuilt frontend and a destination with release artifacts ---
web_dir="$work/web-frontend"
build_dir="$web_dir/build"
mkdir -p "$build_dir/_app/immutable"
printf '{}\n' >"$web_dir/package.json"
printf '<html>new</html>\n' >"$build_dir/index.html"
printf 'console.log(1)\n' >"$build_dir/_app/immutable/abc.js"

remote="$work/remote"
mkdir -p "$remote/updates/android"
printf '{"schemaVersion":1}\n' >"$remote/updates/android/manifest.json"
printf 'apk-bytes\n' >"$remote/updates/android/home-flix-0.1.0-1.apk"
printf 'stale\n' >"$remote/old-asset.html"

# --- Stubs: npm (build is pre-made) and rsync (rewrite remote -> local) ------
shim="$work/bin"
mkdir -p "$shim"
printf '#!/usr/bin/env bash\nexit 0\n' >"$shim/npm"
chmod +x "$shim/npm"
cat >"$shim/rsync" <<EOF
#!/usr/bin/env bash
# Rewrite the last argument (user@host:path) to a local path, then run the real
# rsync so --delete/--exclude semantics are genuinely exercised.
args=("\$@")
dest="\${args[-1]}"
local_dest="\${dest#*:}"
exec "$rsync_real" "\${args[@]:0:\${#args[@]}-1}" "\$local_dest"
EOF
chmod +x "$shim/rsync"

# --- Dry run writes nothing -------------------------------------------------
rc=0
PATH="$shim:$PATH" DRY_RUN=1 SERVER=localhost SSH_USER=test \
	REMOTE_DIR="$remote" WEB_DIR="$web_dir" \
	"$deploy" >"$work/dry.log" 2>&1 || rc=$?
expect_rc "dry run exits 0" 0 "$rc"
expect_absent "dry run does not add the built index.html" "$remote/index.html"
expect_exists "dry run leaves existing artifacts alone" "$remote/updates/android/manifest.json"

# --- Real run: deploy must not delete the update artifacts ------------------
rc=0
PATH="$shim:$PATH" SERVER=localhost SSH_USER=test \
	REMOTE_DIR="$remote" WEB_DIR="$web_dir" \
	"$deploy" >"$work/run.log" 2>&1 || rc=$?
expect_rc "deploy exits 0" 0 "$rc"
expect_exists "deploys the built index.html" "$remote/index.html"
expect_exists "deploys hashed assets" "$remote/_app/immutable/abc.js"
expect_absent "deletes a stale frontend file (--delete still active)" "$remote/old-asset.html"
expect_exists "PRESERVES the update manifest across a web deploy" "$remote/updates/android/manifest.json"
expect_exists "PRESERVES the published APK across a web deploy" "$remote/updates/android/home-flix-0.1.0-1.apk"

echo
if [[ "$fails" -eq 0 ]]; then
	echo "All checks passed."
else
	echo "$fails check(s) failed."
	exit 1
fi
