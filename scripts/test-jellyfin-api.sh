#!/usr/bin/env bash
set -euo pipefail

JELLYFIN_URL="${JELLYFIN_URL:-http://orangepi3b.local/api}"
API_KEY="${JELLYFIN_API_KEY:-}"

if [[ -z "$API_KEY" ]]; then
	echo "ERROR: JELLYFIN_API_KEY is required." >&2
	echo "Usage: JELLYFIN_API_KEY=<key> [JELLYFIN_URL=<url>] $0" >&2
	exit 1
fi

AUTH=(-H "X-Emby-Token: $API_KEY")
FAILED=0

check() {
	local desc="$1" expected="$2" actual="$3"
	if [[ "$actual" == "$expected" ]]; then
		echo "PASS: $desc"
	else
		echo "FAIL: $desc (expected $expected, got $actual)"
		FAILED=1
	fi
}

jget() {
	python3 -c 'import sys, json
d = json.load(sys.stdin)
for k in sys.argv[1].split("."):
    d = d[int(k)] if k.isdigit() else d[k]
print(d)' "$1" 2>/dev/null || echo ""
}

echo "== 1. Auth: API key accepted =="
users_status=$(curl -s -o /tmp/jf_users.json -w '%{http_code}' "${AUTH[@]}" "$JELLYFIN_URL/Users")
check "GET /Users returns 200 with API key" "200" "$users_status"
user_id=$(jget '0.Id' < /tmp/jf_users.json)
echo "   userId: $user_id"

echo "== 2. Auth: rejected without credentials =="
noauth_status=$(curl -s -o /dev/null -w '%{http_code}' "$JELLYFIN_URL/Users")
check "GET /Users returns 401 without auth" "401" "$noauth_status"

echo "== 3. Library: list movies =="
items_status=$(curl -s -o /tmp/jf_items.json -w '%{http_code}' "${AUTH[@]}" \
	"$JELLYFIN_URL/Users/$user_id/Items?IncludeItemTypes=Movie&Recursive=true")
check "GET /Users/{id}/Items returns 200" "200" "$items_status"
item_count=$(python3 -c 'import json; print(len(json.load(open("/tmp/jf_items.json")).get("Items", [])))' 2>/dev/null || echo 0)
first_movie_id=$(jget 'Items.0.Id' < /tmp/jf_items.json)
echo "   movies: $item_count (first id: $first_movie_id)"

echo "== 4. Playback info =="
if [[ -n "$first_movie_id" && "$first_movie_id" != "None" ]]; then
	playback_status=$(curl -s -o /tmp/jf_playback.json -w '%{http_code}' -X POST \
		-H "Content-Type: application/json" -d '{}' "${AUTH[@]}" \
		"$JELLYFIN_URL/Items/$first_movie_id/PlaybackInfo")
	check "POST /Items/{id}/PlaybackInfo returns 200" "200" "$playback_status"
	media_sources=$(python3 -c 'import json; print(len(json.load(open("/tmp/jf_playback.json")).get("MediaSources", [])))' 2>/dev/null || echo 0)
	echo "   media sources: $media_sources"
else
	echo "SKIP: no movies found to test playback info"
fi

echo
if [[ "$FAILED" -eq 0 ]]; then
	echo "All checks passed."
else
	echo "Some checks failed."
	exit 1
fi
