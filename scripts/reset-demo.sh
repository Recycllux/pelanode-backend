#!/usr/bin/env bash
# Reset the demo mission: clears ingested data, reseeds, replay back to idle (P0-17).
# Usage: scripts/reset-demo.sh [base_url]   (default http://localhost:8080)
# Needs PN_WRITE_API_KEY in the environment or in config/.env when the server has a key set.
set -euo pipefail
cd "$(dirname "$0")/.."
BASE="${1:-${PN_BASE_URL:-http://localhost:8080}}"
if [[ -z "${PN_WRITE_API_KEY:-}" && -f config/.env ]]; then
  PN_WRITE_API_KEY="$(grep -E '^PN_WRITE_API_KEY=' config/.env | cut -d= -f2- || true)"
fi
MISSION="${PN_DEMO_MISSION_ID:-HACK-001}"

curl -fsS -X POST "$BASE/api/v1/demo/reset" \
  -H "Content-Type: application/json" -H "X-PN-Key: ${PN_WRITE_API_KEY:-}" \
  -d "{\"mission_id\":\"$MISSION\"}"
echo
