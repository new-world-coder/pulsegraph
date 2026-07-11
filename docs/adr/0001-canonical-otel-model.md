# ADR-0001: OpenTelemetry as the Canonical Internal Data Model

| Field | Value |
|-------|-------|
| **Status** | Accepted |
| **Date** | 2026-07-11 |
| **Deciders** | Platform Architecture, SRE Lead |
| **Supersedes** | — |
| **Superseded by** | — |

---

## Context

PulseGraph ingests MELT telemetry (Metrics, Events, Logs, Traces) from multiple observability vendors — Datadog, New Relic, Dynatrace — as well as from OpenTelemetry-instrumented services emitting native OTLP payloads. Each vendor exposes a proprietary schema for its data types:

- **Datadog**: `DogStatsD` metrics format, Datadog APM JSON spans, structured log JSON with `ddtags`, alert webhook payloads.
- **New Relic**: MELT API (Metric, Event, Log, Trace) with New Relic-specific `entity.guid` and `attributes` bags.
- **Dynatrace**: Davis AI problem JSON, metric ingest API (Dynatrace metric line protocol), OneAgent trace format.
- **OTel native**: OTLP Protobuf with `ResourceSpans`, `ResourceMetrics`, `ResourceLogs` structures and standardised semantic conventions.

Without a unified internal model, every service (correlator, snapshotter, rca-engine, query-api) would need to understand all four vendor schemas. This creates O(N×M) translation complexity, makes adding new vendor adapters expensive, and prevents consistent querying across sources.

## Decision

**PulseGraph adopts OpenTelemetry (OTel) as its single canonical internal data model for all MELT signals.**

Concretely:
1. Each vendor adapter translates its native payload into OTLP `ExportRequest` protos immediately at the ingest boundary, before any data enters the internal Kafka pipeline.
2. All internal services (normalizer, correlator, snapshotter, rca-engine, query-api) operate exclusively on the OTel-normalised `NormMELTEvent` proto, which is a tenant-scoped wrapper around standard OTLP structures plus PulseGraph-specific metadata fields.
3. The `normalizer` service applies OTel Semantic Conventions (v1.26+) to enforce attribute naming consistency (e.g. `http.request.method` not `http.method`; `server.address` not `net.host.name`).
4. Vendor-specific identifiers (Datadog `ddtags`, New Relic `entity.guid`) are preserved in a `vendor_attrs` map within the resource attributes — never discarded — to enable round-trip fidelity and cross-tool linking.
5. Storage schemas (ClickHouse, OpenSearch, Parquet in snapshots) are defined as projections of the canonical OTel model, not of any vendor schema.

## Rationale

### Options Considered

| Option | Description | Rejected because |
|--------|-------------|-----------------|
| **A. Per-vendor internal model** | Each vendor has its own internal representation; services handle all formats | O(N×M) complexity; adding a new vendor requires changes to every downstream service |
| **B. PulseGraph-proprietary model** | Define a bespoke internal schema independent of any standard | Maintenance burden; no ecosystem tooling; impedance mismatch with OTel SDK users |
| **C. OTel as canonical (chosen)** | Translate to OTLP at the boundary; all internals use OTel | See consequences below |
| **D. Hybrid: OTel for traces, proprietary for metrics/logs** | Partial adoption | Inconsistent querying; still requires per-type translation in downstream services |

### Why OTel

- **Industry standard**: OTel is CNCF-graduated and has first-class support from all major observability vendors. Datadog, New Relic, and Dynatrace all export OTLP natively or have published OTLP mappings.
- **Rich semantic conventions**: OTel Semantic Conventions define attribute names for HTTP, RPC, database, messaging, and infrastructure signals — reducing the need for ad-hoc field naming decisions.
- **Tooling ecosystem**: Prometheus, Grafana, Jaeger, and ClickHouse all have OTel-native integrations. The OTel Collector is available as a composable pipeline for preprocessing before PulseGraph.
- **Single proto dependency**: All internal services share one set of generated proto types (`go.opentelemetry.io/proto/otlp`). This eliminates N separate vendor SDKs from internal services.
- **Vendor extensibility**: Adding a new vendor (e.g. Elastic APM, Honeycomb) requires only a new adapter — no changes to normalizer, correlator, or rca-engine.

### Translation Fidelity

Each adapter maintains a documented field mapping table in `services/adapter-{vendor}/docs/field-mapping.md`. Unmapped vendor fields are placed in `resource.attributes["pulsegraph.vendor_raw.*"]` to preserve them without polluting the canonical namespace. This ensures:
- No data is silently discarded at the adapter boundary.
- Vendor-specific RCA rules can reference vendor-native fields via `vendor_raw`.

## Consequences

### Positive

- Downstream services have a single, stable, well-documented schema to code against.
- Query API can offer a unified query surface (metrics, traces, logs) regardless of source vendor.
- New vendor adapters can be added without touching any service beyond the new adapter itself.
- OTel Semantic Conventions provide a shared vocabulary for RCA rules and alert predicates.
- Snapshots are self-describing and portable — any OTel-compatible tool can read them.

### Negative / Trade-offs

- **Translation latency**: Each adapter performs a translation step, adding ~1–3 ms per batch. Acceptable given the 5 s P99 ingest SLO.
- **Fidelity loss risk**: OTel Semantic Conventions may not have standardised attributes for all vendor-specific concepts. Mitigated by the `vendor_raw` preservation strategy; reviewed quarterly as OTel SemConv evolves.
- **OTel SemConv version lock**: PulseGraph normalizes to a pinned SemConv version (currently 1.26). Upstream SemConv changes require normalizer updates. Mitigated by the `schema_version` field in `NormMELTEvent` and versioned ClickHouse migrations.
- **Adapter per vendor**: Four adapters to maintain. Offset by the simplicity gained in all downstream services.

## Compliance

All code changes that add or modify OTel attribute mappings in adapters or the normalizer **must** include:
1. An updated `field-mapping.md` for the affected adapter.
2. A unit test asserting the translated attribute name and value.
3. A `buf breaking` CI check confirming no proto breaking changes.

## References

- [OpenTelemetry Specification](https://opentelemetry.io/docs/specs/otel/)
- [OTel Semantic Conventions v1.26](https://opentelemetry.io/docs/specs/semconv/)
- [OTLP Protobuf Definition](https://github.com/open-telemetry/opentelemetry-proto)
- [Datadog → OTLP mapping (Datadog docs)](https://docs.datadoghq.com/opentelemetry/)
- [New Relic OTLP endpoint](https://docs.newrelic.com/docs/opentelemetry/opentelemetry-introduction/)
- [Dynatrace OTLP ingest](https://docs.dynatrace.com/docs/ingest-from/opentelemetry)
- ADR-0002: Event-Driven Microservices Architecture
