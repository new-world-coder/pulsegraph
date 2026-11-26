# PulseGraph — Component Architecture

> **Status:** Approved v1.0 · Sep 2026  
> Cross-reference: [HLD.md](./HLD.md) for system-level design; [REQUIREMENTS.md](./REQUIREMENTS.md) for MoSCoW requirements.

---

## Table of Contents

1. [Repository Layout](#1-repository-layout)
2. [Component Responsibilities](#2-component-responsibilities)
3. [Kafka Topic Catalogue](#3-kafka-topic-catalogue)
4. [Proto / Schema Ownership](#4-proto--schema-ownership)
5. [Sequence Diagram: Snapshot Pipeline](#5-sequence-diagram-snapshot-pipeline)
6. [Sequence Diagram: RCA Pipeline](#6-sequence-diagram-rca-pipeline)
7. [Sequence Diagram: Query Path](#7-sequence-diagram-query-path)
8. [Data Schemas](#8-data-schemas)
9. [Configuration & Feature Flags](#9-configuration--feature-flags)
10. [Testing Strategy](#10-testing-strategy)

---

## 1. Repository Layout

Reflects the monorepo as scaffolded (Go workspace under `packages/` + `services/`; console under `apps/`).

```
pulsegraph/
├── apps/
│   └── console/                    # TypeScript / React / Vite War Room UI
│
├── packages/                       # Shared Go libraries (go.work members)
│   ├── melt/                       # MELT envelope + topic constants + OTLP/JSON helpers
│   ├── kafkautil/                  # Producer/consumer wrappers
│   ├── snapshot/                   # Incident snapshot bundle types & MinIO helpers
│   ├── contextgraph/               # Context Graph model
│   └── otel-normalize/             # Vendor → OTel normalisation helpers (planned)
│
├── services/
│   ├── adapter-datadog/            # Go
│   ├── adapter-newrelic/           # Go
│   ├── adapter-dynatrace/          # Go
│   ├── adapter-otel/               # Go
│   ├── ingest-gateway/             # Go
│   ├── normalizer/                 # Go
│   ├── correlator/                 # Go
│   ├── topology-builder/           # Go
│   ├── snapshotter/                # Go
│   ├── rca-engine/                 # Python
│   ├── api-gateway/                # Go (JWT/RBAC edge; stubs)
│   └── query-api/                  # Go
│
├── java/                           # Optional Spring Boot rewrite scaffold (docs + stubs)
│
├── proto/                          # Protobuf definitions (source of truth as schemas land)
│
├── deploy/compose/                 # Compose override notes
├── docker-compose.yml              # Local data plane (Redpanda, CH, OS, MinIO, Postgres)
├── docs/                           # REQUIREMENTS, HLD, ARCHITECTURE, ADRs, manuals
└── scripts/                        # bootstrap, smoke-ingest, smoke-snapshot, run-mvp
```

Planned (not yet required for blueprint completeness): `rules/` for RCA YAML, `infra/` migration trees, and full `proto/pulsegraph/**` trees — see proto README and Phase 0 in HLD.
---

## 2. Component Responsibilities

### 2.1 Adapters (`adapter-datadog`, `adapter-newrelic`, `adapter-dynatrace`, `adapter-otel`)

**Language:** Go  
**Deployment:** One Deployment per vendor; stateless; HPA on CPU.

Each adapter is a thin translation layer — it speaks the vendor's wire format on the inbound side and emits standard OTLP `ExportRequest` protos on the outbound side to `ingest-gateway`.

| Concern | Detail |
|---------|--------|
| Inbound protocols | Datadog: HTTP webhook (JSON) + Datadog API poll; New Relic: MELT API (JSON); Dynatrace: Problems API HTTP push; OTel: gRPC/HTTP OTLP |
| Translation | Vendor field → OTel semantic convention attribute mapping, documented in `packages/otel-normalize/` and adapter READMEs |
| Enrichment | Adds `pulsegraph.source` resource attribute identifying the originating vendor |
| Idempotency | Datadog/NR/DT adapters deduplicate by vendor-provided event ID using a Redis TTL-keyed bloom filter (shared via Redpanda's built-in key-value store) |
| Error handling | Non-retryable parse errors are logged + metrics-incremented; payload is dropped with a `adapter.parse_error` counter |
| Backpressure | gRPC stream to ingest-gateway applies flow control; adapter applies exponential back-off (100 ms → 30 s) on `RESOURCE_EXHAUSTED` |

### 2.2 `ingest-gateway`

**Language:** Go  
**Deployment:** Deployment; HPA on CPU + RPS; behind an L4 load balancer (Envoy).

Single entry point for all normalised OTLP traffic inside the platform.

| Concern | Detail |
|---------|--------|
| Authentication | API key (HMAC-validated header) for adapter service accounts; mTLS for adapter gRPC calls |
| Rate limiting | Token-bucket per `tenant_id`, limits from PostgreSQL, enforced in-memory with periodic re-sync |
| Schema validation | Validates OTLP proto structure; rejects missing required fields (resource, scope) |
| Routing | Produces to `raw.melt` Kafka topic, keyed by `tenant_id` + `service.name` for partition affinity |
| Telemetry | Self-reports ingest throughput (`ingest.records.total`), validation errors, and queue depth as OTel metrics |

### 2.3 `normalizer`

**Language:** Go  
**Deployment:** Deployment; HPA on Kafka consumer lag (`raw.melt`).

Transforms raw OTLP records into the PulseGraph canonical form and writes to storage.

| Concern | Detail |
|---------|--------|
| OTel convention enforcement | Applies [OTel Semantic Conventions 1.26](https://opentelemetry.io/docs/specs/semconv/) — normalises `http.method` → `http.request.method`, legacy span kinds, etc. |
| Resource attribute resolution | Merges resource attributes from span scope and record level; resolves `service.instance.id` if absent |
| Timestamp normalisation | Converts all timestamps to UTC nanoseconds; detects and corrects clock skew > 30 s using monotonic offset |
| Storage writes | **Metrics + Traces**: async batch insert to ClickHouse via HTTP bulk API (batch size 5000, flush every 2 s). **Logs**: bulk index to OpenSearch (batch size 1000, flush every 1 s). Both use local disk buffer on write failure. |
| Schema version | Each `NormMELTEvent` carries `schema_version` field; normalizer writes version tag to ClickHouse for future migrations |

### 2.4 `correlator`

**Language:** Go  
**Deployment:** Deployment; HPA on Kafka consumer lag (`norm.melt`).

Stateful sliding-window anomaly detector and incident trigger.

| Concern | Detail |
|---------|--------|
| State | In-memory ring buffer per `(tenant_id, service, metric_name)` keyed on a 5-minute sliding window. State is check-pointed to Redpanda's built-in state store every 30 s. |
| Anomaly detection | **Metric**: z-score vs 5-min EMA baseline. **Error-rate spike**: `error_count / request_count > threshold`. **Latency cliff**: P99 latency > N × median. Thresholds configurable per alert rule in PostgreSQL. |
| Alert correlation | Groups raw alert-webhook events (from Datadog/Dynatrace receivers) with internal anomaly detections by time window and overlapping entity labels. |
| Incident deduplication | Deduplicates open incidents by `(tenant_id, root_entity, fingerprint)`. Fingerprint = SHA-1 of sorted anomalous entity IDs + metric names. |
| Snapshot trigger | On new incident: writes incident to PostgreSQL, publishes `SnapshotCommand` to `snapshot.commands`. |
| Topology updates | On span data: publishes edge updates to `topology-builder` via gRPC streaming call. |

### 2.5 `topology-builder`

**Language:** Go  
**Deployment:** StatefulSet (consistent hash ring for partition ownership); HPA on CPU.

Maintains the live directed weighted graph of service dependencies.

| Concern | Detail |
|---------|--------|
| Graph store | In-memory adjacency list (sync.Map); persisted to PostgreSQL `topology_edges` on every update for durability. On restart, replays from PostgreSQL. |
| Edge attributes | `source_service`, `dest_service`, `call_rate`, `error_rate`, `p99_latency`, `last_seen_at`, `protocol` |
| Subgraph extraction | BFS/DFS up to configurable depth; used by snapshotter and rca-engine |
| Stale edge pruning | Edges not refreshed in > 5 min are marked `stale=true`; > 30 min are pruned from the live graph (kept in PostgreSQL for history) |
| gRPC API | `GetGraph`, `GetSubgraph(entity_ids, depth)`, `UpdateEdge`, `WatchGraph` (server-streaming for change subscriptions) |

### 2.6 `snapshotter`

**Language:** Go  
**Deployment:** Deployment; HPA on Kafka consumer lag (`snapshot.commands`).

Assembles and persists immutable incident snapshot bundles.

| Concern | Detail |
|---------|--------|
| Parallelism | Fetches metrics (ClickHouse), logs (OpenSearch), and topology (gRPC) concurrently via `errgroup`. |
| Parquet writer | Uses Apache Arrow Go library for Parquet serialisation. Metrics and traces in columnar format optimised for time-range scans. |
| Object store | Uploads via S3 multipart upload. Each file is checksummed (SHA-256). Manifest is written last with all file hashes. |
| Immutability | After upload, sets `x-amz-object-lock-mode: COMPLIANCE` + `x-amz-object-lock-retain-until-date` (90 days). |
| Retry semantics | Retains `SnapshotCommand` offset until all files are uploaded and manifest is written. Only commits offset on success. |
| Failure mode | On 5-minute timeout: marks incident `snapshot_status=degraded`, publishes `SnapshotRef` with `degraded=true`. RCA engine skips bundle read and runs topology-only analysis. |

### 2.7 `rca-engine`

**Language:** Python 3.12  
**Key libraries:** `confluent-kafka`, `pyarrow`, `networkx`, `scikit-learn`, `statsmodels`, `grpcio`, `openai` (optional)  
**Deployment:** Deployment; HPA on Kafka consumer lag (`snapshot.refs`).

| Strategy | Implementation | Output |
|----------|---------------|--------|
| Rule Engine | YAML rules parsed at startup; hot-reloaded via `inotify`. Each rule is a `Predicate` tree over OTel attributes. Fired rules produce `Hypothesis` objects with base confidence. | 0–N hypotheses |
| Graph Analyst | `networkx.DiGraph` loaded from topology subgraph. Reverse-BFS from anomalous leaf nodes. Blame score decays by 0.7 per hop. Top-5 nodes by blame score become hypotheses. | 0–5 hypotheses |
| Anomaly ML | `IsolationForest` trained on last 7-day metric baseline fetched from ClickHouse at startup (refreshed hourly). Scores each entity in snapshot against model. High-scoring entities feed hypothesis generation. | Anomaly scores per entity |
| Confidence Fusion | Borda count across strategies; weighted by strategy weight (configurable). Final score normalised to [0, 1]. | Ranked hypothesis list |
| LLM Narrator | `openai.ChatCompletion` or Anthropic `messages` API. Prompt built from top-3 hypotheses + evidence excerpts. Response appended as `narrative` field. Timeout 30 s; non-blocking. | Optional narrative string |

### 2.8 `api-gateway`

**Language:** Go  
**Deployment:** Deployment; HPA on RPS; TLS termination at ingress.

| Concern | Detail |
|---------|--------|
| Authentication | Validates RS256 JWT on every request. JWKS cached from OIDC provider (refreshed every 5 min). |
| Authorisation | Checks RBAC role bindings from PostgreSQL (TTL 60 s cache). Injects `tenant_id` and `actor` into downstream context. |
| Routing | Reverse-proxies REST calls to `query-api`; WebSocket upgrade forwarded to `query-api`. |
| Rate limiting | Per-user and per-tenant token bucket. |
| Observability | Emits OTel spans for every request; propagates `traceparent` header downstream. |

### 2.9 `query-api`

**Language:** Go  
**Deployment:** Deployment; HPA on CPU.

Serves all read-path queries for the console and external integrations.

| Resource | Storage | Notes |
|----------|---------|-------|
| Incidents | PostgreSQL | Pagination by `created_at` cursor; full-text search on `title` and `labels` |
| Snapshot metadata | PostgreSQL | `snapshot_uri`, `sha256`, `frozen_at` |
| Snapshot bundle | MinIO | Pre-signed URL (15 min TTL) or proxied download |
| RCA hypotheses | PostgreSQL | Ordered by `rank`; evidence refs include snapshot-relative paths |
| Metrics / Traces | ClickHouse | Prometheus-compatible range query; raw trace fetch by `trace_id` |
| Logs | OpenSearch | Full-text + structured filter; returns sorted by `@timestamp` |
| Live topology | topology-builder gRPC | Pass-through with tenant filter injection |

WebSocket handler pushes incident state-change events to subscribed console sessions using a fanout implemented via an in-process pub/sub hub (Redis pub/sub in multi-replica deployments).

### 2.10 `console`

**Language:** TypeScript / React 18 / Vite  
**Key libraries:** `@tanstack/react-query`, `react-router-dom`, `@xyflow/react` (topology graph), `recharts` (metric charts), `@radix-ui/react-*` (accessible UI primitives)

| View | Description |
|------|-------------|
| Incident List | Paginated, searchable; severity badges; live WebSocket updates |
| Incident Detail | Timeline view of trigger events + correlated signals; snapshot integrity indicator (SHA-256 verified in-browser) |
| Topology Explorer | Force-directed graph; nodes coloured by anomaly severity; click to drill into service |
| RCA Hypotheses | Ranked card list; expand to see evidence chain, metric sparklines, log excerpts, narrative |
| Metric Explorer | PromQL-like query builder over ClickHouse; rendered as time-series charts |
| Log Search | Faceted log search powered by OpenSearch |
| Admin | Alert rule editor, RBAC management, topology hint configuration |

---

## 3. Kafka Topic Catalogue

All topics use `tenant_id` as part of the message key to enable partition affinity. Retention policy is defined per topic.

| Topic | Key | Value Schema | Partitions | Retention | Producers | Consumers |
|-------|-----|-------------|-----------|-----------|-----------|-----------|
| `raw.melt` | `{tenant_id}:{service}` | `RawMELTEvent` (proto) | 24 | 6 h | ingest-gateway | normalizer |
| `raw.melt.dlq` | same | `DeadLetterRecord` (proto) | 4 | 7 d | normalizer | alerting |
| `norm.melt` | `{tenant_id}:{service}` | `NormMELTEvent` (proto) | 24 | 2 h | normalizer | correlator |
| `norm.melt.dlq` | same | `DeadLetterRecord` | 4 | 7 d | correlator | alerting |
| `corr.events` | `{tenant_id}:{incident_id}` | `CorrelationEvent` (proto) | 8 | 24 h | correlator | query-api (for WS push) |
| `snapshot.commands` | `{tenant_id}:{incident_id}` | `SnapshotCommand` (proto) | 8 | 24 h | correlator | snapshotter |
| `snapshot.commands.dlq` | same | `DeadLetterRecord` | 4 | 7 d | snapshotter | alerting |
| `snapshot.refs` | `{tenant_id}:{incident_id}` | `SnapshotRef` (proto) | 8 | 24 h | snapshotter | rca-engine |
| `snapshot.refs.dlq` | same | `DeadLetterRecord` | 4 | 7 d | rca-engine | alerting |
| `rca.results` | `{tenant_id}:{incident_id}` | `RCAResult` (proto) | 8 | 7 d | rca-engine | query-api |

### Consumer Groups

| Consumer Group ID | Service | Topic(s) |
|-------------------|---------|----------|
| `pg-normalizer-v1` | normalizer | `raw.melt` |
| `pg-correlator-v1` | correlator | `norm.melt` |
| `pg-snapshotter-v1` | snapshotter | `snapshot.commands` |
| `pg-rca-v1` | rca-engine | `snapshot.refs` |
| `pg-queryapi-v1` | query-api | `rca.results`, `corr.events` |

---

## 4. Proto / Schema Ownership

Proto files live in `/proto/` and are the **single source of truth**. Generated code is committed for each language under `services/{service}/gen/`.

```
proto/pulsegraph/
├── ingest/v1/
│   ├── raw_melt.proto        # RawMELTEvent — wraps otlp.ExportRequest + pulsegraph metadata
│   └── ingest_service.proto  # IngestService gRPC definition
│
├── norm/v1/
│   ├── norm_melt.proto       # NormMELTEvent — fully normalised, tenant-scoped
│   └── dlq.proto             # DeadLetterRecord
│
├── snapshot/v1/
│   ├── commands.proto        # SnapshotCommand, SnapshotRef
│   ├── manifest.proto        # SnapshotManifest (JSON-serialisable for storage)
│   └── snapshot_service.proto
│
├── topology/v1/
│   ├── graph.proto           # Node, Edge, Subgraph
│   └── topology_service.proto
│
└── rca/v1/
    ├── result.proto          # RCAResult, Hypothesis, Evidence
    └── rca_service.proto
```

Schema evolution rules:
- All proto fields use explicit numbers; no reuse of deprecated field numbers.
- `BREAKING CHANGE` fields require a minor version bump of the topic name (e.g. `norm.melt.v2`).
- Backward-compatible additions (new optional fields) are allowed without topic versioning.
- ClickHouse and OpenSearch schema changes are managed by versioned migration scripts in `infra/clickhouse/migrations/` and `infra/opensearch/templates/`.

---

## 5. Sequence Diagram: Snapshot Pipeline

```mermaid
sequenceDiagram
    autonumber
    participant C   as correlator
    participant PG  as PostgreSQL
    participant K   as Kafka: snapshot.commands
    participant S   as snapshotter
    participant CH  as ClickHouse
    participant OS  as OpenSearch
    participant TB  as topology-builder
    participant M   as MinIO/S3

    C->>C: Anomaly threshold exceeded or webhook received
    C->>PG: INSERT incidents (id=UUID-v7, status=open, severity, trigger_labels, started_at)
    C->>K: PRODUCE SnapshotCommand {incident_id, tenant_id, window_start=T-30m, window_end=T+15m, entity_ids=[...]}

    K-->>S: CONSUME SnapshotCommand (at-least-once)
    S->>S: Validate command; mark snapshot_status=collecting in PG

    par Parallel data collection
        S->>CH: SELECT metrics WHERE service IN (entity_ids) AND ts BETWEEN window_start AND window_end
        CH-->>S: Metric rows (columnar)
        S->>CH: SELECT traces WHERE root_service IN (entity_ids) AND ts BETWEEN window_start AND window_end
        CH-->>S: Trace rows
        S->>OS: SearchLogs {services: entity_ids, from: window_start, to: window_end}
        OS-->>S: Log hits (JSON)
        S->>TB: GetSubgraph {root_entity_ids: entity_ids, depth: 2}
        TB-->>S: Subgraph {nodes, edges}
    end

    S->>S: Serialise to Parquet (metrics.parquet, traces.parquet, logs.parquet)<br/>Serialise topology to JSON (topology/subgraph.json)
    S->>S: Compute SHA-256 per file
    S->>S: Build manifest.json {schema_version, files: [{name, sha256, size}], window, entity_ids}

    S->>M: PUT s3://pulsegraph-snapshots/{tenant_id}/{incident_id}/metrics/metrics.parquet
    S->>M: PUT s3://pulsegraph-snapshots/{tenant_id}/{incident_id}/traces/traces.parquet
    S->>M: PUT s3://pulsegraph-snapshots/{tenant_id}/{incident_id}/logs/logs.parquet
    S->>M: PUT s3://pulsegraph-snapshots/{tenant_id}/{incident_id}/topology/subgraph.json
    S->>M: PUT s3://pulsegraph-snapshots/{tenant_id}/{incident_id}/manifest.json<br/>[Object-lock COMPLIANCE, retain 90 days]

    S->>PG: UPDATE incidents SET snapshot_uri, snapshot_sha256, snapshot_frozen_at, snapshot_status=frozen

    S->>K: PRODUCE SnapshotRef {incident_id, tenant_id, s3_uri, manifest_sha256, degraded=false}
    S->>K: COMMIT offset for SnapshotCommand

    Note over S,M: If MinIO unreachable after 5 min retry:<br/>PRODUCE SnapshotRef {degraded=true}<br/>UPDATE incidents SET snapshot_status=degraded
```

---

## 6. Sequence Diagram: RCA Pipeline

```mermaid
sequenceDiagram
    autonumber
    participant K4  as Kafka: snapshot.refs
    participant R   as rca-engine (Python)
    participant M   as MinIO/S3
    participant TB  as topology-builder
    participant CH  as ClickHouse
    participant LLM as LLM API (optional)
    participant K5  as Kafka: rca.results
    participant QA  as query-api
    participant PG  as PostgreSQL

    K4-->>R: CONSUME SnapshotRef
    R->>R: Check degraded flag; if true, skip bundle read

    alt Bundle available
        R->>M: GET manifest.json; verify SHA-256
        R->>M: GET metrics.parquet, traces.parquet, logs.parquet, topology/subgraph.json
        R->>R: Load into in-process DataFrames (pyarrow)
    end

    R->>TB: GetSubgraph {entity_ids from SnapshotRef, depth=2}
    TB-->>R: Live topology subgraph

    par Strategy: Rule Engine
        R->>R: Evaluate YAML rules against OTel attributes<br/>Fire matching rules → Hypothesis list
    and Strategy: Graph Analyst
        R->>R: Build networkx.DiGraph from subgraph<br/>Reverse-BFS from anomaly nodes<br/>Score nodes by decaying blame weight
    and Strategy: Anomaly ML
        R->>CH: SELECT 7-day metric baseline for entity_ids
        CH-->>R: Baseline rows
        R->>R: Fit/score IsolationForest on snapshot metrics vs baseline<br/>Compute z-scores per metric × entity
    end

    R->>R: Merge hypotheses from all strategies<br/>Borda count + weight fusion → ranked list<br/>Assign final confidence [0,1]<br/>Attach evidence refs (snapshot-relative paths)

    opt LLM enabled AND top hypothesis confidence > 0.5
        R->>LLM: POST /chat/completions {system prompt, hypothesis JSON, evidence excerpts}
        LLM-->>R: narrative string
        Note over R,LLM: Timeout 30 s; failure → narrative=null
    end

    R->>R: Assemble RCAResult {incident_id, hypotheses[], narrative?, strategy_versions, run_id}
    R->>M: PUT rca/{run_id}.json (append to snapshot bundle)
    R->>K5: PRODUCE RCAResult
    R->>K4: COMMIT offset for SnapshotRef

    K5-->>QA: CONSUME RCAResult
    QA->>PG: UPSERT rca_results; INSERT rca_hypotheses (rank, confidence, evidence_refs, narrative)
    QA->>QA: Fanout RCA_READY event to WebSocket hub → push to subscribed console sessions
```

---

## 7. Sequence Diagram: Query Path

```mermaid
sequenceDiagram
    autonumber
    participant U   as Engineer (browser)
    participant C   as console (React)
    participant AG  as api-gateway
    participant QA  as query-api
    participant PG  as PostgreSQL
    participant CH  as ClickHouse
    participant OS  as OpenSearch
    participant M   as MinIO/S3
    participant TB  as topology-builder

    U->>C: Open incident detail page /incidents/{id}
    C->>AG: GET /v1/incidents/{id}  [JWT]
    AG->>AG: Validate JWT; check RBAC (viewer role); inject tenant_id
    AG->>QA: GET /v1/incidents/{id}  [gRPC proxy]
    QA->>PG: SELECT incident + rca_hypotheses WHERE id={id} AND tenant_id={tenant}
    PG-->>QA: IncidentRow + HypothesisRows
    QA-->>AG: IncidentDetail (JSON)
    AG-->>C: 200 IncidentDetail

    C->>AG: GET /v1/incidents/{id}/snapshot  [JWT]
    AG->>QA: GET /v1/incidents/{id}/snapshot
    QA->>M: GeneratePresignedURL (15 min TTL)
    M-->>QA: presigned_url
    QA-->>C: {presigned_url, manifest_sha256}
    C->>M: GET presigned_url → manifest.json
    C->>C: Verify SHA-256 of manifest in-browser

    C->>AG: GET /v1/topology/graph/{service}?depth=2  [JWT]
    AG->>QA: gRPC → TopologyService.GetSubgraph
    QA->>TB: GetSubgraph {service, depth=2, tenant_id}
    TB-->>QA: Subgraph proto
    QA-->>C: Subgraph JSON
    C->>C: Render @xyflow/react topology graph

    C->>AG: GET /v1/metrics/query_range?query=...&start=...&end=...
    AG->>QA: MetricQueryRange
    QA->>CH: SELECT ... FROM metrics WHERE tenant_id=... AND ts BETWEEN ... AND ...
    CH-->>QA: Result rows
    QA-->>C: {data: [{metric, values}]}
    C->>C: Render recharts time-series
```

---

## 8. Data Schemas

### 8.1 PostgreSQL Tables (key tables only)

```sql
-- Incident record
CREATE TABLE incidents (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL,
    severity         TEXT NOT NULL CHECK (severity IN ('critical','high','medium','low')),
    status           TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open','resolved','closed')),
    title            TEXT,
    started_at       TIMESTAMPTZ NOT NULL,
    resolved_at      TIMESTAMPTZ,
    trigger_source   TEXT,          -- 'correlator' | 'webhook.datadog' | 'webhook.dynatrace'
    trigger_labels   JSONB,
    root_entities    TEXT[],
    fingerprint      TEXT,          -- SHA-1 for dedup
    snapshot_uri     TEXT,
    snapshot_sha256  TEXT,
    snapshot_frozen_at TIMESTAMPTZ,
    snapshot_status  TEXT DEFAULT 'pending' CHECK (snapshot_status IN ('pending','collecting','frozen','degraded')),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- RCA run
CREATE TABLE rca_results (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    incident_id      UUID NOT NULL REFERENCES incidents(id),
    run_id           UUID NOT NULL,
    status           TEXT NOT NULL DEFAULT 'running',
    started_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at     TIMESTAMPTZ,
    strategy_versions JSONB,        -- {rule_engine: "1.2.0", graph_analyst: "0.4.1", ...}
    narrative        TEXT           -- LLM-generated, nullable
);

-- Individual hypothesis
CREATE TABLE rca_hypotheses (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rca_result_id    UUID NOT NULL REFERENCES rca_results(id),
    rank             INT NOT NULL,
    confidence       FLOAT NOT NULL,
    summary          TEXT NOT NULL,
    strategies       TEXT[],
    evidence_refs    JSONB,         -- [{type, entity, metric/log, snapshot_ref, value, ts}]
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Topology edges (durable store for topology-builder)
CREATE TABLE topology_edges (
    id               BIGSERIAL PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    source_service   TEXT NOT NULL,
    dest_service     TEXT NOT NULL,
    protocol         TEXT,
    call_rate        FLOAT,
    error_rate       FLOAT,
    p99_latency_ms   FLOAT,
    last_seen_at     TIMESTAMPTZ NOT NULL,
    stale            BOOLEAN NOT NULL DEFAULT false,
    UNIQUE (tenant_id, source_service, dest_service)
);
```

### 8.2 ClickHouse Tables

```sql
-- Metrics
CREATE TABLE metrics (
    tenant_id        LowCardinality(String),
    service_name     LowCardinality(String),
    metric_name      LowCardinality(String),
    unit             LowCardinality(String),
    ts               DateTime64(9, 'UTC'),
    value            Float64,
    resource_attrs   Map(String, String),
    scope_name       LowCardinality(String),
    schema_version   UInt8
) ENGINE = MergeTree()
PARTITION BY (tenant_id, toYYYYMMDD(ts))
ORDER BY (tenant_id, service_name, metric_name, ts)
TTL ts + INTERVAL 90 DAY;

-- Traces (flattened spans)
CREATE TABLE traces (
    tenant_id        LowCardinality(String),
    trace_id         FixedString(32),
    span_id          FixedString(16),
    parent_span_id   FixedString(16),
    service_name     LowCardinality(String),
    span_name        String,
    span_kind        UInt8,
    start_ts         DateTime64(9, 'UTC'),
    end_ts           DateTime64(9, 'UTC'),
    duration_ns      UInt64,
    status_code      UInt8,
    resource_attrs   Map(String, String),
    span_attrs       Map(String, String),
    schema_version   UInt8
) ENGINE = MergeTree()
PARTITION BY (tenant_id, toYYYYMMDD(start_ts))
ORDER BY (tenant_id, service_name, trace_id, start_ts)
TTL start_ts + INTERVAL 30 DAY;
```

### 8.3 OpenSearch Index Template (Logs)

```json
{
  "index_patterns": ["pulsegraph-logs-*"],
  "template": {
    "settings": {
      "number_of_shards": 3,
      "number_of_replicas": 1,
      "index.lifecycle.name": "pulsegraph-logs-30d"
    },
    "mappings": {
      "properties": {
        "tenant_id":     { "type": "keyword" },
        "service_name":  { "type": "keyword" },
        "@timestamp":    { "type": "date_nanos" },
        "severity_text": { "type": "keyword" },
        "severity_number": { "type": "short" },
        "body":          { "type": "text", "analyzer": "standard" },
        "trace_id":      { "type": "keyword" },
        "span_id":       { "type": "keyword" },
        "resource_attrs":{ "type": "flattened" },
        "log_attrs":     { "type": "flattened" }
      }
    }
  }
}
```

---

## 9. Configuration & Feature Flags

All services read configuration from environment variables (12-factor). Secrets are injected via Vault agent / Docker secrets.

| Key | Service | Default | Description |
|-----|---------|---------|-------------|
| `KAFKA_BROKERS` | all | `redpanda:9092` | Comma-separated broker list |
| `KAFKA_CONSUMER_GROUP` | each | per-service constant | Consumer group ID |
| `CLICKHOUSE_DSN` | normalizer, query-api, rca-engine | `http://clickhouse:8123` | ClickHouse HTTP endpoint |
| `OPENSEARCH_URL` | normalizer, query-api | `http://opensearch:9200` | OpenSearch endpoint |
| `POSTGRES_DSN` | ingest-gateway, correlator, snapshotter, query-api, topology-builder | `postgres://pg:5432/pulsegraph` | PostgreSQL connection string |
| `S3_ENDPOINT` | snapshotter, rca-engine, query-api | `http://minio:9000` | Object store endpoint |
| `S3_BUCKET_SNAPSHOTS` | snapshotter, rca-engine | `pulsegraph-snapshots` | Snapshot bucket name |
| `TOPOLOGY_GRPC_ADDR` | correlator, snapshotter, rca-engine, query-api | `topology-builder:9090` | topology-builder gRPC address |
| `RCA_LLM_ENABLED` | rca-engine | `false` | Enable LLM narrator |
| `RCA_LLM_PROVIDER` | rca-engine | `openai` | `openai` or `anthropic` |
| `RCA_LLM_MODEL` | rca-engine | `gpt-4o` | Model name |
| `RCA_LLM_API_KEY` | rca-engine | — | Secret; injected via Vault |
| `INGEST_RATE_LIMIT_DEFAULT` | ingest-gateway | `10000` | Default records/sec per tenant |
| `SNAPSHOT_WORM_RETENTION_DAYS` | snapshotter | `90` | Object-lock retention in days |
| `CORRELATOR_ANOMALY_ZSCORE_THRESHOLD` | correlator | `3.5` | z-score threshold for anomaly |
| `JWKS_URI` | api-gateway | — | OIDC JWKS endpoint |

---

## 10. Testing Strategy

| Layer | Tool | Coverage Target |
|-------|------|----------------|
| Unit | Go: `testing` + `testify`; Python: `pytest` | ≥ 80 % per service |
| Integration | `docker-compose.test.yml` + `testcontainers-go`; real Redpanda + ClickHouse + Postgres | Happy path + failure injection per service |
| Contract | `buf breaking` on proto changes in CI; consumer-driven tests via Pact (adapter ↔ ingest-gateway) | Block merges on breaking changes |
| End-to-end | `scripts/seed-demo-telemetry.sh` pushes synthetic MELT signals; assertions via `query-api` REST | Incident created → snapshot frozen → RCA published within 90 s |
| Chaos | Toxiproxy injected in test compose for network partition + latency; validates DLQ routing and degraded-mode fallbacks | Per-release gate |
| Performance | k6 load test: 50 k events/s ingest, measure P99 normalizer lag and snapshot latency | Baseline regression guard |

---

*ADRs for major architectural decisions are in [adr/](./adr/).*
