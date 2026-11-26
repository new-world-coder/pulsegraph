# Sequence & Interaction Diagrams — Java Fork

All diagrams assume **Java** implementations of the named services; contracts match the Go system.

---

## 1. Steady-state MELT ingest

```mermaid
sequenceDiagram
    autonumber
    actor Src as OTel SDK / Collector
    participant AO as adapter-otel (Java)
    participant IG as ingest-gateway (Java)
    participant K as Redpanda
    participant N as normalizer (Java)
    participant T as topology-builder (Java)
    participant C as correlator (Java)

    Src->>AO: POST /v1/traces (OTLP JSON)
    AO->>AO: Extract service.name, wrap MeltEnvelope
    AO->>IG: POST /v1/ingest
    IG->>IG: Validate + tenant defaults
    IG->>K: Produce raw.melt
    IG-->>AO: 202 Accepted
    AO-->>Src: 200 {}

    K->>N: Consume raw.melt
    N->>N: Normalize labels / schema
    N->>K: Produce norm.melt

    par Fan-out on norm.melt
        K->>T: Consume (group topology-builder)
        T->>T: Upsert graph edges from spans
        T->>K: Produce topology.changes
    and
        K->>C: Consume (group correlator)
        C->>C: Join signals / detect candidates
        C->>K: Produce corr.events
    end
```

---

## 2. Incident freeze (snapshot) pipeline

```mermaid
sequenceDiagram
    autonumber
    actor IC as Incident Commander / Webhook
    participant QA as query-api (Java)
    participant K as Redpanda
    participant S as snapshotter (Java)
    participant T as topology-builder (Java)
    participant M as MinIO
    participant R as rca-engine (Java)

    IC->>QA: Declare incident / POST freeze proxy
    QA->>K: Produce snapshot.commands
    Note over S: Also buffers norm.melt in ring buffer

    K->>S: Consume snapshot.commands
    S->>S: Slice MELT window from buffer
    S->>T: GET /v1/graph/subgraph?service&depth
    alt Topology OK
        T-->>S: subgraph JSON
    else Topology down
        T-->>S: error
        S->>S: Mark degraded
    end
    S->>S: Build manifest + SHA-256
    S->>M: PutObject tenant/incident/*
    S->>K: Produce snapshot.refs
    S-->>QA: SnapshotRef (if sync path)

    K->>R: Consume snapshot.refs
    R->>M: GetObject bundle
    R->>R: Rank hypotheses
    R->>K: Produce rca.results
```

**Direct War Room path (sync):** console → Vite `/freeze` proxy → snapshotter `POST /v1/freeze` (same freeze engine; still publishes `snapshot.refs`).

---

## 3. Agentic War Room interaction (UI + backend)

```mermaid
sequenceDiagram
    autonumber
    actor SRE as SRE
    participant UI as console (React)
    participant QA as query-api (Java)
    participant S as snapshotter (Java)
    participant T as topology-builder (Java)
    participant R as rca-engine (Java)

    SRE->>UI: Enter intent + Open War Room
    UI->>UI: Animate Triage/Metrics/Logs agents (client orchestration)
    UI->>S: POST /v1/freeze (Evidence Freeze)
    S-->>UI: SnapshotRef
    UI->>T: GET blast-radius / subgraph
    T-->>UI: Graph JSON
    UI->>QA: GET /rca/{incident} (or wait Kafka→API)
    QA->>R: (async already ran) read rca.results / DB
    QA-->>UI: Hypotheses + confidence
    SRE->>UI: Approve remediation (HITL)
    Note over UI,R: Executor not auto-run in pilot
```

---

## 4. Multi-vendor adapter interaction

```mermaid
sequenceDiagram
    autonumber
    participant DD as Datadog Agent/Webhook
    participant NR as New Relic
    participant DT as Dynatrace
    participant AD as adapter-datadog
    participant AN as adapter-newrelic
    participant ADt as adapter-dynatrace
    participant IG as ingest-gateway
    participant K as raw.melt

    DD->>AD: Native / webhook
    NR->>AN: Native / OTLP
    DT->>ADt: Problem notify / OTLP
    AD->>IG: MeltEnvelope vendor=datadog
    AN->>IG: MeltEnvelope vendor=newrelic
    ADt->>IG: MeltEnvelope vendor=dynatrace
    IG->>K: Produce
```

---

## 5. Component interaction map (static)

```mermaid
flowchart LR
  subgraph SyncHTTP
    UI[console]
    QA[query-api]
    SNAP[snapshotter]
    TOPO[topology-builder]
    UI --- QA
    SNAP --- TOPO
    QA --- TOPO
  end

  subgraph KafkaBus
    RAW[raw.melt]
    NORMT[norm.melt]
    CMD[snapshot.commands]
    REF[snapshot.refs]
    RCA[rca.results]
  end

  IG[ingest-gateway] --> RAW
  RAW --> N[normalizer]
  N --> NORMT
  NORMT --> C[correlator]
  NORMT --> TOPO
  NORMT --> SNAP
  C --> CMD
  CMD --> SNAP
  SNAP --> REF
  REF --> R[rca-engine]
  R --> RCA
  RCA --> QA
```

---

## 6. Failure & degradation interactions

| Failure | Interaction behavior |
|---------|----------------------|
| Kafka unavailable | Gateway returns 503; adapters surface 502 |
| Topology down during freeze | Snapshotter writes bundle with `degraded_reasons+=topology_unavailable` |
| Empty MELT buffer | `empty_melt_window`; still writes manifest |
| RCA slow | UI shows freeze + graph first; hypotheses arrive async |
| MinIO full | Freeze fails hard; alert ops |

---

## 7. Traceability to War Room investigation steps

| War Room step | PulseGraph interaction |
|---------------|------------------------|
| Triage | Intent + severity in UI / correlator |
| Parallel investigate | Agents over freeze + live APIs |
| RCA | `rca-engine` ← `snapshot.refs` |
| Blast radius | `topology-builder` subgraph API |
| Remediate HITL | Console approve (no silent exec) |
| **+ Evidence Freeze** | `snapshotter` (PulseGraph differentiator) |
