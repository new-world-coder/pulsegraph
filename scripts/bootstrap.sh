#!/usr/bin/env bash
# bootstrap.sh — Create Redpanda topics and MinIO buckets for local development.
#
# Usage:
#   bash scripts/bootstrap.sh
#   make bootstrap
#
# Prerequisites: docker compose stack is running (make up).

set -euo pipefail

# ── Colours ──────────────────────────────────────────────────────────────────
GREEN="\033[0;32m"
YELLOW="\033[1;33m"
RED="\033[0;31m"
RESET="\033[0m"

log_info()  { echo -e "${GREEN}[bootstrap]${RESET} $*"; }
log_warn()  { echo -e "${YELLOW}[bootstrap]${RESET} $*"; }
log_error() { echo -e "${RED}[bootstrap]${RESET} $*" >&2; }

# ── Configuration ─────────────────────────────────────────────────────────────
KAFKA_BROKERS="${KAFKA_BROKERS:-localhost:9092}"
MINIO_ENDPOINT="${MINIO_ENDPOINT:-http://localhost:9001}"
MINIO_ROOT_USER="${MINIO_ROOT_USER:-pulsegraph}"
MINIO_ROOT_PASSWORD="${MINIO_ROOT_PASSWORD:-pulsegraph123}"

REDPANDA_CONTAINER="pulsegraph-redpanda"
MINIO_CONTAINER="pulsegraph-minio"

# ── Topics ────────────────────────────────────────────────────────────────────
KAFKA_TOPICS=(
  "raw.events"
  "raw.events.dlq"
  "normalised.events"
  "incidents"
  "rca.results"
  "topology.changes"
)

create_topics() {
  log_info "Creating Redpanda topics..."
  for topic in "${KAFKA_TOPICS[@]}"; do
    if docker exec "${REDPANDA_CONTAINER}" \
        rpk topic create "${topic}" \
        --partitions 4 \
        --replicas 1 \
        --brokers "${KAFKA_BROKERS}" \
        2>&1 | grep -qE "(Created|already exists)"; then
      log_info "  ✓ ${topic}"
    else
      # Re-run to show actual output on failure
      docker exec "${REDPANDA_CONTAINER}" \
        rpk topic create "${topic}" \
        --partitions 4 \
        --replicas 1 \
        --brokers "${KAFKA_BROKERS}" || true
    fi
  done
}

# ── MinIO Buckets ─────────────────────────────────────────────────────────────
MINIO_BUCKETS=(
  "snapshots"
  "models"
  "exports"
)

create_buckets() {
  log_info "Creating MinIO buckets..."

  # Configure mc alias inside the container
  docker exec "${MINIO_CONTAINER}" \
    mc alias set local "${MINIO_ENDPOINT}" \
    "${MINIO_ROOT_USER}" "${MINIO_ROOT_PASSWORD}" \
    --api S3v4 > /dev/null 2>&1 || true

  for bucket in "${MINIO_BUCKETS[@]}"; do
    docker exec "${MINIO_CONTAINER}" \
      mc mb --ignore-existing "local/${bucket}" 2>&1 | grep -v "^$" || true
    log_info "  ✓ ${bucket}"
  done
}

# ── Wait helpers ──────────────────────────────────────────────────────────────
wait_for_container() {
  local name="$1"
  local max_attempts=30
  local attempt=0
  log_info "Waiting for container ${name} to be healthy..."
  while [[ $attempt -lt $max_attempts ]]; do
    status=$(docker inspect --format='{{.State.Health.Status}}' "${name}" 2>/dev/null || echo "not_found")
    if [[ "$status" == "healthy" ]]; then
      log_info "  ${name} is healthy."
      return 0
    fi
    attempt=$((attempt + 1))
    sleep 2
  done
  log_warn "  ${name} did not become healthy within $((max_attempts * 2))s — continuing anyway."
}

# ── Main ──────────────────────────────────────────────────────────────────────
main() {
  log_info "PulseGraph bootstrap starting..."

  wait_for_container "${REDPANDA_CONTAINER}"
  create_topics

  wait_for_container "${MINIO_CONTAINER}"
  create_buckets

  log_info ""
  log_info "Bootstrap complete!"
  log_info ""
  log_info "  Kafka topics:    ${KAFKA_TOPICS[*]}"
  log_info "  MinIO buckets:   ${MINIO_BUCKETS[*]}"
  log_info ""
  log_info "Next steps:"
  log_info "  make ps          — verify all containers are healthy"
  log_info "  make logs        — tail infrastructure logs"
}

main "$@"
