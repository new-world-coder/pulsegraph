#!/usr/bin/env bash
# smoke-ingest.sh — Post sample OTLP/JSON traces through adapter → gateway → Kafka.
set -euo pipefail

ADAPTER_URL="${ADAPTER_URL:-http://localhost:4318}"
BROKER_CONTAINER="${BROKER_CONTAINER:-pulsegraph-redpanda}"

SAMPLE='{
  "resourceSpans": [{
    "resource": {
      "attributes": [
        {"key": "service.name", "value": {"stringValue": "checkout"}},
        {"key": "deployment.environment", "value": {"stringValue": "prod"}}
      ]
    },
    "scopeSpans": [{
      "spans": [{
        "traceId": "5b8aa5a2d2c872e8321cf37308d69df2",
        "spanId": "051581bf3cb55c13",
        "name": "POST /pay",
        "kind": 3,
        "startTimeUnixNano": "1710000000000000000",
        "endTimeUnixNano": "1710000000500000000"
      }]
    }]
  }]
}'

echo "==> POST ${ADAPTER_URL}/v1/traces"
curl -sf -X POST "${ADAPTER_URL}/v1/traces" \
  -H "Content-Type: application/json" \
  -d "${SAMPLE}"
echo
echo "==> Waiting briefly for normalizer..."
sleep 2
echo "==> Tail norm.melt (Ctrl-C to stop after first message if stuck)"
docker exec "${BROKER_CONTAINER}" rpk topic consume norm.melt -n 1 --offset start 2>/dev/null \
  || docker exec "${BROKER_CONTAINER}" rpk topic consume norm.melt -n 1 --offset end-1
