#!/usr/bin/env bash
# Smoke test against a running service (local or Cloud Run), P0-17 checklist.
# Resets the demo, seeks to the end, checks the three events, the context, a review and an export.
# Usage: scripts/smoke-test.sh [base_url]   (default http://localhost:8080)
set -uo pipefail
cd "$(dirname "$0")/.."
BASE="${1:-${PN_BASE_URL:-http://localhost:8080}}"
if [[ -z "${PN_WRITE_API_KEY:-}" && -f config/.env ]]; then
  PN_WRITE_API_KEY="$(grep -E '^PN_WRITE_API_KEY=' config/.env | cut -d= -f2- || true)"
fi
KEY="${PN_WRITE_API_KEY:-}"
M="${PN_DEMO_MISSION_ID:-HACK-001}"
API="$BASE/api/v1"
PASS=0; FAIL=0

check() { # name, command producing output, expected substring
  local name="$1" out="$2" want="$3"
  if [[ "$out" == *"$want"* ]]; then echo "PASS  $name"; PASS=$((PASS+1));
  else echo "FAIL  $name (wanted '$want')"; echo "      got: ${out:0:300}"; FAIL=$((FAIL+1)); fi
}
w() { curl -sS -X POST "$API$1" -H "Content-Type: application/json" -H "X-PN-Key: $KEY" -d "$2"; }
r() { curl -sS "$API$1"; }

check "health"            "$(curl -sS "$BASE/actuator/health")"                    '"status":"UP"'
check "reset"             "$(w /demo/reset "{\"mission_id\":\"$M\"}")"            '"state":"idle"'
check "mission"           "$(r /missions/$M)"                                       "\"mission_id\":\"$M\""
check "seek to end"       "$(w /missions/$M/replay '{"action":"seek","to":"2026-10-24T15:04:00Z"}')" '"state":"ended"'
EVENTS="$(r "/events?mission_id=$M")"
check "EV-0142"           "$EVENTS" '"EV-0142"'
check "EV-0143"           "$EVENTS" '"EV-0143"'
check "EV-0144"           "$EVENTS" '"EV-0144"'
check "EV-0143 context"   "$(r /events/EV-0143)" '"relation":"coincides_with"'
check "frame served"      "$(curl -sS -o /dev/null -w '%{http_code}' "$BASE/media/surface/SURF-003210.jpg")" '200'
check "review"            "$(w /events/EV-0143/review "{\"action\":\"confirm\",\"reviewer\":\"smoke\",\"request_id\":\"smoke-$(date +%s)\"}")" '"status":"confirmed"'
check "report"            "$(r /missions/$M/report)" '"events_total":3'
EXPORT="$(w /missions/$M/export '{"format":"csv"}')"
check "export csv"        "$EXPORT" '"file_uri":"/exports/'
URI="$(sed -E 's/.*"file_uri":"([^"]+)".*/\1/' <<<"$EXPORT")"
check "export served"     "$(curl -sS "$BASE$URI" | head -c 200)" 'event_id'
check "write needs key"   "$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$API/demo/reset" -H 'Content-Type: application/json' -d '{}')" "$([[ -n "$KEY" ]] && echo 401 || echo 200)"
UI="$(curl -sS -o /dev/null -w '%{http_code}' "$BASE/")"
if [[ "$UI" == 200 ]]; then echo "PASS  ui index"; PASS=$((PASS+1));
else echo "WARN  ui index returned $UI (UI not bundled yet, step I-01)"; fi

# Leave the demo clean for the next presenter.
w /demo/reset "{\"mission_id\":\"$M\"}" >/dev/null
echo "----"; echo "$PASS passed, $FAIL failed"
[[ $FAIL -eq 0 ]]
