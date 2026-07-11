#!/usr/bin/env bash
# run-mvp.sh — Launch MVP ingest path processes in the background.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

mkdir -p .run
PIDS=()

cleanup() {
  for pid in "${PIDS[@]:-}"; do
    kill "$pid" 2>/dev/null || true
  done
}
trap cleanup EXIT INT TERM

echo "[mvp] starting ingest-gateway..."
(cd services/ingest-gateway && go run ./cmd/server) >.run/ingest-gateway.log 2>&1 &
PIDS+=($!)

echo "[mvp] starting normalizer..."
(cd services/normalizer && go run ./cmd/server) >.run/normalizer.log 2>&1 &
PIDS+=($!)

sleep 1
echo "[mvp] starting adapter-otel..."
(cd services/adapter-otel && go run ./cmd/server) >.run/adapter-otel.log 2>&1 &
PIDS+=($!)

echo "[mvp] services up (pids: ${PIDS[*]}). Logs in .run/"
echo "[mvp] health: curl -s localhost:8081/healthz ; curl -s localhost:4318/healthz"
echo "[mvp] smoke:  make smoke-ingest"
echo "[mvp] Ctrl-C to stop."

wait
