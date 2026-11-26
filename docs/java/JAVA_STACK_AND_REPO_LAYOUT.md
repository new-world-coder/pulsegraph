# Java Stack Recommendation & Repo Layout

**Status:** **Approved** — Spring Boot 3 / Java 21 · RCA = Java orchestrator + Python sidecar · scaffolding under `java/`

---

## 1. Recommended stack

| Concern | Choice | Rationale |
|---------|--------|-----------|
| Runtime | Java 21 LTS | Virtual threads friendly for gateway/adapters |
| Framework | **Spring Boot 3.3+** | Ecosystem for Kafka, Actuator, Security; team familiarity |
| Alt | Quarkus 3 (native optional) | If startup/RSS on Oracle free VM is critical |
| Messaging | `spring-kafka` | Maps cleanly to current Redpanda topics |
| HTTP | Spring MVC or WebFlux for adapters | WebFlux optional for OTLP fan-in |
| JSON | Jackson | Envelope/command/ref parity |
| Persistence | Spring Data JDBC / JPA (Postgres); ClickHouse JDBC | Keep simple |
| Object store | AWS SDK v2 S3 client → MinIO | Same as Go minio-go role |
| Build | Maven multi-module **or** Gradle | Prefer **Maven** for clarity in enterprise forks |
| Observability | Micrometer + OTLP | Dogfood into adapter-otel |
| API docs | springdoc-openapi | query-api / freeze |

**RCA ML:** Phase 1 rules + graph in Java; Phase 2 DJL/ONNX or keep a thin Python sidecar called by `rca-engine`.

---

## 2. Proposed monorepo layout (new tree, do not delete Go yet)

```text
pulsegraph/
  services/          # existing Go (remain until cutover)
  apps/console/      # React (shared by both stacks)
  docs/java/         # this blueprint
  java/              # NEW — created only after approval
    pom.xml          # parent
    libs/
      melt/
      kafka/
      contextgraph/
      snapshot/
      common/
    services/
      adapter-otel/
      adapter-datadog/
      adapter-newrelic/
      adapter-dynatrace/
      ingest-gateway/
      normalizer/
      correlator/
      topology-builder/
      snapshotter/
      rca-engine/
      query-api/
    deploy/
      compose.override.example.yml
```

Dual-run: Go and Java services must **not** share the same consumer group IDs until cutover.

---

## 3. Implementation phases (after approval)

| Phase | Deliverable |
|-------|-------------|
| J0 | Parent POM + `melt` + `kafka` libs + empty service shells |
| J1 | `ingest-gateway` + `adapter-otel` + `normalizer` (parity with Go MVP A) |
| J2 | `topology-builder` + `snapshotter` |
| J3 | `correlator` + `query-api` + wire console |
| J4 | `rca-engine` Java + vendor adapters |
| J5 | Decommission Go services behind feature flags |

---

## 4. Explicit non-goals for first Java PR

- Rewriting React console to Java/Vaadin  
- Kubernetes Helm charts  
- Changing Kafka topic names  
- Claiming feature parity with the full predictive / agentic-SRE suite on day one  

---

## 5. Approval checklist

- [x] Confirm **11** backend services count  
- [x] Confirm **Spring Boot 3 / Java 21**  
- [x] Confirm dual-run under `java/` directory  
- [x] Confirm RCA: **Java + Python sidecar** (`:8095` + `:8096`)  
- [x] Scaffolding authorized (`java rewrite: yes`)
