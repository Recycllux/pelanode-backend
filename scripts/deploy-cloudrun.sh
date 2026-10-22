#!/usr/bin/env bash
# Build the image with Cloud Build and deploy to Cloud Run (P0-17).
# One instance only (min=max=1): replay state and H2 live in that instance; a restart equals a reset.
# Usage: GCP_PROJECT=<project> scripts/deploy-cloudrun.sh
# The write key is read from config/.env (PN_WRITE_API_KEY) and passed as an env var; it is never committed.
set -euo pipefail
cd "$(dirname "$0")/.."

: "${GCP_PROJECT:?set GCP_PROJECT}"
REGION="${GCP_REGION:-europe-west3}"
SERVICE="${PN_SERVICE_NAME:-pelanode-poc}"
REPO="${GCP_AR_REPO:-pelanode}"
TAG="$(git rev-parse --short HEAD 2>/dev/null || date +%Y%m%d%H%M)"
IMAGE="$REGION-docker.pkg.dev/$GCP_PROJECT/$REPO/$SERVICE:$TAG"

if [[ -z "${PN_WRITE_API_KEY:-}" && -f config/.env ]]; then
  PN_WRITE_API_KEY="$(grep -E '^PN_WRITE_API_KEY=' config/.env | cut -d= -f2- || true)"
fi
if [[ -z "${PN_WRITE_API_KEY:-}" ]]; then
  echo "PN_WRITE_API_KEY is empty: writes would be open to anyone. Set it in config/.env." >&2
  exit 1
fi

gcloud artifacts repositories describe "$REPO" --project "$GCP_PROJECT" --location "$REGION" >/dev/null 2>&1 \
  || gcloud artifacts repositories create "$REPO" --project "$GCP_PROJECT" --location "$REGION" \
       --repository-format docker

gcloud builds submit --project "$GCP_PROJECT" --tag "$IMAGE" .

gcloud run deploy "$SERVICE" --project "$GCP_PROJECT" --region "$REGION" --image "$IMAGE" \
  --min-instances 1 --max-instances 1 --cpu 1 --memory 1Gi --no-cpu-throttling \
  --allow-unauthenticated --port 8080 \
  --set-env-vars "PN_SIMULATED_MODE=true,PN_REPLAY_SPEED=demo,PN_WRITE_API_KEY=$PN_WRITE_API_KEY"

URL="$(gcloud run services describe "$SERVICE" --project "$GCP_PROJECT" --region "$REGION" --format 'value(status.url)')"
echo "Deployed: $URL"
echo "Next: scripts/smoke-test.sh $URL"
