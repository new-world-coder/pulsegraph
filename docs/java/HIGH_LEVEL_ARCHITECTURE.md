# High-Level Architecture — Java Microservices Fork

---

## 1. Architectural intent

PulseGraph remains a **platform-agnostic MELT overlay**:
- Federate Datadog / New Relic / Dynatrace / OTel
- Build a **Context Graph**
- **Freeze** incident evidence
- Run **RCA** and present an **Agentic War Room** (UI)

The Java fork preserves **event-driven microservices** (ADR-0002) with **11** Spring/Quarkus services + React console.

---

## 2. C4 Context diagram

```mermaid
C4Context
    title PulseGraph Java Fork — System Context

    Person(sre, "SRE / Incident Commander", "Uses War Room console")
    Person(plat, "Platform Engineer", "Wires OTLP and webhooks")

    System(pg, "PulseGraph", "MELT overlay, Context Graph, freeze, RCA")

    System_Ext(dd, "Datadog")
    System_Ext(nr, "New Relic")
    System_Ext(dt, "Dynatrace")
    System_Ext(otel, "OpenTelemetry sources")
    System_Ext(pd, "PagerDuty / Rootly / incident.io")

    Rel(sre, pg, "Investigates via War Room")
    Rel(plat, pg, "Configures adapters")
    Rel(otel, pg, "OTLP")
    Rel(dd, pg, "Webhooks / export")
    Rel(nr, pg, "Webhooks / OTLP")
    Rel(dt, pg, "Problems / OTLP")
    Rel(pd, pg, "Incident declare → freeze")
    Rel(pg, pd, "SnapshotRef / RCA summary")
```

---

## 3. C4 Container diagram

```mermaid
C4Container
    title PulseGraph Java Fork — Containers

    Person(sre, "SRE")

    Container_Boundary(pg, "PulseGraph") {
        Container(ui, "console", "React/TS", "Agentic War Room UI")
        Container(qa, "query-api", "Java", "REST BFF / reads")
        Container(adapters, "adapters ×4", "Java", "OTel DD NR DT ingress")
        Container(ig, "ingest-gateway", "Java", "Validate + publish")
        Container(norm, "normalizer", "Java", "Canonical MELT")
        Container(corr, "correlator", "Java", "Joins + triggers")
        Container(topo, "topology-builder", "Java", "Context Graph")
        Container(snap, "snapshotter", "Java", "Incident freeze")
        Container(rca, "rca-engine", "Java", "Hypothesis ranking")
    }

    ContainerDb(kafka, "Redpanda", "Kafka API", "Event bus")
    ContainerDb(ch, "ClickHouse", "Analytics", "Metrics/spans")
    ContainerDb(os, "OpenSearch", "Search", "Logs/graph persist")
    ContainerDb(minio, "MinIO", "S3", "Freeze bundles")
    ContainerDb(pgdb, "Postgres", "RDBMS", "Incidents/RBAC")

    Rel(sre, ui, "HTTPS")
    Rel(ui, qa, "REST")
    Rel(adapters, ig, "HTTP")
    Rel(ig, kafka, "raw.melt")
    Rel(norm, kafka, "consume/produce")
    Rel(corr, kafka, "norm→corr/commands")
    Rel(topo, kafka, "norm.melt")
    Rel(snap, kafka, "commands/refs")
    Rel(snap, minio, "PutObject")
    Rel(snap, topo, "subgraph HTTP")
    Rel(rca, kafka, "refs→results")
    Rel(rca, minio, "Get bundle")
    Rel(qa, pgdb, "JDBC")
    Rel(qa, ch, "SQL")
    Rel(qa, os, "REST")
    Rel(qa, topo, "graph API")
```

---

## 4. Logical pipeline (high level)

```mermaid
flowchart TB
  subgraph Adapters["Adapters (Java) ×4"]
    OTEL[adapter-otel]
    DD[adapter-datadog]
    NR[adapter-newrelic]
    DT[adapter-dynatrace]
  end

  IG[ingest-gateway]
  NORM[normalizer]
  CORR[correlator]
  TOPO[topology-builder]
  SNAP[snapshotter]
  RCA[rca-engine]
  QA[query-api]
  UI[console War Room]

  OTEL --> IG
  DD --> IG
  NR --> IG
  DT --> IG
  IG -->|raw.melt| NORM
  NORM -->|norm.melt| CORR
  NORM -->|norm.melt| TOPO
  NORM -->|norm.melt| SNAP
  CORR -->|snapshot.commands| SNAP
  SNAP -->|snapshot.refs| RCA
  RCA -->|rca.results| QA
  TOPO -->|HTTP graph| SNAP
  TOPO -->|HTTP/gRPC| QA
  SNAP -->|MinIO| B[(snapshots)]
  QA --> UI
```

---

## 5. Deployment view (pilot)

```text
                    ┌─────────────────────────────────┐
                    │  Single VM (Oracle A1 / local)   │
                    │  docker compose: bus + stores    │
                    │  JVM processes ×11 (or compose)  │
                    │  Node: console :3000             │
                    └─────────────────────────────────┘
```

Kubernetes is **deferred** (same as Go pilot strategy). Prefer:
- One process per service JAR
- or one Compose file `deploy/java/docker-compose.yml` later

---

## 6. Non-functional targets (carry over)

| Metric | Target |
|--------|--------|
| Ingest→norm p95 | ≤ 30s under pilot load |
| Freeze API | seconds when MinIO/topology healthy |
| query-api p95 | ≤ 2s |
| RCA first hypothesis | ≤ 5 min (async) |

---

## 7. Migration / dual-run

1. Keep Kafka contracts identical.  
2. Run Java `normalizer` beside Go with a **new consumer group** offline, then cut group.  
3. Swap adapters last (ingress risk).  
4. Console unchanged throughout.
