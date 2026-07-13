# Microservices Inventory — Count & Responsibilities

**Target language:** Java (parity with current Go/Python services)  
**Frontend:** React console remains TypeScript (not rewritten to Java)

---

## 1. Total count

### Backend microservices: **11**

| # | Service | Current impl | Java target module |
|---|---------|--------------|--------------------|
| 1 | `adapter-otel` | Go | `adapter-otel` |
| 2 | `adapter-datadog` | Go (stub) | `adapter-datadog` |
| 3 | `adapter-newrelic` | Go (stub) | `adapter-newrelic` |
| 4 | `adapter-dynatrace` | Go (stub) | `adapter-dynatrace` |
| 5 | `ingest-gateway` | Go | `ingest-gateway` |
| 6 | `normalizer` | Go | `normalizer` |
| 7 | `correlator` | Go (stub) | `correlator` |
| 8 | `topology-builder` | Go | `topology-builder` |
| 9 | `snapshotter` | Go | `snapshotter` |
| 10 | `rca-engine` | Python | `rca-engine` *(Java service; ML via DJL/ONNX or sidecar)* |
| 11 | `query-api` | Go (stub) | `query-api` |

### Not counted as microservices

| Component | Why |
|-----------|-----|
| `apps/console` | SPA UI (React) — BFF calls `query-api` |
| `packages/melt`, `kafkautil`, `contextgraph`, `snapshot` | Shared libraries → Java modules (`pulsegraph-melt`, etc.) |
| Redpanda / ClickHouse / OpenSearch / MinIO / Postgres | Infrastructure |

**Grand total deployable application units in the Java fork:** **11 microservices + 1 UI**.

---

## 2. Functionality of each microservice

### 1. `adapter-otel`
- **Role:** Vendor-neutral OTLP ingress (HTTP JSON today; gRPC later).
- **In:** OTLP traces/metrics/logs from SDKs/Collectors.
- **Out:** Canonical `MeltEnvelope` → HTTP to `ingest-gateway` (or Kafka `raw.melt`).
- **Java notes:** Spring WebFlux or Quarkus REST; optional OpenTelemetry Collector contrib patterns.

### 2. `adapter-datadog`
- **Role:** Accept Datadog-native intake / webhooks; map to OTel-shaped envelopes.
- **In:** Agent intake, monitor webhooks.
- **Out:** Envelopes to gateway / `raw.melt`.
- **Status:** Stub → full parser in Java rewrite tranche.

### 3. `adapter-newrelic`
- **Role:** New Relic Events / OTLP / alert webhooks → envelopes.
- **Out:** Same as other adapters.

### 4. `adapter-dynatrace`
- **Role:** Dynatrace Problems / OTLP / notifications → envelopes.
- **Out:** Same as other adapters.

### 5. `ingest-gateway`
- **Role:** Validate envelopes, tenancy stamp, publish to Kafka.
- **In:** HTTP `POST /v1/ingest` from adapters.
- **Out:** Kafka topic `raw.melt` (DLQ `raw.melt.dlq`).
- **NFRs:** Backpressure, authn (future), rate limits.

### 6. `normalizer`
- **Role:** Schema hygiene; OTel semantic convention mapping; mark `normalized=true`.
- **In:** `raw.melt` consumer group.
- **Out:** `norm.melt` (+ DLQ).
- **Also (planned):** Persist hot-path rows to ClickHouse / OpenSearch.

### 7. `correlator`
- **Role:** Cross-signal joins (trace_id, service, time window); emit correlation / incident candidates.
- **In:** `norm.melt`.
- **Out:** `corr.events`, optionally `snapshot.commands` / `incidents`.
- **Status:** Stub in Go; first-class in Java design.

### 8. `topology-builder` (Context Graph service)
- **Role:** Maintain live entity graph from traces; subgraph & blast-radius APIs.
- **In:** `norm.melt` (spans); optional deploy events.
- **Out:** In-memory/persisted graph; `topology.changes`; HTTP/gRPC query API.
- **Java notes:** Port `packages/contextgraph` to `pulsegraph-contextgraph` (Guava / custom digraph).

### 9. `snapshotter`
- **Role:** Incident-triggered **freeze**: time-window MELT + topology subgraph → MinIO; publish `snapshot.refs`.
- **In:** `snapshot.commands`, buffered `norm.melt`; topology HTTP.
- **Out:** Object store bundle; Kafka `snapshot.refs`.
- **HTTP:** `POST /v1/freeze`, `GET /v1/snapshots/{id}`.

### 10. `rca-engine`
- **Role:** Ranked hypotheses from freeze + graph (rules → graph walk → ML → optional LLM narrative).
- **In:** `snapshot.refs` (+ read MinIO bundle).
- **Out:** `rca.results`.
- **Java strategy:** Spring Boot service; call Python sidecar **or** DJL/ONNX for Isolation Forest; LLM via HTTP to Ollama.

### 11. `query-api`
- **Role:** Read API for console / War Room: incidents, topology proxy, snapshots, RCA results, RBAC later.
- **In:** Postgres metadata, ClickHouse, OpenSearch, MinIO, topology gRPC/HTTP.
- **Out:** REST/JSON to `apps/console`.

---

## 3. Go → Java responsibility matrix (1:1)

```text
Go adapter-*          →  Java adapter-*
Go ingest-gateway     →  Java ingest-gateway
Go normalizer         →  Java normalizer
Go correlator         →  Java correlator
Go topology-builder   →  Java topology-builder
Go snapshotter        →  Java snapshotter
Python rca-engine     →  Java rca-engine (+ optional ML sidecar)
Go query-api          →  Java query-api
React console         →  unchanged (calls query-api)
```

Parity fork means **same service boundaries and Kafka contracts**, not a collapse into fewer services.

---

## 4. Shared libraries (Java modules, not services)

| Module | Port of | Purpose |
|--------|---------|---------|
| `pulsegraph-melt` | `packages/melt` | Envelope, topics, OTLP JSON helpers |
| `pulsegraph-kafka` | `packages/kafkautil` | Producer/consumer helpers |
| `pulsegraph-contextgraph` | `packages/contextgraph` | Graph ADT, subgraph, blast radius |
| `pulsegraph-snapshot` | `packages/snapshot` | Command/Ref/Manifest/bundle hashing |
| `pulsegraph-common` | new | Logging, tracing, tenant context |

---

## 5. Ports (unchanged for local parity)

| Service | Default port |
|---------|-------------:|
| adapter-otel | 4318 |
| ingest-gateway | 8081 |
| topology-builder | 8091 |
| snapshotter | 8092 |
| query-api | 8090 |
| rca-engine | 8095 |
| console | 3000 |
| adapter-datadog / newrelic / dynatrace | 8200 / 8201 / 8202 |
