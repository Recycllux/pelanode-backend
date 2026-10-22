#!/usr/bin/env bash
# Step I-01: build the Lovable UI (Recycllux/pelanode-mission-portal) in http mode and copy it into
# src/main/resources/static, so the jar and the Docker image serve UI + API from one origin.
# The bundle is committed so that a fresh clone runs with the UI; rerun this script and commit the
# result after UI changes. The UI's source of truth stays in its own repository.
# Usage: scripts/build-ui.sh [path-to-pelanode-mission-portal]   (default ../pelanode-mission-portal)
# Needs bun (https://bun.sh); npm is the fallback.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
UI_SRC="$(cd "${1:-$ROOT/../pelanode-mission-portal}" && pwd)"
DEST="$ROOT/src/main/resources/static"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "UI source: $UI_SRC ($(git -C "$UI_SRC" rev-parse --short HEAD 2>/dev/null || echo 'no git'))"
# Build from a clean copy of the committed files, so local edits and node_modules don't leak in.
if git -C "$UI_SRC" rev-parse HEAD >/dev/null 2>&1; then
  git -C "$UI_SRC" archive HEAD | tar -x -C "$WORK"
else
  cp -a "$UI_SRC/." "$WORK/"; rm -rf "$WORK/node_modules" "$WORK/dist"
fi
cd "$WORK"
# Lovable's lockfile points some packages at its private npm cache (403 outside Lovable): use public npm.
if [[ -f bun.lock ]]; then
  sed -i.bak 's#https://europe-west4-npm.pkg.dev/lovable-core-prod/sandbox-npm-cache/#https://registry.npmjs.org/#g' bun.lock
fi

export VITE_API_MODE=http VITE_API_BASE_URL=""
if command -v bun >/dev/null 2>&1; then
  bun install --frozen-lockfile
  bun run build
else
  npm install --no-audit --no-fund
  npm run build
fi

[[ -f dist/client/index.html ]] || { echo "build produced no dist/client/index.html" >&2; exit 1; }
rm -rf "$DEST"
mkdir -p "$DEST"
cp -a dist/client/. "$DEST/"
echo "UI copied to $DEST ($(find "$DEST" -type f | wc -l) files)"
