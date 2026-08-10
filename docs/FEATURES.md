# Implemented Features Catalog

**Product:** PulseGraph  
**Version baseline:** branch `cursor/mvp-otel-ingest-pipeline` / main (post-merge)  
**Related:** [COMPETITIVE_ANALYSIS.md](COMPETITIVE_ANALYSIS.md) (full BF-001–052 backlog) · [CONTEXT_GRAPH.md](CONTEXT_GRAPH.md)

This document lists **what is actually implemented today**, maps each item to the **best-of-breed competitor feature** it emulates, and states honesty about gaps.

**Status legend**

| Status | Meaning |
|--------|---------|
| ✅ Implemented | Works end-to-end in current code |
| 🔶 Partial | Core path works; persistence, UI, or enterprise polish missing |
| 🧾 Spec only | Designed in HLD/requirements; stub or absent in runtime |

---

## 1. Executive feature map

| Area | PulseGraph today | Closest competitor analogue |
|------|------------------|----------------------------|
| OTLP ingest | ✅ | Datadog / Dynatrace / New Relic / Chronosphere OTel receivers |
| Canonical MELT envelope | 🔶 | OTel semantic conventions + collector processors |
| Kafka event pipeline | ✅ | Internal to all large platforms (not user-facing) |
| Context Graph | 🔶 | Dynatrace **Smartscape**, Chronosphere **Temporal Knowledge Graph**, Datadog **Service Map** |
| Blast radius API | 🔶 | Datadog **Impact Analysis**, Dynatrace Davis blast views |
| Incident telemetry freeze | 🔶 | PulseGraph differentiator; closest: Rootly Datadog *screenshot*, not queryable freeze |
| Vendor adapters DD/NR/DT | 🧾 HTTP stubs | N/A (overlay unique) |
| Causal RCA engine | 🧾 Stub | Dynatrace **Davis**, Datadog **Bits AI** / Watchdog |
| Incident lifecycle (Slack) | 🧾 | incident.io, **Rootly** (SF), FireHydrant |
| Console UI / Agentic War Room | 🔶 UI live (agents simulated) | Agentic SRE War Room pattern; Datadog Bits; incident.io AI |
| Compliant Agent control plane | 🔶 Service live (in-memory) | No direct analogue; AI governance stacks (Credo AI, Holistic AI) + platform guardrails |

---

## 2. Implemented & partial features (detail)

### F-01 — OTLP/HTTP JSON ingest  
| | |
|--|--|
| **Status** | ✅ Implemented |
| **BF ID** | BF-043 |
| **Inspired by** | OpenTelemetry Collector OTLP receiver; Datadog / Dynatrace / New Relic OTel ingest |
| **Code** | `services/adapter-otel` |
| **Behavior** | Accepts `POST /v1/traces|metrics|logs` (JSON), extracts `service.name` / environment, wraps `melt.Envelope`, forwards to ingest-gateway |
| **Gap vs leaders** | No OTLP/gRPC or protobuf yet; JSON-only MVP |

### F-02 — Ingest gateway validation & Kafka publish  
| | |
|--|--|
| **Status** | ✅ Implemented |
| **BF ID** | BF-046 (partial), pipeline foundation |
| **Inspired by** | Internal edge of Chronosphere / Honeycomb ingest; OTel Collector gateway pattern |
| **Code** | `services/ingest-gateway`, `packages/melt`, `packages/kafkautil` |
| **Behavior** | `POST /v1/ingest` validates envelope, publishes to topic `raw.melt` |
| **Gap** | No authn, rate limit, or tenant isolation enforcement |

### F-03 — Normalization stage  
| | |
|--|--|
| **Status** | 🔶 Partial |
| **BF ID** | BF-046, BF-007 (partial) |
| **Inspired by** | OTel semantic conventions; collector “transform/attributes” processors |
| **Code** | `services/normalizer` |
| **Behavior** | Consumes `raw.melt`, stamps `pulsegraph.*` labels, sets `Normalized=true`, publishes `norm.melt` |
| **Gap** | Full vendor field remapping (`dd.service` → `service.name`) not complete; DD/NR/DT adapters not live |

### F-04 — Context Graph (entity / dependency graph)  
| | |
|--|--|
| **Status** | 🔶 Partial |
| **BF ID** | BF-001, BF-002 (partial), BF-005 |
| **Inspired by** | **Dynatrace Smartscape**; Chronosphere Temporal Knowledge Graph; Datadog Service Map; New Relic entity map |
| **Code** | `packages/contextgraph`, `services/topology-builder` |
| **Behavior** | Consumes `norm.melt` traces; builds directed multigraph (services + edge kinds); HTTP subgraph export; publishes `topology.changes` |
| **APIs** | `GET /v1/graph`, `/v1/graph/subgraph`, `/v1/graph/blast-radius` on `:8091` |
| **Gap** | In-memory (no OpenSearch/Postgres durability yet); limited infra entity auto-discovery; no temporal history (BF-004/026) |

### F-05 — Blast-radius traversal  
| | |
|--|--|
| **Status** | 🔶 Partial |
| **BF ID** | BF-003 |
| **Inspired by** | Datadog **Watchdog Impact Analysis**; Dynatrace problem impact / Davis |
| **Code** | `packages/contextgraph` (`BlastRadius`), exposed by topology-builder |
| **Gap** | No traffic/revenue weighting; not yet auto-attached to incident objects |

### F-06 — Incident-triggered snapshot freeze  
| | |
|--|--|
| **Status** | 🔶 Partial |
| **BF ID** | BF-022 (PulseGraph differentiator) |
| **Inspired by / vs** | **Rootly** embeds Datadog graph *screenshots* in incidents — not immutable queryable telemetry. Datadog Watchdog captures insight state but not multi-vendor WORM bundles. |
| **Code** | `packages/snapshot`, `services/snapshotter` |
| **Behavior** | Buffers `norm.melt`; on `snapshot.commands` or `POST /v1/freeze` captures pre/post window (default 10m/2m), pulls topology subgraph, writes MinIO bundle + SHA-256 manifest, publishes `snapshot.refs` |
| **Bundle** | `manifest.json`, `metrics.json`, `logs.json`, `traces.json`, `topology/subgraph.json` |
| **Gap** | No Parquet yet; no Object-Lock/WORM (BF-023); no ClickHouse historical pull — ring buffer only |

### F-07 — Local infrastructure data plane  
| | |
|--|--|
| **Status** | ✅ Implemented (dev/pilot) |
| **BF ID** | BF-010 (foundation only) |
| **Inspired by** | Platform backends (Datadog/NR/DT managed stores) — we self-host for overlay control |
| **Code** | `docker-compose.yml`, `scripts/bootstrap.sh` |
| **Components** | Redpanda, ClickHouse, OpenSearch, MinIO, Postgres |
| **Gap** | ClickHouse/OpenSearch not fully written by normalizer yet |

### F-08 — Event-driven microservice layout  
| | |
|--|--|
| **Status** | ✅ Implemented (architecture + several live stages) |
| **Inspired by** | Internal architecture of hyperscale observability (ingest → stream → analyze), ADR-0002 |
| **Code** | `services/*`, `docs/adr/0002-event-driven-microservices.md` |
| **Topics** | `raw.melt`, `norm.melt`, `snapshot.commands`, `snapshot.refs`, `topology.changes`, … |

### F-09 — Competitive & requirements documentation  
| | |
|--|--|
| **Status** | ✅ Implemented (docs) |
| **Inspired by** | Product practice at Rootly / incident.io / Dynatrace (feature matrices, ADRs) |
| **Code** | `docs/COMPETITIVE_ANALYSIS.md`, `docs/REQUIREMENTS.md`, `docs/HLD.md`, this file |

### F-10 — Agentic War Room console  
| | |
|--|--|
| **Status** | 🔶 Partial (frontend + simulated agents; freeze wired when snapshotter up) |
| **BF / competitor** | Market **Agentic War Room** (5-step); Bits Investigation; incident.io AI SRE |
| **Code** | `apps/console` (`WarRoom.tsx`), [AGENTIC_WAR_ROOM_GAPS.md](AGENTIC_WAR_ROOM_GAPS.md), [CONSOLE_UX.md](CONSOLE_UX.md) |
| **Behavior** | Intent → Triage/Metrics/Logs/Correlate/RCA/Remediate rail; blast-radius panel; evidence freeze CTA; human approve remediation (no silent auto-exec) |
| **Gap** | Real agent backends, Agent Chat, predictive intel, Dashboard GPT (see [AGENTIC_WAR_ROOM_GAPS.md](AGENTIC_WAR_ROOM_GAPS.md)) |

### F-11 — Compliant Agent control plane  
| | |
|--|--|
| **Status** | 🔶 Partial (service live end-to-end; stores in-memory) |
| **BF / competitor** | No single competitor owns this; AI-governance tooling (Credo AI, Holistic AI) + hyperscaler guardrails; PulseGraph-native governance-graph angle |
| **Code** | `java/services/compliance-guardian`, `java/libs/compliance`, [COMPLIANT_AGENT.md](COMPLIANT_AGENT.md) |
| **Behavior** | Agent registry; autonomy policy (OBSERVE/RECOMMEND/REQUIRE_APPROVAL/EXECUTE) with least-privilege + data-governance checks; human dual/four-eyes approvals; SHA-256 hash-chained audit trail with verification; global + per-agent kill switch; one-click regulatory evidence packs |
| **APIs** | `/v1/agents`, `/v1/authorize`, `/v1/approvals/{id}/decide`, `/v1/audit`, `/v1/audit/verify`, `/v1/kill-switch/*`, `/v1/evidence-packs/{incidentId}` on `:8096` |
| **Regulatory mapping** | EU AI Act (Art.11–14), DORA (Art.5, 17–19, 24–30), MAS FEAT/Veritas/MindForge, NIST AI RMF, ISO 42001 |
| **Gap** | Persistence is in-memory (no WORM/ledger yet); no RBAC/authn; agent security-testing harness, drift monitoring, and third-party/SBOM register still on roadmap |

---

## 3. Scaffolded / stub only (not production features yet)

| Feature | Competitor reference | Repo location | Notes |
|---------|---------------------|---------------|-------|
| Datadog adapter | Datadog intake / webhooks | `services/adapter-datadog` | HTTP shell |
| New Relic adapter | NR Events / AI | `services/adapter-newrelic` | HTTP shell |
| Dynatrace adapter | DT Problems / Davis feed | `services/adapter-dynatrace` | HTTP shell |
| Correlator | Datadog Watchdog correlation; PagerDuty EI | `services/correlator` | Stub |
| RCA engine | Dynatrace Davis; Datadog Bits AI; Rootly AI RCA | `services/rca-engine` | Python stub |
| Query API | All platforms’ query UIs | `services/query-api` | Stub routes |
| Console | incident.io / Datadog UI | `apps/console` | Vite shell |
| Slack incident rooms | incident.io, Rootly | — | Not started |
| On-call | PagerDuty, Rootly, incident.io | — | Out of scope for overlay v1 |
| SLO burn alerts | Datadog SLOs, Google SRE multi-window | — | Spec in requirements |
| LLM narrative | Bits AI, incident.io AI SRE | — | Planned behind Ollama for pilot |

---

## 4. Differentiators (PulseGraph-native)

These are **not** copies of a single vendor — they are the overlay thesis:

| Differentiator | Why competitors don’t fully own it |
|----------------|-------------------------------------|
| **Multi-vendor Context Graph** | Smartscape/Service Map are strongest inside one agent ecosystem |
| **Immutable incident freeze across MELT + graph** | Rootly’s DD snapshot is visual, not forensic bundle |
| **RCA over federated telemetry** (planned) | Davis/Bits excel on their own data lakes |
| **Sit beside incident.io/Rootly** | They own workflow; we own evidence + graph reasoning |

---

## 5. Traceability matrix (BF → status)

| BF | Name | Status in PulseGraph |
|----|------|----------------------|
| BF-001 | Real-time dependency graph | 🔶 Partial |
| BF-003 | Blast radius | 🔶 Partial |
| BF-005 | Subgraph extraction | 🔶 Partial |
| BF-007 | Multi-vendor normalize | 🔶 OTel path only |
| BF-022 | Incident freeze snapshot | 🔶 Partial |
| BF-043 | OTLP ingest | ✅ |
| BF-046 | Canonical mapping | 🔶 Partial |
| BF-002,004,006,008–021,023–042,044–052 | See competitive analysis | 🧾 / ❌ Missing |

Full definitions: [COMPETITIVE_ANALYSIS.md](COMPETITIVE_ANALYSIS.md).

---

## 6. How to keep this file honest

When a feature ships:

1. Move it from §3 → §2.  
2. Update status emoji.  
3. Cite the competitor analogue (even if we exceed them).  
4. Update gap analysis counts in `COMPETITIVE_ANALYSIS.md`.
