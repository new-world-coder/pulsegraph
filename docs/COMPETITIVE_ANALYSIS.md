# PulseGraph — Competitive Analysis

**Version:** 0.1 (MVP-A baseline)
**Date:** July 2026
**Status:** Internal working document — pre-GA

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Competitor Matrix](#2-competitor-matrix)
3. [Best-of-the-Best Feature Catalog](#3-best-of-the-best-feature-catalog)
4. [Gap Analysis](#4-gap-analysis)
5. [Recommended PulseGraph Differentiators](#5-recommended-pulsegraph-differentiators)
6. [Prioritized Implementation Roadmap](#6-prioritized-implementation-roadmap)

---

## 1. Executive Summary

### What PulseGraph Is (and Is Not)

PulseGraph is a **platform-agnostic MELT observability overlay**. It does not replace the collection agents of Datadog, New Relic, or Dynatrace — it federates their output. The thesis is that no enterprise runs a single observability vendor; they run 2–4, and the gaps between them are where incidents fester and MTTD/MTTR blow out.

PulseGraph's three structural bets:

| Pillar | What it does | Why it matters |
|--------|-------------|----------------|
| **Correlation + unified Context Graph** | Normalizes MELT signals from multiple vendors into a single topology and cross-signal query surface | Engineers stop context-switching between four UIs during an incident; causal blast radius is visible in one place |
| **Incident Snapshot Freeze** | At incident declaration (or anomaly detection), immutably captures the telemetry state (metrics, logs, traces, topology) at that moment | Post-incident RCA no longer relies on reconstructing state from mutable time-series; "what did the system look like at T=0?" has a deterministic answer |
| **RCA as a layered overlay** | Applies causal reasoning (rule engine → graph analyst → ML → LLM narrator) on top of vendor data, not a replacement for it | Customers keep their DD/NR/DT licenses; PulseGraph adds a reasoning layer they could not buy from any single vendor |

### Where PulseGraph Stands Today (MVP-A)

The working ingest pipeline is:

```
OTLP/HTTP JSON → adapter-otel → ingest-gateway → raw.melt (Kafka)
                                                → normalizer → norm.melt
```

Context Graph (P0) is now partially live: `packages/contextgraph` + `topology-builder` consume `norm.melt`, build an in-memory entity graph, expose subgraph/blast-radius APIs, and publish `topology.changes`. Persistence to OpenSearch/Postgres, change/deploy nodes, and full Smartscape-parity remain outstanding. Correlator, snapshotter, rca-engine, and query-api are still stubs/scaffolds.

### Competitive Positioning

PulseGraph does not compete in the **collection agent** market. It competes in:

- **Incident intelligence**: against incident.io, Rootly, FireHydrant (who own the workflow but lack deep telemetry), and against Datadog/Dynatrace incident add-ons (who own the telemetry but are single-vendor).
- **Correlation and topology overlay**: against Chronosphere's Temporal Knowledge Graph and Dynatrace's Smartscape — but positioned as a *multi-vendor* layer, not a replacement data store.
- **AI-assisted RCA**: against Dynatrace Davis and Datadog Bits AI — but without being locked to one vendor's data model.

The window is real. No current product is simultaneously (a) vendor-neutral at the MELT layer, (b) capable of immutable snapshot freeze, and (c) providing causal graph RCA across vendor boundaries.

---

## 2. Competitor Matrix

> **Note on funding:** Amounts are omitted where not authoritatively public; qualitative descriptors reflect known funding stage as of mid-2026.

### 2.1 Incident Lifecycle / Response

| Company | HQ / Stage | Killer Features | Structural Gap vs PulseGraph |
|---------|-----------|-----------------|------------------------------|
| **incident.io** | London / Series B | Slack-native incident lifecycle; AI SRE agent that can propose next investigation steps; automated postmortems; status-page integration; on-call scheduling with escalation policies | Pure workflow layer — no native telemetry ingestion, no MELT context graph. RCA is narrative (LLM over Slack thread), not causal graph. Cannot freeze telemetry state at T=0 |
| **Rootly** | San Francisco / well-funded Series B | Workflow engine with 50–100+ integrations; Slack-first incident rooms; AI-generated retros and RCA summaries; Datadog graph snapshots embedded in incidents; native on-call; metrics widgets in incident timeline | Datadog snapshot is a *screenshot*, not an immutable queryable telemetry freeze. AI RCA is LLM over existing integrations, not a causal graph analysis. Single-vendor bias in snapshot feature |
| **FireHydrant** | New York / Series B | Runbook automation; retrospectives; service catalog with ownership; integrates PagerDuty, Datadog, Slack | Lighter on AI/RCA than incident.io or Rootly. No native telemetry; dependent on integrations. Weakening competitive position post-2024 |
| **PagerDuty** | San Francisco / Public (NYSE: PD) | Market-defining on-call and escalation; AIOps alert grouping; Event Intelligence; Automation Actions (runbook webhooks); Incident Workflows | On-call and alerting category leader but weak in MELT correlation and causal RCA. Alert grouping is correlative, not causal. Not multi-vendor overlay |

### 2.2 Observability / AIOps Platforms

| Company | HQ / Stage | Killer Features | Structural Gap vs PulseGraph |
|---------|-----------|-----------------|------------------------------|
| **Dynatrace** | Waltham MA / Public (NYSE: DT) | **Smartscape**: real-time causal dependency graph across all entities (processes, hosts, services, cloud); **Davis AI**: deterministic causal RCA (not correlative) with automatic problem detection; **Grail lakehouse**: unified MELT + biz events with DQL; **OneAgent**: auto-instrumentation with zero-config topology discovery; Problems app: automated incident lifecycle; AWS CloudWatch/Firehose integration | Single-vendor lock-in is the defining weakness — Smartscape only ingests what OneAgent/OTel reports to *Dynatrace*. Customers running Datadog + Dynatrace see no cross-vendor graph. Expensive per-host unit economics |
| **Datadog** | New York / Public (NASDAQ: DDOG) | **Watchdog**: ML-based anomaly detection across all signals; **Bits AI Investigation**: conversational AI that traverses spans, logs, metrics to propose RCA hypotheses; **Service Map**: APM-derived dependency graph; **Incident Response**: integrated incident management with timeline and signals; **Impact Analysis**: blast-radius estimation from deployment events; correlated APM+Logs+RUM+Infra in one query | Best-in-class UX, strongest single-vendor MELT story. Weakness: Service Map is APM-only (not L+M); Watchdog is correlative not causal; Incident Response weak vs incident.io workflow depth; fundamentally single-vendor — enterprises running NR or DT alongside DD see blind spots |
| **New Relic** | San Francisco / Public (NYSE: NR, taken private 2024 by TPG) | **Applied Intelligence**: alert correlation, anomaly detection, incident intelligence; **NerdGraph**: GraphQL entity graph across all monitored entities; **New Relic AI**: natural-language query and investigation (powered by multiple LLMs); **NRQL**: powerful query language; all-in-one telemetry ingest (MELT + browser + mobile + synthetics); consumption pricing | Per-GB pricing resonates with CFOs but engineers find NRQL steep; entity map less visual than Dynatrace Smartscape; Applied Intelligence alert correlation is correlative, not causal DAG; single-vendor data silo same as DD |
| **Chronosphere** | New York / well-funded Series C | **Temporal Knowledge Graph**: entities, relationships, and their changes indexed over time; **Differential Diagnosis (DDx)**: surfacing which entity/metric changed *relative to a stable baseline* at incident time; high-cardinality metrics with PromQL; built for cloud-native at scale | Metrics-centric (Prometheus lineage); logs/traces secondary. DDx is change-detection, not full causal graph. No multi-vendor telemetry federation. Strong Kubernetes / large-scale focus makes it less relevant for traditional/mixed estates |
| **Honeycomb** | San Francisco / well-funded Series D | **BubbleUp**: statistical anomaly isolation across high-cardinality events; **Canvas**: shareable investigation notebooks; structured events model (traces-as-events); fast query over arbitrarily high-cardinality columns; team-oriented investigation sharing | Trace/event-centric; limited native metrics and logs story. No topology/dependency graph. BubbleUp is statistical, not causal. Single-vendor and requires adoption of Honeycomb's ingestion model. Weaker incident lifecycle features |
| **AWS native stack** | — / Hyperscaler | **CloudWatch**: metrics, logs, alarms at AWS-infra scale; **X-Ray / Application Signals**: service map, SLOs, anomaly detection for AWS workloads; **DevOps Guru**: ML-based operational anomaly detection; commonly deployed *alongside* Dynatrace or Datadog (not instead of) | AWS-only by design. X-Ray service maps are AWS-resource-scoped. No cross-cloud or on-prem telemetry. DevOps Guru anomaly detection is reactive not causal. Customers running AWS + non-AWS workloads need an overlay |

---

## 3. Best-of-the-Best Feature Catalog

> Features are identified from the **best-in-class implementation** observed across all covered competitors, not from average implementations. These represent the capability bar PulseGraph should aspire to or deliberately exceed.

### Group A — Context / Entity / Dependency Graph (Context Graph)

This is the most structurally critical group. An accurate, live, multi-vendor Context Graph is what separates a telemetry silo from an observability platform.

| ID | Feature | Best-in-class reference | Description |
|----|---------|------------------------|-------------|
| **BF-001** | Real-time causal dependency graph | Dynatrace Smartscape | Continuously maintained DAG of all entities (services, processes, hosts, containers, cloud resources) and their call relationships. Edges carry call rate, error rate, p99 latency in real time. Graph updates within seconds of topology change — not periodically. Causal traversal answers: "if this service degrades, what else breaks?" |
| **BF-002** | Entity relationship model with full context | New Relic NerdGraph, Dynatrace entity model | Typed entity catalog (Service, Host, Container, Database, Queue, etc.) with standardized attributes. Cross-signal data (M+E+L+T) attached to each entity node. Queryable via graph traversal, not just flat search |
| **BF-003** | Blast-radius estimation from graph | Datadog Impact Analysis, Dynatrace Davis | Given a failing entity, traverse the Context Graph to enumerate downstream dependents with estimated traffic/revenue impact. Shown at incident declaration time, not discovered during retrospective |
| **BF-004** | Historical topology diff and change detection | Chronosphere Temporal Knowledge Graph | Store topology snapshots over time. Answer: "what changed in the service graph between T-1h and now?" Correlate topology change events (new service, changed edge weight, disappeared dependency) with anomaly onset |
| **BF-005** | Scope-aware subgraph extraction | Dynatrace Smartscape scoping | Given an incident entity, extract the minimal subgraph relevant to that incident (N hops upstream + downstream). Suppress global graph noise during investigation |

### Group B — MELT Correlation & Unified Query

| ID | Feature | Best-in-class reference | Description |
|----|---------|------------------------|-------------|
| **BF-006** | Cross-signal correlation on shared keys | Datadog (trace_id in logs+metrics+APM), Dynatrace Grail | A single trace ID, span ID, or service name joins metrics, logs, and traces in one query surface. No manual correlation — the join is structural, not textual |
| **BF-007** | Multi-vendor MELT normalization | (PulseGraph's target; closest: OTel Collector processors) | Ingest DD agent format, NR Events API, DT problems API, and OTLP simultaneously. Map vendor-specific field names to a canonical schema. Expose a unified query surface regardless of which vendor emitted the signal |
| **BF-008** | High-cardinality columnar query engine | Honeycomb, Chronosphere | Query over arbitrary tag combinations (e.g., `user_id`, `feature_flag`, `region`) at millisecond latency without pre-aggregation. Column-store semantics over structured events |
| **BF-009** | Temporal Knowledge Graph query | Chronosphere Differential Diagnosis (DDx) | Query not just "what are the current values" but "what changed relative to a stable baseline window." DDx surfaces the metrics, entities, and edges that deviated from their historical distribution around incident onset |
| **BF-010** | Unified retention with tiered storage | Datadog Flex Logs, Dynatrace Grail, Chronosphere | Hot/warm/cold tiering across MELT signals. Hot path for recent data (sub-second query), warm for incident snapshots, cold for compliance archival. Policy-driven per signal type and tenant |

### Group C — Anomaly / Causal AI / RCA

| ID | Feature | Best-in-class reference | Description |
|----|---------|------------------------|-------------|
| **BF-011** | Deterministic causal RCA | Dynatrace Davis AI | Construct a causal chain (not correlation): "root cause is service A → propagated to service B via synchronous call → triggered alert on service C." Distinguish root cause from symptom entities. Davis uses a proprietary causal engine over Smartscape edges; not purely ML/statistical |
| **BF-012** | Statistical anomaly detection on metrics | Datadog Watchdog, AWS DevOps Guru | Seasonality-aware anomaly detection (hourly, daily, weekly baselines). Low false-positive rate. Applied at fleet scale (tens of thousands of metrics streams) without manual threshold configuration |
| **BF-013** | Causal graph / DAG inference from trace topology | Dynatrace (topology + spans), PulseGraph (planned) | Derive causal relationships from observed call relationships in distributed traces. Distinguish correlation (two metrics moved together) from causation (service A called service B and service B latency drove service A latency) |
| **BF-014** | Contributory factor ranking with confidence | Dynatrace Davis, Datadog Bits AI | Produce an ordered list of contributing factors for an incident, each with a confidence score and supporting evidence (specific metric, log line, trace). Not a single point answer — a ranked differential |
| **BF-015** | ML-based outlier detection (Isolation Forest / OCSVM) | Datadog NPM anomalies, AWS DevOps Guru | Unsupervised detection of anomalous behavior at the service level using model-based approaches. Complements rule-based alerting with discovery of unknown-unknown failure modes |
| **BF-016** | LLM-narrated incident summary and RCA | Datadog Bits AI, incident.io AI SRE agent, Rootly AI retros | Synthesize structured telemetry (metrics, log excerpts, deployment events) into a human-readable incident narrative. Produce draft postmortems. Distinguish LLM as narrator (synthesizer of evidence) from LLM as analyst (source of causal logic) — the former is safer |

### Group D — Incident Lifecycle & Collaboration

| ID | Feature | Best-in-class reference | Description |
|----|---------|------------------------|-------------|
| **BF-017** | Structured incident declaration and severity taxonomy | incident.io, Rootly, PagerDuty | Formal incident record with severity (SEV-1…SEV-4), impacted services, commander, status, timeline. Not a Slack thread — a structured object with an audit trail. Feeds postmortems and SLO accounting |
| **BF-018** | Slack-native incident room with bot | incident.io, Rootly, FireHydrant | Dedicated incident Slack channel with bot commands: `/incident update`, `/incident resolve`, automated timeline pinning, stakeholder status-page sync. Reduces the operational overhead of incident comms |
| **BF-019** | On-call scheduling and escalation | PagerDuty, incident.io, Rootly | Rotation management, override calendar, escalation policy (page → escalate → page manager). SLA on response time. Integrates with mobile push and phone/SMS |
| **BF-020** | Automated postmortem generation | incident.io, Rootly | Structured retrospective populated from incident timeline, contributing signals, and on-call notes. Follows a defined template (5-whys, contributing factors, action items). Draft generated by AI, edited by humans |
| **BF-021** | Status page with subscriber management | incident.io, Rootly, Datadog | Public and internal status pages. Manual or automated incident publication. Component health tracking. Email/RSS/webhook subscriber notification |

### Group E — Snapshot / Time-Travel / Change Correlation

| ID | Feature | Best-in-class reference | Description |
|----|---------|------------------------|-------------|
| **BF-022** | Incident-triggered telemetry freeze (snapshot) | PulseGraph (designed); closest: Rootly DD screenshot, Datadog Watchdog state | At incident T=0, atomically capture: metric time-series (±30 min), relevant log streams, contributing trace exemplars, topology subgraph. Store immutably. The snapshot answers "what did the system look like at the exact moment of failure" without relying on mutable time-series retention |
| **BF-023** | WORM / object-lock snapshot storage | (Compliance-oriented; PulseGraph MinIO design) | Snapshots are write-once, read-many, with configurable retention lock. Supports SOX/HIPAA audit requirements. Prevents retroactive tampering with incident evidence |
| **BF-024** | Deployment change correlation | Datadog Impact Analysis, Dynatrace Davis deployment events | Correlate metric/error anomalies with recent deployment events (by service, version, cluster). Surface "service X deployed version 1.2.3 at T-12min; error rate increased at T-7min" as first-class evidence in RCA |
| **BF-025** | Comparative baseline (before/after) | Chronosphere DDx, Honeycomb BubbleUp | Compare metric distributions between a stable window (pre-event) and the incident window. Statistical significance testing to surface signals that genuinely shifted, not just noise |
| **BF-026** | Time-travel query on historical topology | Chronosphere Temporal Knowledge Graph | Reconstruct the service graph as it existed at any past timestamp. Answer: "what was calling this service at 14:32 last Tuesday?" Critical for post-incident investigation where current topology has already changed |

### Group F — Alert Noise Reduction & Correlation

| ID | Feature | Best-in-class reference | Description |
|----|---------|------------------------|-------------|
| **BF-027** | Alert grouping and deduplication | PagerDuty Event Intelligence, Datadog Watchdog | Cluster hundreds of related alerts into a single incident. Grouping based on service topology, shared root cause signals, or temporal proximity. Reduces alert storm from 500 pages to 1 actionable incident |
| **BF-028** | Dynamic and adaptive thresholds | Datadog Watchdog, New Relic Applied Intelligence | Automatically adjust alert thresholds to seasonal patterns without manual recalibration. Suppress expected load spikes (Black Friday traffic) from generating false-positive alerts |
| **BF-029** | Topology-aware alert suppression | Dynatrace Davis (problem grouping) | When a root cause entity is identified, suppress downstream alerts that are causal consequences, not independent failures. Prevents on-call overload from a single failure cascading into 40 alerts |
| **BF-030** | Alert dependency / maintenance windows | PagerDuty, Datadog | Scheduled suppression of alerts during planned maintenance. Dependency-aware: suppress child alerts when parent is in maintenance. Prevents false-positive noise during deployments |

### Group G — SLO / Error Budget / Burn Rate

| ID | Feature | Best-in-class reference | Description |
|----|---------|------------------------|-------------|
| **BF-031** | SLO definition and tracking (request/window) | Datadog SLOs, New Relic Service Levels, AWS Application Signals | Define SLOs over request-success ratio or time-window availability. Track error budget consumption in real time. Expose to teams as a shared contract between SRE and product |
| **BF-032** | Multi-window error budget burn-rate alerting | Google SRE book pattern; Datadog, New Relic | Alert when burn rate exceeds sustainable thresholds across multiple time windows simultaneously (e.g., 1h burn rate > 14x AND 6h burn rate > 6x triggers SEV-2). Prevents both false positives (short spikes) and slow burns (missed by short windows) |
| **BF-033** | Error budget policy enforcement | Datadog SLO-based alerting, New Relic | Automated escalation when error budget crosses defined thresholds (50%, 75%, 90%). Link SLO breaches to incident declaration. Feed SLO data into postmortems |
| **BF-034** | SLO impact estimation for incidents | (PulseGraph opportunity; not well-solved by current tools) | At incident declaration, estimate error budget burn over the incident duration at current error rate. Estimate: "at current rate, SLO will breach in 47 minutes." Drives severity escalation |

### Group H — Autoremediation / Runbooks / Workflows

| ID | Feature | Best-in-class reference | Description |
|----|---------|------------------------|-------------|
| **BF-035** | Runbook automation engine | Rootly, FireHydrant, PagerDuty Automation Actions | Codified remediation steps triggered by incident type or alert signature. Supports webhook, script, and human-approval steps. Audit log of every runbook execution |
| **BF-036** | AI-suggested remediation actions | Datadog Bits AI, Dynatrace Davis (auto-remediation in DPS) | Given a causal chain, propose ranked remediation actions with supporting rationale. Human-in-the-loop: suggest, not auto-execute, unless explicitly configured |
| **BF-037** | Change impact assessment pre-deploy | Datadog Impact Analysis (post-deploy), Dynatrace | Predict blast radius of a proposed change based on service graph. Not widely solved pre-deploy; mostly reactive. PulseGraph's overlay position makes this a unique opportunity |
| **BF-038** | Workflow builder with approval gates | Rootly, incident.io | Visual or YAML workflow definition for incident response steps. Each step can be manual (human approval) or automated. Maintains complete execution audit trail |

### Group I — AI SRE Agent / ChatOps

| ID | Feature | Best-in-class reference | Description |
|----|---------|------------------------|-------------|
| **BF-039** | Conversational AI investigation assistant | Datadog Bits AI, incident.io AI SRE agent | Natural language: "why is the checkout service slow?" → AI traverses spans, metrics, logs, recent deployments, and returns structured evidence with citations. Not a chatbot — a reasoning agent with tool use over live telemetry |
| **BF-040** | Natural-language query interface | New Relic AI (NL→NRQL), Datadog NL queries | Translate plain English questions into the platform's native query language. Lower barrier for developers who are not fluent in PromQL/NRQL/DQL |
| **BF-041** | Automated on-call handoff briefing | incident.io, Rootly | At shift change or responder swap, generate a structured briefing: current incident status, signals investigated, hypotheses explored, open questions. Reduces MTTD after handoff |
| **BF-042** | Incident timeline reconstruction | Datadog Incident timeline, incident.io | Chronological view of: alert firings, deployment events, topology changes, responder actions, Slack messages, status updates — all on one timeline. Evidence for postmortems; reduces reliance on memory |

### Group J — Multi-Vendor / OpenTelemetry Federation

| ID | Feature | Best-in-class reference | Description |
|----|---------|------------------------|-------------|
| **BF-043** | OTel-native ingest (OTLP/HTTP + gRPC) | All modern observability platforms | OTLP/HTTP and OTLP/gRPC receivers for traces, metrics, and logs. Respect OTel semantic conventions. Support W3C TraceContext propagation |
| **BF-044** | Vendor-specific adapter ingest | (PulseGraph adapter-* design) | Receive signals in each vendor's native format (Datadog agent intake API, New Relic Events API, Dynatrace Problems API) without requiring customers to change their agent configuration |
| **BF-045** | Cross-vendor trace correlation | (Unique to overlay position; no current product does this well) | Stitch trace spans emitted by different vendor agents (e.g., Datadog APM for service A, New Relic APM for service B) into a single distributed trace using W3C TraceContext or B3 headers. Single pane of glass for cross-boundary traces |
| **BF-046** | Schema normalization and canonical mapping | OTel semantic conventions + PulseGraph MELT envelope | Map vendor-specific field names to canonical OTel semantic convention keys. `dd.service` → `service.name`, `dd.env` → `deployment.environment`. Downstream consumers see one schema regardless of source vendor |
| **BF-047** | Collector-level data pipeline control | OTel Collector, Datadog agent pipelines | Filter, sample, transform, and route signals at the collector tier. Reduce egress costs by dropping high-cardinality low-value data before it reaches the backend. Tail-based sampling |

### Group K — Security / RBAC / Multi-Tenant / Compliance

| ID | Feature | Best-in-class reference | Description |
|----|---------|------------------------|-------------|
| **BF-048** | RBAC with fine-grained resource policies | Datadog RBAC, New Relic user management, Dynatrace | Role definitions with permissions scoped to teams, environments, or services. Least-privilege: an SRE for team A cannot read team B's incident data. Audit log of all permission grants |
| **BF-049** | Multi-tenant data isolation | All enterprise observability platforms | Hard namespace isolation between tenants. Cryptographic key separation for snapshot storage. No cross-tenant data leakage at query, Kafka consumer, or storage layer |
| **BF-050** | SOC 2 Type II / HIPAA / GDPR compliance posture | Datadog, Dynatrace, New Relic (all certified) | Data residency controls, encryption at rest and in transit, audit logging, DPA agreements, data retention policies. Table stakes for enterprise sales cycles |
| **BF-051** | API key management and scoped tokens | Datadog API/app keys, New Relic user keys | Programmatic access with per-key permission scopes. Key rotation without service disruption. Secret scanning integration (prevent accidental key exposure in repos) |
| **BF-052** | Sensitive data scrubbing / redaction | Datadog Sensitive Data Scanner | Detect and redact PII (email, SSN, credit card) from log payloads and trace attributes before storage. Configurable via regex or ML classifier. Required for HIPAA workloads |

---

## 4. Gap Analysis

> **Legend:**
> - ✅ **Implemented** — functional code present, tested, ships in MVP-A
> - 🔶 **Scaffolded** — service/endpoint exists but returns stub data or no-ops
> - ❌ **Missing** — not in codebase; design/docs only or not mentioned

| ID | Feature | PulseGraph Status | Notes |
|----|---------|-------------------|-------|
| **BF-001** | Real-time causal dependency graph | 🔶 Scaffolded | `packages/contextgraph` + `topology-builder` consume `norm.melt`, maintain live adjacency, HTTP subgraph APIs; OpenSearch/Postgres persistence still missing |
| **BF-002** | Entity relationship model | ❌ Missing | `melt.Envelope` has `service_name` and `labels` but no typed entity catalog or graph node model |
| **BF-003** | Blast-radius estimation from graph | 🔶 Scaffolded | `GET /v1/graph/blast-radius` implemented on live in-memory graph; no traffic/revenue impact weighting yet |
| **BF-004** | Historical topology diff | ❌ Missing | Requires Context Graph + time-indexed storage |
| **BF-005** | Scope-aware subgraph extraction | 🔶 Scaffolded | `GET /v1/graph/subgraph` depth-limited BFS works; not yet wired into snapshotter/RCA |
| **BF-006** | Cross-signal correlation on shared keys | 🔶 Scaffolded | `melt.Envelope` carries `trace_id`/`span_id`; normalizer labels signals. No join query surface yet — `query-api` returns empty arrays |
| **BF-007** | Multi-vendor MELT normalization | 🔶 Scaffolded | OTel ingest path works end-to-end. `adapter-datadog`, `adapter-newrelic`, `adapter-dynatrace` are HTTP shells that return 202 but do not parse or publish to Kafka |
| **BF-008** | High-cardinality columnar query | ❌ Missing | ClickHouse is running in compose but no normalizer writes to it; no query API |
| **BF-009** | Temporal Knowledge Graph / DDx | ❌ Missing | Not designed yet |
| **BF-010** | Unified retention with tiered storage | ❌ Missing | MinIO `snapshots` bucket exists; no writes. ClickHouse/OpenSearch schemas not created |
| **BF-011** | Deterministic causal RCA | ❌ Missing | `rca-engine` stub returns `root_cause: "unknown"`, `confidence: 0.0` |
| **BF-012** | Statistical anomaly detection | ❌ Missing | No anomaly detection pipeline |
| **BF-013** | Causal DAG inference from traces | ❌ Missing | Prerequisite for meaningful RCA; requires Context Graph |
| **BF-014** | Contributory factor ranking | ❌ Missing | `rca-engine` designed to produce ranked factors (Borda-count fusion per docs); not implemented |
| **BF-015** | ML-based outlier detection | ❌ Missing | pyproject.toml declares scikit-learn but it is unused |
| **BF-016** | LLM-narrated incident summary | ❌ Missing | No LLM integration |
| **BF-017** | Structured incident declaration | ❌ Missing | `correlator` (incident creator) is a stub |
| **BF-018** | Slack-native incident room | ❌ Missing | No ChatOps integration |
| **BF-019** | On-call scheduling and escalation | ❌ Missing | Out of initial scope per HLD |
| **BF-020** | Automated postmortem generation | ❌ Missing | Not designed |
| **BF-021** | Status page | ❌ Missing | Not in scope for MVP |
| **BF-022** | Incident-triggered telemetry freeze | ❌ Missing | `snapshotter` is a startup stub. MinIO bucket exists. ClickHouse/OpenSearch writes not wired. Postgres schema not migrated |
| **BF-023** | WORM / object-lock snapshot storage | ❌ Missing | MinIO object-lock not configured in bootstrap |
| **BF-024** | Deployment change correlation | ❌ Missing | No deployment event ingestion |
| **BF-025** | Comparative baseline (before/after) | ❌ Missing | Requires storage writes as prerequisite |
| **BF-026** | Time-travel query on historical topology | ❌ Missing | Requires BF-001 + BF-004 |
| **BF-027** | Alert grouping and deduplication | ❌ Missing | `correlator` is a stub |
| **BF-028** | Dynamic and adaptive thresholds | ❌ Missing | No alerting pipeline |
| **BF-029** | Topology-aware alert suppression | ❌ Missing | Requires Context Graph + correlator |
| **BF-030** | Maintenance windows | ❌ Missing | Not designed |
| **BF-031** | SLO definition and tracking | ❌ Missing | Not in MVP scope |
| **BF-032** | Multi-window burn-rate alerting | ❌ Missing | Not in MVP scope |
| **BF-033** | Error budget policy enforcement | ❌ Missing | Not in MVP scope |
| **BF-034** | SLO impact estimation for incidents | ❌ Missing | Not in MVP scope; strong differentiator opportunity |
| **BF-035** | Runbook automation engine | ❌ Missing | Not in MVP scope |
| **BF-036** | AI-suggested remediation | ❌ Missing | Phase 3 per HLD |
| **BF-037** | Change impact assessment pre-deploy | ❌ Missing | Not designed; strong differentiator opportunity |
| **BF-038** | Workflow builder with approval gates | ❌ Missing | Not in MVP scope |
| **BF-039** | Conversational AI investigation assistant | ❌ Missing | Phase 3 per HLD |
| **BF-040** | Natural-language query interface | ❌ Missing | Not designed |
| **BF-041** | Automated on-call handoff briefing | ❌ Missing | Not designed |
| **BF-042** | Incident timeline reconstruction | ❌ Missing | Requires incident records + event store |
| **BF-043** | OTel-native ingest (OTLP) | ✅ Implemented | `adapter-otel` → `ingest-gateway` → `raw.melt` → `normalizer` → `norm.melt`. End-to-end tested via `make smoke-ingest` |
| **BF-044** | Vendor-specific adapter ingest | 🔶 Scaffolded | HTTP shells for Datadog (:8200), New Relic (:8201), Dynatrace (:8202). Accept POST, log, return 202; do not parse or forward to Kafka |
| **BF-045** | Cross-vendor trace correlation | ❌ Missing | Requires vendor adapters to be functional + correlator |
| **BF-046** | Schema normalization and canonical mapping | 🔶 Scaffolded | Normalizer adds `pulsegraph.*` labels and sets `Normalized=true`. Does not apply full OTel semantic convention mapping. `otel-normalize` package is README-only |
| **BF-047** | Collector-level pipeline control | ❌ Missing | No sampling, filtering, or routing logic |
| **BF-048** | RBAC with fine-grained resource policies | ❌ Missing | No `api-gateway` service in repo; no auth layer |
| **BF-049** | Multi-tenant data isolation | 🔶 Scaffolded | `melt.Envelope` has `tenant_id` field (defaults to `"default"`). No tenant-isolated Kafka topics, ClickHouse partitions, or OpenSearch index per tenant |
| **BF-050** | SOC 2 / HIPAA / GDPR compliance | ❌ Missing | Not relevant until production data is handled; plan for Phase 3/4 |
| **BF-051** | API key management | ❌ Missing | No auth; `api-gateway` is absent |
| **BF-052** | Sensitive data scrubbing | ❌ Missing | Not designed |

### Gap Summary Counts

| Status | Count | % of catalog |
|--------|-------|-------------|
| ✅ Implemented | 1 | 2% |
| 🔶 Scaffolded | 8 | 15% |
| ❌ Missing | 43 | 83% |

**The gap count is not a failure — it is an accurate reflection of an early-stage overlay platform with a correct ingest foundation.** The canonical MELT envelope (BF-046 scaffolded) and OTel ingest path (BF-043 implemented) are the right primitives to build on. The sequencing risk is building correlation and RCA before the Context Graph is ready.

---

## 5. Recommended PulseGraph Differentiators

These are positions that an overlay platform can *structurally win* at — capabilities that single-vendor platforms cannot easily replicate because they would cannibalize their own agent lock-in, and that pure incident workflow tools cannot offer because they lack deep telemetry access.

### D-1: The Multi-Vendor Context Graph (Primary Moat)

**What it is:** A live topology graph of services, hosts, and resources, populated from MELT signals regardless of which vendor emitted them. A span from a service instrumented with Datadog APM and a span from a service instrumented with New Relic APM are normalized and joined into one graph.

**Why competitors cannot replicate it:**
- Dynatrace Smartscape requires OneAgent or DT-instrumented OTel.
- Datadog Service Map requires DD APM.
- New Relic NerdGraph requires NR entity data.
- None of the above see the other's data.

**Why it matters operationally:** In a multi-vendor enterprise, a cross-system incident (service A on DD APM calls service B on NR APM) produces a gap in every existing topology graph. The blast-radius analysis is wrong. PulseGraph eliminates that gap.

**PulseGraph's implementation path:** `topology-builder` consuming from `norm.melt` (which has vendor-agnostic trace_id/span_id after normalization) to build the adjacency graph. This is the most important engineering work in the backlog.

### D-2: Immutable Telemetry Snapshot as Forensic Evidence

**What it is:** At incident T=0, freeze a queryable snapshot: the exact metric time-series, log streams, trace exemplars, and topology subgraph that existed at that moment, stored with WORM semantics.

**Why competitors cannot replicate it:**
- Rootly embeds Datadog *screenshots* — rendered images, not queryable data.
- Datadog Incident Response links to live metric views — the data can change or expire.
- No platform today offers a multi-vendor, immutable, queryable telemetry freeze.

**Why it matters operationally:** Post-incident forensics are currently reconstructed from mutable time-series — the evidence changes as retention expires and the system state evolves. WORM snapshots make incident forensics reproducible and auditable. This is also the foundation for offline/async RCA.

### D-3: Causal RCA Across Vendor Boundaries

**What it is:** Causal analysis (not just correlation) that traverses a multi-vendor graph. "Service A (DD APM) degraded → it increased error rate on calls to Service B (NR APM) → Service B latency increased → downstream consumer C triggered an SLO breach."

**Why competitors cannot replicate it:**
- Dynatrace Davis is causal but single-vendor.
- Datadog Bits AI is conversational-AI-over-DD-data.
- incident.io / Rootly RCA is LLM-over-Slack — narrative, not causal.

**Why it matters operationally:** In a multi-vendor environment, every existing RCA tool produces a *partial* causal chain. The missing edges are the root cause.

### D-4: Vendor-Neutral SLO Impact Accounting

**What it is:** Track SLO burn rate using MELT signals from any vendor. At incident declaration, estimate remaining error budget and time-to-breach. This works even if SLOs are defined over multi-vendor service dependencies.

**Why it is a differentiator:** Datadog SLOs only track DD-collected signals. NR SLOs only track NR signals. No platform today tracks SLO burn rate across a mixed-vendor service graph.

### D-5: Overlay Deployment Model (Non-Disruptive Adoption)

**What it is:** PulseGraph is installed *alongside* existing agents, not instead of them. Customers do not cancel Datadog or New Relic to adopt PulseGraph. This dramatically reduces the adoption barrier and objection-handling surface.

**Why it is a differentiator:** Every other observability platform requires migration. PulseGraph's sales motion is additive, not replacement. The risk to the customer is near-zero in a PoC phase.

---

## 6. Prioritized Implementation Roadmap

> Priorities are assigned relative to the competitive differentiators above, the MVP-A baseline, and the dependency order in the system design.

### P0 — Foundation: Context Graph (6–10 weeks)

The Context Graph (BF-001 through BF-005) is the structural prerequisite for blast-radius analysis, topology-aware alert suppression, causal RCA, and cross-vendor trace correlation. Nothing in the competitive differentiator set is achievable without it. This must ship before any other P0 work.

| Work item | BF coverage | Key deliverable |
|-----------|------------|-----------------|
| `topology-builder`: consume `norm.melt`, extract parent→child span edges | BF-001 | In-memory adjacency list keyed by `service_name` |
| `topology-builder`: persist to OpenSearch index `topology-graph` (nodes + edges with timestamps) | BF-001, BF-004 | Queryable topology; topology diff by time window |
| `topology-builder`: publish `topology.changes` events | BF-001 | Downstream consumers notified of graph mutations |
| `topology-builder`: gRPC API (`GetGraph`, `GetSubgraph`, `WatchGraph`) | BF-005 | Snapshotter and console can query topology |
| `packages/otel-normalize`: implement OTel semantic convention mapping | BF-046 | Vendor-agnostic canonical keys on all signals |
| `adapter-datadog`: parse DD agent format → `melt.Envelope` → Kafka | BF-044, BF-007 | DD agents feed the shared graph without reconfiguration |
| Entity model: typed entity catalog in Postgres or OpenSearch | BF-002 | Services, hosts, containers as first-class typed nodes |
| Blast-radius API on `query-api` using graph traversal | BF-003 | "What does this incident affect?" answered at declaration time |

### P0 — Foundation: Storage Writes (4–6 weeks, parallel)

The Context Graph and RCA both require data in ClickHouse and OpenSearch. The normalizer currently publishes to `norm.melt` but writes nothing to storage.

| Work item | BF coverage | Key deliverable |
|-----------|------------|-----------------|
| ClickHouse schema: `melt_metrics` table (tenant, service, signal_type, timestamp, labels, value) | BF-008, BF-010 | Metrics queryable by `query-api` |
| OpenSearch indexing: `melt-logs-*` index template; normalizer writes log envelopes | BF-008 | Logs searchable |
| `api-gateway`: JWT auth, RBAC enforcement, rate limiting | BF-048, BF-051 | Required before any external-facing endpoint |
| Postgres migrations: incidents, snapshots, tenants tables | BF-017, BF-022 | Incident and snapshot lifecycle records |

### P1 — Incident Intelligence (8–12 weeks)

Builds on the Context Graph and storage writes to deliver the first end-to-end incident workflow.

| Work item | BF coverage | Key deliverable |
|-----------|------------|-----------------|
| `correlator`: sliding-window cross-signal anomaly correlation; incident record creation | BF-006, BF-027 | Incidents declared from telemetry, not manual Slack messages |
| `snapshotter`: incident-triggered freeze; Parquet+JSON to MinIO; WORM/object-lock | BF-022, BF-023 | Immutable telemetry evidence at T=0 |
| `snapshotter`: parallel fetch from ClickHouse + OpenSearch + topology gRPC | BF-022 | Complete MELT+topology snapshot |
| `rca-engine`: YAML rule engine (deterministic rules as first-pass RCA) | BF-011, BF-014 | Low-latency causal hypotheses without ML |
| `rca-engine`: NetworkX graph traversal over Context Graph for causal chain | BF-013, BF-014 | Causal chain, not correlation |
| SLO tracking: burn-rate calculation from ClickHouse metrics; multi-window alerts | BF-031, BF-032, BF-034 | SLO impact shown at incident declaration |
| `adapter-newrelic`, `adapter-dynatrace`: implement parsing + Kafka publish | BF-044 | Multi-vendor signals feed into graph |
| `query-api`: real data on `/incidents`, `/topology`, `/snapshots`, `/rca/{id}` | BF-006 | Console can render real data |
| Console: incident list/detail; topology graph (xyflow); RCA panel | — | First usable UI milestone |
| Cross-vendor trace stitching in `correlator` | BF-045 | Single trace view across DD+NR spans |
| Alert grouping + topology-aware suppression | BF-027, BF-029 | Reduced alert storm noise |

### P2 — AI Layer and Enterprise Readiness (12–20 weeks)

| Work item | BF coverage | Key deliverable |
|-----------|------------|-----------------|
| `rca-engine`: Isolation Forest / ML anomaly detection (scikit-learn) | BF-015 | Unknown-unknown detection |
| `rca-engine`: LLM narrator — structured evidence → human-readable summary | BF-016 | Draft postmortem and incident narrative |
| Deployment change correlation ingestion (GitHub Actions, ArgoCD webhooks) | BF-024 | Deployment events on incident timeline |
| Conversational AI investigation assistant (Bits AI equivalent, multi-vendor) | BF-039 | "Why is checkout slow?" over multi-vendor telemetry |
| Multi-tenant data isolation (per-tenant Kafka namespaces, CH partitions, OS indices) | BF-049 | Enterprise multi-tenant security posture |
| Sensitive data scrubbing pipeline (normalizer layer) | BF-052 | HIPAA-eligible data handling |
| Runbook automation engine (webhook + human-approval steps) | BF-035, BF-038 | Codified incident response workflows |
| SOC 2 Type II audit preparation | BF-050 | Enterprise sales enablement |
| Tail-based sampling and collector-level pipeline control | BF-047 | Cost control at scale |
| Time-travel query API on historical topology | BF-026 | "What was the graph at T=14:32?" |

---

## Appendix: Competitor Feature Quick-Reference

| Feature | Dynatrace | Datadog | New Relic | Chronosphere | Honeycomb | incident.io | Rootly | AWS native |
|---------|-----------|---------|-----------|--------------|-----------|-------------|--------|------------|
| Live dependency graph | ✅ Smartscape | ✅ Service Map (APM only) | ✅ NerdGraph | ✅ Temporal KG | ❌ | ❌ | ❌ | ✅ X-Ray (AWS only) |
| Causal (not correlative) RCA | ✅ Davis | ⚠️ Bits AI (LLM-based) | ⚠️ Applied Intelligence | ✅ DDx | ⚠️ BubbleUp (statistical) | ⚠️ LLM narrative | ⚠️ LLM narrative | ⚠️ DevOps Guru |
| Multi-vendor MELT federation | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Immutable snapshot freeze | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ⚠️ Screenshot only | ❌ |
| Slack-native incident management | ❌ | ⚠️ | ❌ | ❌ | ❌ | ✅ | ✅ | ❌ |
| AI SRE agent | ✅ (DPS) | ✅ Bits AI | ✅ NR AI | ❌ | ❌ | ✅ | ✅ | ❌ |
| SLO / burn-rate | ✅ | ✅ | ✅ | ✅ | ⚠️ | ❌ | ❌ | ✅ App Signals |
| OTel native | ✅ | ✅ | ✅ | ✅ | ✅ | N/A | N/A | ✅ |
| Runbook automation | ✅ (DPS) | ⚠️ | ⚠️ | ❌ | ❌ | ✅ | ✅ | ❌ |
| On-call scheduling | ⚠️ | ⚠️ | ❌ | ❌ | ❌ | ✅ | ✅ | ❌ |

> ✅ = strong implementation; ⚠️ = partial or limited; ❌ = not available

---

*Document maintained by the PulseGraph product and engineering team. Update the Gap Analysis table as BF features move from Missing → Scaffolded → Implemented.*
