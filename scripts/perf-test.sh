#!/usr/bin/env bash
set -euo pipefail

# Performance test: stream N concurrent direct-play videos and sample CPU/RAM.
#
# Usage:
#   On the server (has Docker):
#     JELLYFIN_API_KEY=<key> ./perf-test.sh
#   From the dev laptop (SSH to the server):
#     ssh dzhang@orangepi3b.local 'JELLYFIN_API_KEY=<key> bash -s' < scripts/perf-test.sh
#
# Env vars:
#   JELLYFIN_URL       Jellyfin API base (default http://orangepi3b.local/api)
#   JELLYFIN_API_KEY   required
#   STREAMS            concurrent streams (default 3)
#   RATE_LIMIT         per-stream curl --limit-rate (e.g. 20M); empty = full speed (default 20M)
#   DURATION           seconds to stream (default 20)
#   SAMPLE_INTERVAL    seconds between samples (default 2)
#   CONTAINER          Docker container to sample (default jellyfin)

JELLYFIN_URL="${JELLYFIN_URL:-http://orangepi3b.local/api}"
API_KEY="${JELLYFIN_API_KEY:-}"
STREAMS="${STREAMS:-3}"
RATE_LIMIT="${RATE_LIMIT:-20M}"
DURATION="${DURATION:-20}"
SAMPLE_INTERVAL="${SAMPLE_INTERVAL:-2}"
CONTAINER="${CONTAINER:-jellyfin}"

if [[ -z "$API_KEY" ]]; then
	echo "ERROR: JELLYFIN_API_KEY is required." >&2
	echo "Usage: JELLYFIN_API_KEY=<key> [STREAMS=3] [RATE_LIMIT=20M] [DURATION=20] $0" >&2
	exit 1
fi

for var in STREAMS DURATION SAMPLE_INTERVAL; do
	if [[ ! "${!var}" =~ ^[0-9]+$ ]]; then
		echo "ERROR: $var must be a positive integer (got '${!var}')." >&2
		exit 1
	fi
done

AUTH=(-H "X-Emby-Token: $API_KEY")

jget() {
	python3 -c 'import sys, json
d = json.load(sys.stdin)
for k in sys.argv[1].split("."):
    d = d[int(k)] if k.isdigit() else d[k]
print(d)' "$1" 2>/dev/null || echo ""
}

# /proc/stat first line: cpu user nice system idle iowait irq softirq steal guest guest_nice
# total  = user+nice+system+idle+iowait+irq+softirq+steal (guest/guest_nice are subsets, skip)
# busy   = total - idle  (includes iowait/irq/softirq, which dominate I/O-bound direct-play load)
read_total() { awk 'NR==1 { print $2+$3+$4+$5+$6+$7+$8+$9 }' /proc/stat; }
read_busy() { awk 'NR==1 { print $2+$3+$4+$6+$7+$8+$9 }' /proc/stat; }

echo "== Finding a direct-play movie =="
user_id=$(curl -s "${AUTH[@]}" "$JELLYFIN_URL/Users" | jget '0.Id')
movie_ids=$(curl -s "${AUTH[@]}" \
	"$JELLYFIN_URL/Users/$user_id/Items?IncludeItemTypes=Movie&Recursive=true&Limit=20" \
	| python3 -c 'import sys,json; print("\n".join(i["Id"] for i in json.load(sys.stdin).get("Items",[])))' 2>/dev/null || echo "")

movie_id=""
media_source_id=""
movie_name=""
bitrate=""
container=""
while IFS= read -r mid; do
	[[ -n "$mid" ]] || continue
	info=$(curl -s -X POST -H "Content-Type: application/json" -d '{}' "${AUTH[@]}" \
		"$JELLYFIN_URL/Items/$mid/PlaybackInfo")
	supports=$(echo "$info" | jget 'MediaSources.0.SupportsDirectPlay')
	if [[ "$supports" == "True" ]]; then
		movie_id="$mid"
		media_source_id=$(echo "$info" | jget 'MediaSources.0.Id')
		bitrate=$(echo "$info" | jget 'MediaSources.0.Bitrate')
		container=$(echo "$info" | jget 'MediaSources.0.Container')
		movie_name=$(curl -s "${AUTH[@]}" "$JELLYFIN_URL/Users/$user_id/Items/$mid" | jget 'Name')
		break
	fi
done <<< "$movie_ids"

if [[ -z "$movie_id" ]]; then
	echo "ERROR: no direct-play movie found." >&2
	exit 1
fi

echo "   movie: $movie_name ($container, bitrate ${bitrate:-?} bps)"
echo "   streams: $STREAMS, rate: ${RATE_LIMIT:-full speed}, duration: ${DURATION}s"

URL="$JELLYFIN_URL/Videos/$movie_id/stream?static=true&MediaSourceId=$media_source_id&api_key=$API_KEY"

CURL_ARGS=(-s -o /dev/null --max-time "$DURATION")
[[ -n "$RATE_LIMIT" ]] && CURL_ARGS+=(--limit-rate "$RATE_LIMIT")

echo
echo "== Baseline =="
echo "   loadavg: $(cut -d' ' -f1-3 /proc/loadavg)"
free -m | awk 'NR==2 { printf "   host mem: %s used / %s total\n", $3, $2 }'
docker stats --no-stream --format "   {{.Name}} cpu={{.CPUPerc}} mem={{.MemUsage}}" "$CONTAINER" 2>/dev/null \
	|| echo "   (container '$CONTAINER' not available)"

T1=$(read_total); B1=$(read_busy)

echo
echo "== Streaming $STREAMS concurrent direct-play stream(s) for ${DURATION}s =="
for ((n = 1; n <= STREAMS; n++)); do
	curl "${CURL_ARGS[@]}" "$URL" &
done

end=$((SECONDS + DURATION))
while ((SECONDS < end)); do
	docker stats --no-stream --format "   t=$(date +%T) cpu={{.CPUPerc}} mem={{.MemUsage}} net={{.NetIO}}" "$CONTAINER" 2>/dev/null || true
	sleep "$SAMPLE_INTERVAL"
done
wait

T2=$(read_total); B2=$(read_busy)
DT=$((T2 - T1)); DB=$((B2 - B1))
busy_pct=$(awk -v db="$DB" -v dt="$DT" 'BEGIN { if (dt > 0) printf "%.1f", db*100/dt; else print "0.0" }')

echo
echo "== Results =="
echo "   host CPU busy over window: ${busy_pct}%"
echo "   loadavg (1/5/15m): $(cut -d' ' -f1-3 /proc/loadavg)"
free -m | awk 'NR==2 { printf "   host mem: %s used / %s total (%s free)\n", $3, $2, $4 }'
