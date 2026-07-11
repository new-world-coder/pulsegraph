# Testing Manual

**Audience:** Engineers validating PulseGraph before / during a pilot  
**Related:** [DEPLOYMENT.md](DEPLOYMENT.md) · [PILOT_LITMUS.md](PILOT_LITMUS.md) · [FEATURES.md](FEATURES.md)

---

## 1. Test layers

| Layer | What | How |
|-------|------|-----|
| Unit | Pure packages | `go test` |
| Smoke | Happy-path scripts | `make smoke-*` |
| Integration | Compose + live services | Manual / scripts below |
| Litmus | Customer-like signal | [PILOT_LITMUS.md](PILOT_LITMUS.md) |

---

## 2. Unit tests

```bash
# Context Graph
go test ./packages/contextgraph/...

# Snapshot bundle hashing / windows
go test ./packages/snapshot/...

# All Go modules that have tests (many services still stub)
make test
```

**Python (RCA stub):**

```bash
make test-python
```

---

## 3. Infrastructure smoke

```bash
make up
make ps
make bootstrap
```

Expect:

- Containers healthy / running  
- Topics: `raw.melt`, `norm.melt`, `snapshot.commands`, `snapshot.refs`, `topology.changes`, …  
- MinIO buckets: `snapshots`, `models`, `exports`

---

## 4. Pipeline smoke — ingest

Prerequisites: `make up && make bootstrap`, then ingest-gateway, normalizer, adapter-otel running (`make run-mvp`).

```bash
make smoke-ingest
```

**Pass criteria**

1. `POST` to `adapter-otel /v1/traces` returns 200.  
2. A message appears on Kafka topic `norm.melt` (script uses `rpk topic consume`).  
3. Logs show normalize success for service `checkout`.

Manual alternate:

```bash
curl -s -X POST http://localhost:4318/v1/traces \
  -H 'Content-Type: application/json' \
  -d @- <<'EOF'
{"resourceSpans":[{"resource":{"attributes":[
  {"key":"service.name","value":{"stringValue":"checkout"}},
  {"key":"deployment.environment","value":{"stringValue":"prod"}}
]},"scopeSpans":[{"spans":[{
  "traceId":"5b8aa5a2d2c872e8321cf37308d69df2",
  "spanId":"051581bf3cb55c13",
  "name":"POST /pay","kind":3,
  "startTimeUnixNano":"1710000000000000000",
  "endTimeUnixNano":"1710000000500000000"
}]}]}]}
EOF
```

---

## 5. Context Graph checks

With topology-builder running and after ingesting traces:

```bash
curl -sf http://localhost:8091/healthz | jq .
curl -sf 'http://localhost:8091/v1/graph?depth=2' | jq '.nodes | length'
curl -sf 'http://localhost:8091/v1/graph/subgraph?service=checkout&depth=2' | jq .
curl -sf 'http://localhost:8091/v1/graph/blast-radius?service=checkout&depth=2' | jq .
```

**Pass criteria:** `healthz` reports `status=ok`; after enough spans with peer attributes, edge count &gt; 0. Single-service fixtures may yield nodes without rich edges — still acceptable for ingest litmus.

---

## 6. Snapshot freeze smoke

Prerequisites: snapshotter running; MinIO up; preferably topology-builder up; some `norm.melt` traffic buffered.

```bash
make smoke-snapshot
```

Or:

```bash
curl -sf -X POST http://localhost:8092/v1/freeze \
  -H 'Content-Type: application/json' \
  -d '{"service_name":"checkout","trigger":"manual"}' | jq .
```

**Pass criteria**

| Check | Expect |
|-------|--------|
| HTTP | `202` with `incident_id`, `bundle_uri`, `manifest_sha256` |
| MinIO | Objects under `snapshots/<tenant>/<incident_id>/` |
| Files | `manifest.json`, `metrics.json`, `logs.json`, `traces.json`, `topology/subgraph.json` |
| Degraded | Allowed if buffer empty (`empty_melt_window`) or topology down — document reason |

---

## 7. Negative tests

| Case | Expect |
|------|--------|
| Freeze with topology down | `degraded_reasons` contains `topology_unavailable`; bundle still written |
| Invalid OTLP JSON to adapter | HTTP 400 |
| Ingest gateway invalid envelope | HTTP 400 |
| Kafka down | Gateway / normalizer errors; health may still 200 — watch logs |

---

## 8. Regression checklist before Oracle cutover

- [ ] `go test ./packages/contextgraph/...`  
- [ ] `go test ./packages/snapshot/...`  
- [ ] `make up && make bootstrap`  
- [ ] `make smoke-ingest`  
- [ ] Topology healthz  
- [ ] `make smoke-snapshot`  
- [ ] ARM build on Oracle (`go build` each MVP service)

---

## 9. Performance (pilot — not SLOs)

Record for awareness (not gate):

- Time from OTLP POST → message on `norm.melt`  
- Time from `/v1/freeze` → MinIO objects listed  

Target directionally: freeze API returns in seconds when MinIO/topology healthy.
