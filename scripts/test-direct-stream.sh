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

user_id=$(curl -s "${AUTH[@]}" "$JELLYFIN_URL/Users" | jget '0.Id')
movie_id=$(curl -s "${AUTH[@]}" "$JELLYFIN_URL/Users/$user_id/Items?IncludeItemTypes=Movie&Recursive=true" | jget 'Items.0.Id')

if [[ -z "$movie_id" || "$movie_id" == "None" ]]; then
	echo "FAIL: no movies found to test"
	exit 1
fi

echo "== Direct play support (H.264 MP4) =="
playback=$(curl -s "${AUTH[@]}" -X POST -H "Content-Type: application/json" -d '{}' \
	"$JELLYFIN_URL/Items/$movie_id/PlaybackInfo")
supports_direct=$(echo "$playback" | jget 'MediaSources.0.SupportsDirectPlay')
container=$(echo "$playback" | jget 'MediaSources.0.Container')
media_source_id=$(echo "$playback" | jget 'MediaSources.0.Id')

check "MediaSources[0].SupportsDirectPlay is true" "True" "$supports_direct"
echo "   container: $container"

echo "== Direct stream endpoint =="
read -r stream_status content_type size <<< "$(curl -s -o /tmp/jf_stream.bin \
	-w '%{http_code} %{content_type} %{size_download}' \
	"$JELLYFIN_URL/Videos/$movie_id/stream?static=true&MediaSourceId=$media_source_id&api_key=$API_KEY")"
check "GET /Videos/{id}/stream returns 200" "200" "$stream_status"

if [[ "$content_type" == video/mp4* ]]; then
	echo "PASS: content-type is video/mp4 ($content_type)"
else
	echo "FAIL: content-type is $content_type (expected video/mp4)"
	FAILED=1
fi

if [[ "$size" -gt 0 ]]; then
	echo "PASS: stream body is non-empty ($size bytes)"
else
	echo "FAIL: stream body is empty"
	FAILED=1
fi

echo
echo "H.265 MKV (not tested — no sample file, no ffmpeg): expected SupportsDirectPlay=false,"
echo "and since transcoding is disabled, H.265 files cannot be played. Only H.264/AAC MP4 is supported."

echo
if [[ "$FAILED" -eq 0 ]]; then
	echo "All checks passed."
else
	echo "Some checks failed."
	exit 1
fi
