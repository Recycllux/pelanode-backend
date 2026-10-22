#!/usr/bin/env bash
# Run the backend locally on http://localhost:8080 (profile "local", H2 file in .data).
# Loads config/.env if present. Usage: scripts/run-local.sh [--clean]
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ "${1:-}" == "--clean" ]]; then
  echo "Removing .data (local DB and exports)"
  rm -rf .data
fi

if [[ -f config/.env ]]; then
  set -a; source config/.env; set +a
fi

if [[ -x ./mvnw ]] && ./mvnw -v >/dev/null 2>&1; then MVN=./mvnw; else MVN=mvn; fi
exec "$MVN" -q spring-boot:run
