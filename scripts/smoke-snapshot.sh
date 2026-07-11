#!/usr/bin/env bash
# smoke-snapshot.sh — Trigger an incident freeze and print the SnapshotRef.
set -euo pipefail

SNAPSHOTTER_URL="${SNAPSHOTTER_URL:-http://localhost:8092}"
SERVICE="${SERVICE:-checkout}"

echo "==> POST ${SNAPSHOTTER_URL}/v1/freeze"
RESP=$(curl -sf -X POST "${SNAPSHOTTER_URL}/v1/freeze" \
  -H "Content-Type: application/json" \
  -d "{\"service_name\":\"${SERVICE}\",\"trigger\":\"manual\",\"pre_window_sec\":600,\"post_window_sec\":120,\"depth\":2}")
echo "${RESP}" | python3 -m json.tool 2>/dev/null || echo "${RESP}"

INCIDENT_ID=$(echo "${RESP}" | python3 -c "import sys,json; print(json.load(sys.stdin)['incident_id'])" 2>/dev/null || true)
if [[ -n "${INCIDENT_ID}" ]]; then
  echo "==> GET ${SNAPSHOTTER_URL}/v1/snapshots/${INCIDENT_ID}"
  curl -sf "${SNAPSHOTTER_URL}/v1/snapshots/${INCIDENT_ID}" | python3 -m json.tool 2>/dev/null || true
fi
