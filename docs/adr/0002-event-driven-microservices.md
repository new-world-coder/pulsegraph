# ADR-0002: Event-Driven Microservices Architecture

| Field | Value |
|-------|-------|
| **Status** | Accepted |
| **Date** | 2026-07-11 |
| **Deciders** | Platform Architecture, SRE Lead |
| **Supersedes** | — |
| **Superseded by** | — |

---

## Context

PulseGraph must ingest, process, and correlate high-volume MELT telemetry in real time from multiple vendors, produce immutable incident snapshots, and run multi-strategy RCA analysis — all without a single failure domain bringing the entire platform down. The system must also support independent scaling of each processing stage (ingest volume vs. RCA compute are very different workload profiles) and allow components to be developed and deployed independently.

The two primary architectural patterns under consideration were:

1. **Monolithic / request-driven**: A single service (or small cluster of directly-coupled services) that handles ingest-to-RCA synchronously via HTTP or gRPC calls.
2. **Event-driven microservices**: Each processing stage is an independent service that reads from and writes to a shared durable message bus (Kafka/Redpanda).

## Decision

**PulseGraph is built as an event-driven microservices system with Kafka/Redpanda as the durable event bus.**

The pipeline is: **Adapter → ingest-gateway → normalizer → correlator → snapshotter → rca-engine → query-api**. Each stage is decoupled from the next via a named Kafka topic. Stages communicate exclusively through these topics; no stage calls another stage synchronously (topology-builder and storage are the only shared resources accessed via gRPC or direct DB queries).

The specific pipeline topology is:

```
[Adapters] → raw.melt → [normalizer] → norm.melt → [correlator]
                                                          │
                                               snapshot.commands → [snapshotter]
                                                          │               │
                                               corr.events         snapshot.refs → [rca-engine]
                                                          │                               │
                                                    [query-api] ← rca.results ←──────────┘
```

### Technology Choice: Kafka / Redpanda

Redpanda is selected over Apache Kafka for local development and early production:
- **No JVM dependency**: Redpanda is a C++ Kafka-compatible broker. Eliminates JVM tuning from the local compose stack and reduces memory footprint.
- **Kafka protocol compatibility**: All producers and consumers use the standard Kafka client library (`confluent-kafka-go`, `confluent-kafka-python`). Migrating to managed Kafka (Confluent Cloud, MSK) in production requires only a broker address change.
- **Built-in schema registry and Wasm transforms** (used in later phases for lightweight filtering before normalizer).

In production Kubernetes, the choice between Redpanda and managed Kafka (MSK / Confluent Cloud) is deferred to Phase 4 and treated as a configuration-level decision (see ADR scope).

## Rationale

### Options Considered

| Option | Description | Decision |
|--------|-------------|----------|
| **A. Synchronous monolith** | Single Go service handles ingest → normalise → correlate → RCA in-process | Rejected |
| **B. Direct gRPC pipeline** | Each stage calls the next via gRPC; no message bus | Rejected |
| **C. Event-driven with Kafka (chosen)** | Stages decoupled via durable topics; at-least-once delivery; independent scaling | Accepted |
| **D. Event-driven with NATS JetStream** | Lighter-weight alternative to Kafka | Not chosen at this time |
| **E. Serverless / FaaS per stage** | AWS Lambda or Knative functions per processing step | Rejected |

### Why Not Option A (Monolith)

- Ingest (I/O-bound, high throughput) and RCA (CPU-bound, Python ML, potentially GPU) have fundamentally different resource profiles. Co-locating them in one process requires over-provisioning for the more demanding workload.
- A crash in the RCA logic would take down ingest — unacceptable for a platform that must remain operational during incidents.
- Monolithic deployment means any code change requires a full redeploy of all functionality.

### Why Not Option B (Direct gRPC Pipeline)

- Tight coupling: if `normalizer` is slow or down, `ingest-gateway` blocks. Backpressure propagates synchronously through the chain.
- No durability: if `correlator` restarts, in-flight normalised events are lost unless re-requested.
- Horizontal scaling requires careful service-discovery and load-balancing at every hop.
- Replay and backfill are impossible without a durable log.

### Why Not Option D (NATS JetStream)

NATS JetStream provides durability and consumer groups but has a smaller ecosystem of operational tooling compared to Kafka. ClickHouse, Redpanda Console, and most observability tooling have first-class Kafka integrations. NATS remains a candidate for lightweight internal control-plane events in future phases.

### Why Not Option E (FaaS)

- Cold-start latency is incompatible with the ≤ 5 s P99 ingest SLO.
- Stateful windowing (correlator's sliding-window anomaly detection) is difficult to implement correctly in stateless functions.
- Python ML workers (rca-engine) require heavy runtime environments that conflict with FaaS cold-start constraints.

### Why Event-Driven Kafka

**Durability & Replay**: Kafka's durable log means any stage can be stopped, upgraded, and restarted without losing events. Normalizer can replay from `raw.melt` offset to backfill ClickHouse after a storage outage.

**Independent Scaling**: Each consumer group scales independently. During a major incident storm, `snapshotter` and `rca-engine` can scale out without affecting ingest throughput.

**Temporal Decoupling**: The rca-engine (Python, potentially slow due to ML inference) does not need to keep up with real-time ingest. It processes `snapshot.refs` at its own pace. Incidents are still frozen promptly by the fast-path Go `snapshotter`.

**Dead-Letter Queues**: Kafka's consumer group model enables per-topic DLQ routing for poison messages without complex error-handling middleware in each service.

**Audit & Debugging**: The Kafka topic log serves as an audit trail of every event processed. Replaying the `snapshot.commands` topic allows full reconstruction of any incident pipeline execution.

**Fan-out**: Multiple consumers can independently read from the same topic (e.g. `norm.melt` is consumed by both `correlator` and could be extended with a future `alertmanager` consumer) without the producer knowing.

## Consequences

### Positive

- Each service can be deployed, scaled, and rolled back independently.
- Failures in one stage (e.g. rca-engine crash) are isolated; the upstream pipeline continues.
- At-least-once delivery with idempotent consumers (deduplication by `incident_id` / `run_id`) provides strong delivery guarantees.
- Kafka consumer lag is a first-class operational metric, making pipeline health observable.
- Replay from committed offset enables zero-data-loss recovery after any downstream outage.

### Negative / Trade-offs

- **Operational overhead**: Running Kafka/Redpanda adds infrastructure complexity compared to a monolith. Mitigated by using Redpanda (simpler ops than Kafka) and managed offerings in production.
- **Eventual consistency**: The console sees incident status updates with a small lag (seconds) as events propagate through the pipeline. Synchronous read-after-write is not guaranteed for incident creation. The console must be designed for eventual consistency (polling / WebSocket push).
- **Message ordering**: Kafka guarantees ordering within a partition (keyed by `tenant_id:service`). Cross-service ordering guarantees require careful key design and are not globally guaranteed. The correlator's sliding-window state must tolerate out-of-order arrivals within its window (bounded by `max.clock.skew` config).
- **Debugging distributed pipelines**: Tracing an event through the full pipeline requires distributed trace correlation across services. Mitigated by propagating OTel `traceparent` as a Kafka message header through all hops.
- **Schema evolution**: Adding a new field to `NormMELTEvent` is backward-compatible; removing or renaming a field requires a new topic version (`norm.melt.v2`) and a dual-write migration period. Proto schema governance (enforced by `buf breaking` in CI) is essential.

## Operational Runbook Notes

- **Consumer lag monitoring**: Each consumer group's lag on its input topic is exposed as a Prometheus metric (`kafka_consumer_group_lag`). Alert thresholds: normalizer lag > 10 k records → page; snapshotter lag > 100 records → page (each record is an incident).
- **Reprocessing**: To reprocess all events through a stage (e.g. after a normalizer bug fix), reset the consumer group offset using `rpk group seek --to earliest`. Downstream stages handle duplicates via idempotent upsert by `incident_id`.
- **DLQ monitoring**: Any message routed to a `.dlq` topic fires a low-priority alert. DLQ contents are inspectable via Redpanda Console and replayable after the root cause is fixed.

## References

- [Redpanda documentation](https://docs.redpanda.com/)
- [Kafka Definitive Guide — Consumer Groups](https://www.confluent.io/resources/kafka-the-definitive-guide/)
- [CNCF Landscape: Streaming & Messaging](https://landscape.cncf.io/card-mode?category=streaming-messaging)
- ADR-0001: OpenTelemetry as the Canonical Internal Data Model
