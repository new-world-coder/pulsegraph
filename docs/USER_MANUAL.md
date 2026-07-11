# PulseGraph User Manual

**Audience:** SRE on-call, incident commander, platform engineer evaluating the pilot  
**Related:** [FEATURES.md](FEATURES.md) · [INTEGRATION.md](INTEGRATION.md) · [TESTING.md](TESTING.md)

---

## 1. What PulseGraph is

PulseGraph is an **AIOps / observability overlay**. It does **not** replace Datadog, New Relic, Dynatrace, or incident.io/Rootly agents.

It:

1. Ingests **MELT** (Metrics, Events, Logs, Traces) — starting with **OpenTelemetry**.  
2. Builds a **Context Graph** of services and dependencies.  
3. On incident declaration, **freezes** a time-bounded snapshot (telemetry + subgraph) into immutable object storage.  
4. (Planned) Runs **RCA** over that evidence and drafts investigation narratives.

**Positioning vs competitors**

| Tool | Role vs PulseGraph |
|------|-------------------|
| Datadog / Dynatrace / New Relic | Source systems for telemetry |
| incident.io / Rootly / FireHydrant | Incident workflow / Slack rooms — we sit beside them |
| Chronosphere / Honeycomb | Inspiration for graph + differential diagnosis — we federate, not replace |

---

## 2. Personas & primary jobs

| Persona | Job to be done in PulseGraph |
|---------|------------------------------|
| **On-call SRE** | See subgraph + freeze evidence when paged |
| **Incident commander** | Trigger freeze; share SnapshotRef URI with responders |
| **Platform engineer** | Wire OTLP / webhooks; run smoke tests |
| **Eng manager** | Review pilot MTTR / time-to-context metrics (later) |

---

## 3. Concepts glossary

| Term | Meaning |
|------|---------|
| **MELT** | Metrics, Events, Logs, Traces |
| **Envelope** | Canonical JSON wrapper (`packages/melt`) around a signal |
| **Context Graph** | Entity-relationship graph (services, edges, blast radius) |
| **Freeze / Snapshot** | Immutable capture of MELT window + topology at incident T≈0 |
| **SnapshotRef** | Kafka/API pointer to the MinIO bundle (URI + SHA-256) |
| **norm.melt** | Kafka topic of normalized envelopes |

---

## 4. Day-1 workflow (pilot)

### 4.1 Confirm the platform is up

```bash
curl -s localhost:8081/healthz   # ingest-gateway
curl -s localhost:4318/healthz   # adapter-otel
curl -s localhost:8091/healthz   # topology-builder (Context Graph)
curl -s localhost:8092/healthz   # snapshotter
```

### 4.2 Send telemetry (OTLP JSON)

Point an OpenTelemetry SDK/collector at:

```text
http://<host>:4318
```

Paths: `/v1/traces`, `/v1/metrics`, `/v1/logs` (JSON content-type for MVP).

Or use the smoke script: `make smoke-ingest` (see [TESTING.md](TESTING.md)).

### 4.3 Inspect Context Graph

```bash
curl -s 'http://localhost:8091/v1/graph/subgraph?service=checkout&depth=2' | jq .
curl -s 'http://localhost:8091/v1/graph/blast-radius?service=checkout&depth=2' | jq .
```

### 4.4 Declare an incident freeze

When an outage is declared (or a Slack/PagerDuty webhook arrives later):

```bash
curl -s -X POST http://localhost:8092/v1/freeze \
  -H 'Content-Type: application/json' \
  -d '{
    "service_name": "checkout",
    "trigger": "manual",
    "pre_window_sec": 600,
    "post_window_sec": 120,
    "depth": 2
  }' | jq .
```

Response includes `incident_id`, `bundle_uri` (`s3://snapshots/...`), and `manifest_sha256`.

### 4.5 Retrieve the freeze pointer

```bash
curl -s http://localhost:8092/v1/snapshots/<incident_id> | jq .
```

Open MinIO console (local default `http://localhost:9090`) → bucket `snapshots` → `{tenant}/{incident_id}/`.

---

## 5. Default freeze window

Aligned with HLD:

| Parameter | Default |
|-----------|---------|
| Pre-window | 10 minutes |
| Post-window | 2 minutes |
| Topology depth | 2 hops |

---

## 6. What the console shows today

`apps/console` is a **shell** (Incidents / Topology / Metrics / RCA nav). Prefer HTTP APIs + MinIO for the pilot until query-api is wired.

---

## 7. Safety & tenancy

- MVP uses `tenant_id=default` unless set on the envelope / freeze command.  
- Do not expose data-plane ports publicly (see [ORACLE_SETUP.md](ORACLE_SETUP.md)).  
- Snapshot bundles may contain log payloads — treat MinIO credentials as secret; redact PII upstream when integrating real customers ([INTEGRATION.md](INTEGRATION.md)).

---

## 8. FAQ

**Q: Do we replace incident.io?**  
A: No. Use them for Slack lifecycle; use PulseGraph for evidence + graph.

**Q: Why is my freeze `degraded: true`?**  
A: Empty MELT buffer and/or topology-builder unreachable. Send traffic first; ensure topology service is up. Reasons appear in `degraded_reasons`.

**Q: Can I use Datadog agents today?**  
A: Adapter stubs exist; production path is OTLP. See [INTEGRATION.md](INTEGRATION.md) for interim patterns (OTLP export from DD/collector, webhooks).
