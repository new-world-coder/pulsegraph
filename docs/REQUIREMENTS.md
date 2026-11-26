# PulseGraph — High-Level Requirements Document

**Document ID:** PG-REQ-001  
**Version:** 1.0 (Approved)  
**Date:** 2026-09-17  
**Status:** Approved  
**Authors:** Platform Engineering / SRE Leadership  
**Approved defaults:** PulseGraph name/path; Go ingest/correlation + TypeScript/React console + Python RCA/ML; event-driven Ingest → Normalize → Correlate → Snapshot → RCA → API/UI; OTel canonical model with vendor adapters; Kafka-compatible streaming (Redpanda locally); ClickHouse + OpenSearch + MinIO; triggers = vendor webhooks + SLO burn-rate + anomaly + manual freeze; docker-compose local deploy.

---

## Table of Contents

1. [Executive Summary & Problem Statement](#1-executive-summary--problem-statement)
2. [Goals and Non-Goals](#2-goals-and-non-goals)
3. [Personas](#3-personas)
4. [Glossary](#4-glossary)
5. [Functional Requirements (MoSCoW)](#5-functional-requirements-moscow)
   - 5.1 Multi-Vendor Adapters
   - 5.2 Real-Time MELT Ingest & Normalization
   - 5.3 Cross-Signal Correlation
   - 5.4 Incident Detection Triggers
   - 5.5 Incident Snapshot / Freeze Window
   - 5.6 RCA Engine
   - 5.7 Service Topology & Dependency Graph
   - 5.8 Alerting & Notification Integrations
   - 5.9 Console UX
   - 5.10 Audit, RBAC & Multi-Tenancy
6. [Non-Functional Requirements](#6-non-functional-requirements)
7. [Success Metrics](#7-success-metrics)
8. [Constraints & Assumptions](#8-constraints--assumptions)
9. [Out of Scope for v1](#9-out-of-scope-for-v1)
10. [Resolved Decisions](#10-resolved-decisions-formerly-open-questions)

---

## 1. Executive Summary & Problem Statement

### 1.1 Executive Summary

PulseGraph is a **platform-agnostic observability overlay** that unifies MELT signals (Metrics, Events, Logs, Traces) sourced from heterogeneous vendor tooling — primarily Datadog, New Relic, Dynatrace, and OpenTelemetry-native pipelines — into a single correlated, queryable, and incident-aware data plane. It provides real-time signal correlation, automated incident snapshotting, and machine-assisted root cause analysis (RCA), enabling on-call engineers and incident commanders to reduce mean time to detection (MTTD) and mean time to recovery (MTTR) at large-scale, high-cardinality environments.

### 1.2 Problem Statement

Large engineering organizations operating at scale routinely face the following compounding dysfunctions:

**Tool Sprawl & Context Switching**  
Teams use two or more observability vendors simultaneously — e.g., Datadog for APM and infrastructure metrics, New Relic for browser/synthetics, and Dynatrace for deep JVM diagnostics. During an incident, an on-call engineer must pivot across three separate UIs, three separate query languages (DQL, NRQL, PromQL-variants), and three separate alert histories to construct a coherent timeline. This context switching adds 10–30 minutes to every non-trivial incident.

**No Durable Incident Context**  
Once an incident is resolved, the live state of dashboards, traces, and logs at the time of the outage drifts or expires (Datadog default log retention: 15 days; New Relic free tier: 8 days). Post-incident review teams lose access to the exact correlated state that existed at incident time, making blameless postmortems reliant on screenshots, chat history, and memory.

**Manual Root Cause Analysis**  
RCA today is artisanal: an SRE digs through dashboards, runs ad-hoc queries, and forms hypotheses manually. There is no systematic, evidence-linked hypothesis ranking that draws on all available signals simultaneously. This increases MTTR, creates fatigue, and introduces analyst bias.

**No Cross-Vendor Correlation**  
A distributed trace may start in New Relic (front-end), traverse a Datadog-instrumented API gateway, and terminate in a Dynatrace-monitored JVM service. No vendor natively correlates spans, logs, and metrics across this boundary. Engineers manually stitch together context using timestamps and service names — a brittle, error-prone process.

**High Alert Noise, Low Signal Fidelity**  
SLO burn-rate alerts from multiple vendors fire independently, often for the same underlying fault, creating alert storms that obscure the causal chain and overwhelm on-call rotation queues.

PulseGraph addresses all of these failure modes by acting as a read-side correlation and analysis layer that does not require teams to abandon their existing vendor investments.

---

## 2. Goals and Non-Goals

### 2.1 Goals

| # | Goal |
|---|------|
| G-01 | Provide a unified, vendor-neutral view of MELT signals across Datadog, New Relic, Dynatrace, and OTel-native sources. |
| G-02 | Automatically correlate metrics, logs, and traces by service, time window, and correlation ID to surface causality chains. |
| G-03 | Capture immutable incident snapshots ("freeze windows") containing all correlated MELT signals at the time of an incident. |
| G-04 | Produce automated, ranked RCA hypotheses with supporting evidence references within minutes of incident detection. |
| G-05 | Reduce MTTR by at least 40% compared to baseline for teams onboarded to PulseGraph. |
| G-06 | Enable multi-tenant operation so multiple product teams / business units can share infrastructure while maintaining signal isolation. |
| G-07 | Be observable itself — PulseGraph must emit its own MELT signals consumable by the same pipeline. |
| G-08 | Support local development and full-stack testing via docker-compose without requiring cloud vendor credentials. |

### 2.2 Non-Goals

| # | Non-Goal |
|---|----------|
| NG-01 | PulseGraph is **not** an observability vendor replacement. It does not aim to ingest raw infrastructure metrics at the agent level or compete with vendor instrumentation SDKs. |
| NG-02 | PulseGraph does **not** provide SLA/SLO management configuration (defining SLOs remains in the source vendors). It consumes burn-rate events from vendors. |
| NG-03 | PulseGraph does **not** provide on-call scheduling or escalation policy management (delegated to PagerDuty / OpsGenie). |
| NG-04 | PulseGraph does **not** perform application code profiling or continuous profiling (e.g., Pyroscope/Parca integration is a future concern). |
| NG-05 | PulseGraph will **not** attempt to write back configuration or silence alerts into source vendor platforms in v1. |

---

## 3. Personas

### P-01: SRE On-Call Engineer

**Role:** Tier-1 / Tier-2 responder paged during an incident, typically one of 5–15 engineers in a rotation across multiple services.

**Context:** Receives a PagerDuty alert at 2 AM. Has 15–30 seconds to orient before beginning remediation. May be unfamiliar with the specific service that fired.

**Goals:**
- Rapidly understand what broke, where it broke, and when it started.
- See correlated logs and traces alongside the anomalous metric spike without switching browser tabs.
- Get a ranked list of probable root causes with evidence, so they can confirm or reject without running 10 ad-hoc queries.
- Access an immutable snapshot of the incident state even after the live signals have aged out.

**Pain points:** Alert storms with duplicate notifications, context switching between vendor UIs, stale or missing data during post-incident review.

---

### P-02: Platform Engineer

**Role:** Owns the observability stack, vendor integrations, and internal tooling. Not typically on the incident critical path but defines the tooling that SREs use.

**Context:** Manages PulseGraph adapter configuration, onboards new services, tunes correlation rules, and maintains health of the PulseGraph pipeline itself.

**Goals:**
- Add a new vendor adapter or service with minimal friction.
- Monitor PulseGraph's own ingest lag, correlation accuracy, and snapshot durability.
- Define and tune signal normalization rules and correlation heuristics.
- Enforce RBAC and tenant isolation policies.

**Pain points:** Adapter configuration drift, high cardinality causing pipeline back-pressure, debugging which vendor source introduced a malformed signal.

---

### P-03: Incident Commander

**Role:** Senior engineer or engineering manager who coordinates response during a P0/P1 incident, typically managing communication across engineering, product, and executive stakeholders.

**Context:** May not be deeply technical in the affected service. Needs a high-level, real-time picture of blast radius — which services are affected, what percentage of traffic is impacted, and what the remediation timeline looks like.

**Goals:**
- See a live topology view showing affected services and their dependency relationships.
- Access a shareable, read-only incident timeline that can be sent to stakeholders.
- Understand when a freeze window was captured and what it contains.
- Know the current top RCA hypothesis without reading raw logs.

**Pain points:** Lack of a single "source of truth" during incidents, having to synthesize status from multiple Slack threads, manually compiling incident summaries.

---

### P-04: Engineering Manager / Director

**Role:** Responsible for team reliability targets (error budget, MTTR SLOs), postmortem quality, and long-term reliability investment decisions.

**Context:** Reviews incidents after the fact. Needs trend data, comparison across teams, and evidence that the RCA was thorough.

**Goals:**
- Track MTTR and MTTD trends over time across teams and services.
- Review incident snapshots for postmortem preparation.
- Assess false RCA rate and hypothesis quality over time.
- Export incident reports for compliance or stakeholder communication.

**Pain points:** No consistent postmortem artifact format, inability to compare incidents across vendor tools, lost context once vendor retention windows expire.

---

## 4. Glossary

| Term | Definition |
|------|------------|
| **MELT** | Metrics, Events, Logs, Traces — the four pillars of modern observability telemetry. |
| **Metric** | A numeric time-series measurement emitted by a service or infrastructure component (e.g., request latency p99, CPU utilization). |
| **Event** | A discrete, structured occurrence with a timestamp and attributes (e.g., deployment event, config change, alert state transition). |
| **Log** | A timestamped, semi-structured or unstructured record of application behavior, typically at line granularity. |
| **Trace** | A distributed, correlated chain of spans representing the lifecycle of a single request as it propagates across services. |
| **Span** | A single unit of work within a distributed trace, with a start time, duration, service name, and attributes. |
| **MTTD** | Mean Time To Detect — the average elapsed time from the onset of a fault to the moment an alert fires or an engineer is notified. |
| **MTTR** | Mean Time To Recover — the average elapsed time from detection to full service restoration. |
| **RCA** | Root Cause Analysis — the process of identifying the originating fault that caused an observable incident. |
| **Blast Radius** | The set of services, endpoints, or users materially impacted by a fault, either directly or via cascading dependency failures. |
| **Burn Rate** | The rate at which an SLO error budget is being consumed, expressed as a multiple of the nominal consumption rate (e.g., 14.4x burn rate means budget exhausts in 1 hour if sustained). |
| **Frozen Snapshot** | An immutable, durably-stored capture of all correlated MELT signals (metrics windows, log excerpts, trace samples, event timeline) associated with a specific incident. Created at the time of incident detection or manually triggered. |
| **Correlation ID** | A stable identifier propagated through a request's lifecycle (e.g., `X-Trace-ID`, `X-Request-ID`, span context) that links log lines, spans, and metric samples to a common request or session. |
| **Service Map** | A directed graph of runtime service dependencies, constructed from trace data and enriched with real-time health signals. |
| **OTel / OpenTelemetry** | A CNCF open standard for instrumentation, collection, and export of MELT telemetry. PulseGraph uses the OTel data model as its canonical internal representation. |
| **Adapter** | A PulseGraph component that translates a specific vendor's data model and API protocol into the canonical OTel-aligned internal format. |
| **Correlation Engine** | The PulseGraph service responsible for linking metrics anomalies, log error bursts, and trace degradation signals that share temporal proximity and service/attribute overlap. |
| **Hypothesis** | An RCA artifact: a candidate root cause with a confidence score, supporting evidence (signal references), and a brief natural-language rationale. |
| **Ingest Lag** | The elapsed time between a signal being emitted by a source vendor and being available for query within PulseGraph. |
| **Soft Tenancy** | Multi-tenancy implemented via data-level partitioning and access control rather than fully isolated infrastructure per tenant. |
| **DRI** | Directly Responsible Individual — the single owner accountable for an incident's resolution. |
| **Error Budget** | The allowed amount of unreliability in an SLO period, calculated as `(1 - SLO target) × total requests/time`. |
| **Redpanda** | A Kafka-API-compatible streaming broker suitable for local development; used as a drop-in replacement for Apache Kafka in docker-compose environments. |
| **ClickHouse** | A columnar OLAP database used by PulseGraph for high-throughput time-series metrics and trace analytics. |
| **OpenSearch** | An open-source search and analytics engine (Elasticsearch-compatible) used by PulseGraph for full-text log storage and search. |

---

## 5. Functional Requirements (MoSCoW)

MoSCoW classification applies to **v1** (initial production-ready release).

- **Must** — Required for v1 launch; absence is a launch blocker.
- **Should** — High value for v1; will be built unless scope forces deferral.
- **Could** — Valuable but deferrable to v1.x patch or v2.
- **Won't (v1)** — Explicitly deferred; acknowledged and documented.

---

### 5.1 Multi-Vendor Adapters

| ID | Priority | Requirement |
|----|----------|-------------|
| REQ-F-001 | **Must** | The system MUST provide a Datadog adapter that consumes metrics, logs, traces, and events via Datadog webhook forwarding and the Datadog API (metrics query, log archive forwarding, APM trace export). |
| REQ-F-002 | **Must** | The system MUST provide a New Relic adapter that ingests NRDB events, metric timeslice data, distributed traces (Infinite Tracing or standard), and log forwarding via New Relic webhook/streaming export. |
| REQ-F-003 | **Must** | The system MUST provide a Dynatrace adapter that ingests Davis AI problem events, metric export v2, distributed traces (OpenTelemetry egress from Dynatrace), and log export via Dynatrace API. |
| REQ-F-004 | **Must** | The system MUST accept native OpenTelemetry signals (OTLP/gRPC and OTLP/HTTP) as a first-class ingest path requiring no adapter transformation beyond schema validation and tenant routing. |
| REQ-F-005 | **Must** | Each adapter MUST translate its vendor-native data model into the PulseGraph canonical model (OTel-aligned; see REQ-F-010) before publishing to the normalization pipeline. |
| REQ-F-006 | **Must** | Adapter configurations (API keys, endpoint URLs, tenant mappings, enabled signal types) MUST be stored as versioned, encrypted configuration and MUST NOT be committed to source control in plaintext. |
| REQ-F-007 | **Should** | Each adapter SHOULD expose a health endpoint reporting last-successful-ingest timestamp, current lag, and error rate per signal type. |
| REQ-F-008 | **Should** | Adapters SHOULD support graceful degradation: if a vendor API is rate-limited or unavailable, the adapter SHOULD buffer up to a configurable backlog (default: 5 minutes of estimated volume) and resume without data loss within that window. |
| REQ-F-009 | **Could** | A Prometheus/OpenMetrics scrape adapter COULD be provided for teams that expose `/metrics` endpoints without a managed vendor agent. |
| REQ-F-010 | **Won't (v1)** | A Splunk adapter is deferred to v2 pending prioritization. |

---

### 5.2 Real-Time MELT Ingest & Normalization

| ID | Priority | Requirement |
|----|----------|-------------|
| REQ-F-011 | **Must** | The system MUST ingest all four MELT signal types (Metrics, Events, Logs, Traces) through a shared Kafka topic architecture, with dedicated topics per signal type and per tenant partition key. |
| REQ-F-012 | **Must** | The Normalize service MUST apply a canonical schema to all ingested signals, enforcing: required fields (service name, environment, timestamp, signal type, tenant ID), attribute key normalization (snake_case), and unit standardization for metrics (SI base units). |
| REQ-F-013 | **Must** | The Normalize service MUST redact or hash PII fields (email addresses, IP addresses, credit card patterns) according to a configurable redaction policy before writing to any persistent store. |
| REQ-F-014 | **Must** | Normalized metrics MUST be written to ClickHouse with a retention policy configurable per tenant (default: 90 days hot, 1 year warm via tiered storage). |
| REQ-F-015 | **Must** | Normalized logs MUST be indexed in OpenSearch with a retention policy configurable per tenant (default: 30 days). |
| REQ-F-016 | **Must** | Normalized traces MUST be written to ClickHouse trace tables, with span-level indexing on trace ID, span ID, service name, and error status. |
| REQ-F-017 | **Must** | Events (deployments, config changes, alert state changes, on-call escalations) MUST be written to a dedicated ClickHouse events table and overlaid on metric timelines in the console. |
| REQ-F-018 | **Should** | The Normalize service SHOULD enrich signals with resource metadata (Kubernetes namespace, pod name, node, cloud region, availability zone) by joining against a live resource registry populated from vendor asset APIs. |
| REQ-F-019 | **Should** | The system SHOULD support schema evolution: adding new fields to the canonical model SHOULD not require a pipeline restart; dropping/renaming fields MUST go through a documented deprecation cycle. |
| REQ-F-020 | **Could** | The pipeline COULD support a "dry run" mode for adapter validation that processes and logs signals without writing to persistent stores. |

---

### 5.3 Cross-Signal Correlation

| ID | Priority | Requirement |
|----|----------|-------------|
| REQ-F-021 | **Must** | The Correlate service MUST link log lines to traces when both share a `trace_id` or a `correlation_id` attribute within the same service and time window. |
| REQ-F-022 | **Must** | The Correlate service MUST associate metric anomalies (p99 latency spikes, error rate step changes, saturation thresholds) with co-occurring log error bursts on the same service within a configurable temporal proximity window (default: ±2 minutes). |
| REQ-F-023 | **Must** | Correlation results MUST be persisted as `CorrelationEdge` records referencing the source signal IDs, correlation type, confidence score (0.0–1.0), and originating rule or algorithm. |
| REQ-F-024 | **Must** | The system MUST support propagation of correlation context across vendor boundaries: a trace span from Datadog APM and a log line from New Relic are linkable if they share a `trace_id` propagated via W3C TraceContext or Datadog `x-datadog-trace-id` headers. |
| REQ-F-025 | **Must** | Correlation MUST be computed in near-real-time (target: within 30 seconds of all constituent signals arriving) using stream-processing on the normalized Kafka topics. |
| REQ-F-026 | **Should** | The Correlate service SHOULD implement service-graph-aware correlation: upstream metric degradation SHOULD be linked to downstream error rate increases when a known dependency edge exists between services. |
| REQ-F-027 | **Should** | Correlation rules SHOULD be configurable via a declarative YAML/JSON rule DSL, allowing platform engineers to add custom correlation heuristics without code changes. |
| REQ-F-028 | **Should** | The system SHOULD detect and suppress duplicate correlation edges when the same causal pair is identified by multiple rules (deduplication by signal ID pair + correlation type). |
| REQ-F-029 | **Could** | The Correlate service COULD apply ML-derived baselines (trained on historical signal distributions) to improve anomaly detection sensitivity within the correlation window. |

---

### 5.4 Incident Detection Triggers

| ID | Priority | Requirement |
|----|----------|-------------|
| REQ-F-030 | **Must** | The system MUST support incident detection via inbound vendor webhooks: Datadog monitor alerts, New Relic alert notifications, and Dynatrace problem open/close events MUST each be able to trigger incident creation in PulseGraph. |
| REQ-F-031 | **Must** | The system MUST support SLO burn-rate triggers: when a consumed burn-rate event from any vendor exceeds a configurable threshold (e.g., 1-hour window burn rate > 14.4×), PulseGraph MUST create or update an incident record. |
| REQ-F-032 | **Must** | The system MUST support anomaly-based triggers: statistically significant deviations (default: 3-sigma from a 7-day rolling baseline) in any tracked metric for a service MUST be eligible to create an incident. Sensitivity MUST be configurable per service. |
| REQ-F-033 | **Must** | The system MUST support manual freeze window triggers: an authenticated user (SRE or higher) MUST be able to manually trigger a freeze window for a named service or incident at any time via the API or console. |
| REQ-F-034 | **Must** | Incident records MUST include: unique incident ID, detection trigger type and source, affected services (initially inferred, updatable), severity (P0–P4), detection timestamp, first-signal timestamp (may precede detection), status (open/acknowledged/resolved), and DRI. |
| REQ-F-035 | **Should** | The system SHOULD implement alert deduplication: multiple vendor alerts for the same underlying fault within a 5-minute window SHOULD be grouped into a single incident rather than creating separate incident records. |
| REQ-F-036 | **Should** | Incident severity SHOULD be automatically estimated based on blast radius (number of affected services, error rate magnitude, and estimated user impact) at the time of detection, with manual override allowed. |
| REQ-F-037 | **Could** | The system COULD support a "pre-incident" state: when anomaly signals are detected but have not yet crossed alert thresholds, PulseGraph COULD emit a low-severity advisory notification and begin buffering candidate signals for potential snapshot capture. |

---

### 5.5 Incident Snapshot / Freeze Window

| ID | Priority | Requirement |
|----|----------|-------------|
| REQ-F-038 | **Must** | Upon incident creation (by any trigger), the system MUST automatically capture a freeze-window snapshot: an immutable, self-contained archive of all correlated MELT signals for the affected services, covering a time window of T−30m to T+configurable post-capture (default: +15 min, continuously extended while incident is open). |
| REQ-F-039 | **Must** | Snapshot contents MUST include: metric time-series (raw samples) for all affected service metrics at the time of capture, a sample of correlated log lines (up to a configurable maximum, default 100k lines), all trace IDs and span trees for requests that spanned affected services in the capture window, and all events (deployments, config changes, alert transitions) overlapping the capture window. |
| REQ-F-040 | **Must** | Snapshots MUST be stored in an object store (S3-compatible) as a versioned, content-addressed archive. The snapshot manifest MUST include a SHA-256 checksum of each content bundle for integrity verification. |
| REQ-F-041 | **Must** | Snapshot storage MUST be independent of the live query stores (ClickHouse, OpenSearch): snapshot data MUST remain accessible even if live retention windows expire. Snapshot retention MUST be configurable per tenant (default: 2 years). |
| REQ-F-042 | **Must** | The system MUST provide a snapshot replay API: given a snapshot ID, the API MUST serve the snapshot's signals in a form indistinguishable from a live query against the same time window, enabling postmortem investigation. |
| REQ-F-043 | **Must** | Snapshots MUST be immutable after their capture window closes. No mutation or deletion of snapshot content is permitted except via explicit, audited administrative action (tenant deletion or legal hold release). |
| REQ-F-044 | **Should** | The snapshot manifest SHOULD include a human-readable summary: affected services, peak error rates, detection trigger, initial blast radius, and capture window timestamps. |
| REQ-F-045 | **Should** | The system SHOULD support snapshot export: authorized users SHOULD be able to export a snapshot as a compressed archive (`.tar.zst`) for offline postmortem tooling or compliance evidence. |
| REQ-F-046 | **Could** | Snapshots COULD be diff-compared: given two snapshot IDs (e.g., before and after a fix is deployed), the system COULD surface which metrics normalized and which anomalies persisted. |

---

### 5.6 RCA Engine

| ID | Priority | Requirement |
|----|----------|-------------|
| REQ-F-047 | **Must** | The RCA engine MUST be triggered automatically upon freeze-window capture. It MUST produce at least one ranked RCA hypothesis within 5 minutes of snapshot finalization for incidents with sufficient correlated signal. |
| REQ-F-048 | **Must** | Each hypothesis MUST include: a unique hypothesis ID, a natural-language description of the probable root cause, a confidence score (0.0–1.0), a list of supporting evidence (signal references with type, ID, and a brief excerpt), and the reasoning chain (ordered list of correlations that led to the hypothesis). |
| REQ-F-049 | **Must** | The RCA engine MUST implement at minimum the following deterministic analysis strategies: (a) causal chain traversal from the point of first anomaly up the service dependency graph; (b) change correlation — identifying deployments, config changes, or scaling events in the 2-hour window preceding the incident; (c) error pattern matching — known error signatures matched against the top error log clusters. |
| REQ-F-050 | **Must** | RCA hypotheses MUST be persisted and versioned: re-running the RCA engine on an existing snapshot MUST produce a new hypothesis version rather than overwriting the previous one. |
| REQ-F-051 | **Must** | Engineers MUST be able to accept, reject, or annotate RCA hypotheses with free-text comments. Accepted hypotheses MUST be flagged as confirmed root causes. |
| REQ-F-052 | **Should** | The RCA engine SHOULD rank multiple competing hypotheses (up to 5 per run) by confidence score and present them in descending order of likelihood. |
| REQ-F-053 | **Should** | The RCA engine SHOULD incorporate historical incident context: if a service has had similar anomaly patterns in the past 30 days, previous confirmed root causes SHOULD be surfaced as additional hypotheses with evidence citations. |
| REQ-F-054 | **Should** | Hypothesis feedback (accept/reject) SHOULD be used to tune future hypothesis ranking through a lightweight feedback loop (initial implementation: Bayesian weight adjustment per service × error pattern pair). |
| REQ-F-055 | **Could** | The RCA engine COULD offer an LLM-assisted hypothesis narrative: a concise, plain-English incident summary generated from the structured hypothesis data, intended for incident summaries and postmortem drafts. |
| REQ-F-056 | **Could** | The RCA engine COULD produce a "what would fix this" recommendation (e.g., rollback to version X, scale up service Y) derived from the confirmed root cause type and historical resolution actions. |
| REQ-F-057 | **Won't (v1)** | Fully autonomous remediation actions (e.g., auto-rollback, auto-scaling triggers) are deferred. PulseGraph v1 provides recommendations only, not execution. |

---

### 5.7 Service Topology & Dependency Graph

| ID | Priority | Requirement |
|----|----------|-------------|
| REQ-F-058 | **Must** | The system MUST construct and maintain a live service dependency graph, derived from distributed trace data (parent-child span relationships across services), updated at least every 60 seconds. |
| REQ-F-059 | **Must** | The service map MUST display real-time health state for each node: current error rate, p50/p99 latency, request throughput, and active incident indicator. |
| REQ-F-060 | **Must** | The service map MUST visually highlight blast radius during an active incident: affected nodes and their first- and second-order dependencies MUST be distinguishable from healthy nodes. |
| REQ-F-061 | **Should** | The service map SHOULD persist historical graph snapshots so the topology at the time of any incident can be reviewed (stored as part of the incident snapshot, REQ-F-039). |
| REQ-F-062 | **Should** | Dependency edges SHOULD carry call volume and error rate metadata so that the RCA engine can weight causal chains by traffic impact. |
| REQ-F-063 | **Could** | The service map COULD support manual annotations: platform engineers COULD label edges with ownership, criticality tier, and known failure modes. |

---

### 5.8 Alerting & Notification Integrations

| ID | Priority | Requirement |
|----|----------|-------------|
| REQ-F-064 | **Must** | The system MUST send PagerDuty incidents upon PulseGraph incident creation (P0/P1), with incident ID, severity, affected services, detection trigger, and a deep link to the PulseGraph incident page. |
| REQ-F-065 | **Must** | The system MUST send Slack notifications to configurable channels upon incident creation, hypothesis completion, and incident resolution. Notification payloads MUST include: incident ID, severity, affected services, top RCA hypothesis summary, and a deep link. |
| REQ-F-066 | **Must** | Notification routing rules MUST be configurable per tenant and per service: different services SHOULD be able to route to different PagerDuty service integrations and Slack channels. |
| REQ-F-067 | **Should** | The system SHOULD send an updated Slack thread reply (not a new message) when hypothesis confidence updates or new evidence is added to an open incident. |
| REQ-F-068 | **Should** | The system SHOULD support notification suppression windows: authorized users SHOULD be able to suppress non-critical notifications for a named service during planned maintenance (not the same as disabling incident detection). |
| REQ-F-069 | **Could** | Integration with Microsoft Teams COULD be added as a notification target in v1.x. |
| REQ-F-070 | **Won't (v1)** | Email-based alerting is deferred; PagerDuty and Slack are the primary v1 notification channels. |

---

### 5.9 Console UX

| ID | Priority | Requirement |
|----|----------|-------------|
| REQ-F-071 | **Must** | The console MUST provide an Incident List view: paginated, filterable (by service, severity, tenant, date range, status), sortable (by detection time, MTTR, blast radius). |
| REQ-F-072 | **Must** | The console MUST provide an Incident Detail view showing: event timeline, correlated MELT signals (tabbed: Metrics / Logs / Traces / Events), blast radius service map, RCA hypothesis panel, and snapshot status. |
| REQ-F-073 | **Must** | The console MUST provide a Snapshot Replay view: using snapshot data as the query backend, the full Incident Detail view MUST be navigable in replay mode against a closed incident. |
| REQ-F-074 | **Must** | The console MUST provide a Service Map view (as described in §5.7) with incident overlay capability. |
| REQ-F-075 | **Must** | The console MUST enforce RBAC: users without the required role for a tenant MUST NOT be able to view, query, or export any signals from that tenant. |
| REQ-F-076 | **Should** | The console SHOULD provide a unified Signal Search view: a single query interface capable of searching across metrics (time range + label filters), logs (full-text + structured field filters), traces (service + latency + error filters), and events (type + time range). |
| REQ-F-077 | **Should** | The console SHOULD be responsive and functional on a standard laptop display (1280×800 minimum) without horizontal scroll on primary views. |
| REQ-F-078 | **Should** | All console views SHOULD support deep-linkable URLs so that incident commanders can share exact state (time range, filters, selected hypothesis) with teammates via URL. |
| REQ-F-079 | **Should** | The console SHOULD support dark mode. |
| REQ-F-080 | **Could** | The console COULD support customizable dashboard pinning: users COULD pin metric charts or log queries from signal search to a personal or team dashboard. |
| REQ-F-081 | **Won't (v1)** | A native mobile application is deferred. The console MUST be a web application. |

---

### 5.10 Audit, RBAC & Multi-Tenancy

| ID | Priority | Requirement |
|----|----------|-------------|
| REQ-F-082 | **Must** | The system MUST implement role-based access control (RBAC) with at minimum the following predefined roles: `viewer` (read-only access to signals and incidents), `responder` (viewer + acknowledge/annotate incidents, trigger manual freeze), `platform-engineer` (responder + manage adapter configs, correlation rules), `tenant-admin` (platform-engineer + manage users/roles within tenant), `system-admin` (cross-tenant operations, system configuration). |
| REQ-F-083 | **Must** | Tenant isolation MUST be enforced at the data layer: all queries MUST be scoped by `tenant_id` and the API MUST reject any query that does not resolve to a tenant context associated with the authenticated principal. |
| REQ-F-084 | **Must** | All mutating API calls (incident acknowledgment, hypothesis feedback, freeze trigger, config change, role assignment) MUST produce an immutable audit log entry containing: principal ID, action type, resource ID, timestamp, and source IP. |
| REQ-F-085 | **Must** | Audit logs MUST be stored in a separate, append-only store with retention of at least 1 year (independent of signal retention). |
| REQ-F-086 | **Must** | The system MUST support integration with an external OIDC identity provider (e.g., Okta, Auth0, Google Workspace) for authentication. Username/password authentication is a fallback only for local/development environments. |
| REQ-F-087 | **Should** | The system SHOULD support SCIM 2.0 for automated user provisioning and deprovisioning from the IdP. |
| REQ-F-088 | **Should** | The system SHOULD enforce a tenant-level data isolation guarantee: a platform engineer in Tenant A MUST NOT be able to craft an API query that returns signals from Tenant B, even via injection or parameter manipulation. This MUST be validated by automated tests. |
| REQ-F-089 | **Could** | The system COULD support attribute-based access control (ABAC) for fine-grained service-level isolation within a tenant (e.g., team X can only see signals for services they own). |
| REQ-F-090 | **Won't (v1)** | Full hard-tenancy (isolated infrastructure per tenant) is deferred. v1 implements soft tenancy with logical isolation enforced at the application and query layer. |

---

## 6. Non-Functional Requirements

### 6.1 Performance & Latency

| ID | Priority | Requirement |
|----|----------|-------------|
| REQ-N-001 | **Must** | Ingest-to-queryable latency (adapter receipt → normalized signal available in ClickHouse/OpenSearch) MUST be ≤ 30 seconds at the 95th percentile under nominal load. |
| REQ-N-002 | **Must** | Ingest-to-queryable latency MUST be ≤ 60 seconds at the 99th percentile under nominal load. |
| REQ-N-003 | **Must** | Correlation computation latency (all constituent signals ingested → `CorrelationEdge` written) MUST be ≤ 30 seconds at p95 for incidents with up to 20 correlated services. |
| REQ-N-004 | **Must** | Freeze-window snapshot capture MUST begin within 60 seconds of incident creation and MUST produce a complete snapshot manifest within 10 minutes of capture start, for incidents covering up to 50 services and up to 1M log lines in the capture window. |
| REQ-N-005 | **Must** | RCA engine MUST produce a first-ranked hypothesis within 5 minutes of snapshot finalization, targeting 95% of incidents with ≥ 3 correlated signals. |
| REQ-N-006 | **Must** | Console API read queries (incident list, signal search, service map) MUST respond within 2 seconds at p95 for the scale targets defined in REQ-N-013. |
| REQ-N-007 | **Should** | Snapshot replay queries SHOULD perform within 10% of equivalent live query response times (i.e., replaying a snapshot SHOULD feel as fast as querying live data). |

### 6.2 Availability & Durability

| ID | Priority | Requirement |
|----|----------|-------------|
| REQ-N-008 | **Must** | The PulseGraph API and console MUST target 99.9% monthly availability (≤ 43.8 min/month downtime). Planned maintenance windows are included in this budget. |
| REQ-N-009 | **Must** | The ingest pipeline (adapter → Kafka → normalize → store) MUST be designed for at-least-once delivery: no signal MUST be silently dropped. Duplicate signal handling (idempotent writes) MUST be implemented in the Normalize service. |
| REQ-N-010 | **Must** | Incident snapshots MUST be stored with 99.999999% (11 nines) durability via multi-region object store replication or equivalent. Snapshot data is the highest-durability artifact in the system. |
| REQ-N-011 | **Must** | The system MUST tolerate the failure of any single Kafka broker, ClickHouse shard, or OpenSearch node without data loss or ingest interruption beyond a brief rebalance period (target: < 60 seconds impact). |
| REQ-N-012 | **Should** | Individual service failures (e.g., Correlate service crash) SHOULD result in degraded functionality (correlation unavailable) but MUST NOT impact ingest availability or snapshot replay. Circuit-breaker patterns SHOULD be employed for inter-service calls. |

### 6.3 Scale Assumptions

| ID | Priority | Requirement |
|----|----------|-------------|
| REQ-N-013 | **Must** | The system MUST be designed to sustain the following v1 scale targets without horizontal scaling intervention: **50,000 metric data points/sec** across all tenants; **200,000 log lines/sec** across all tenants; **10,000 trace spans/sec** across all tenants; **5,000 events/sec** across all tenants. |
| REQ-N-014 | **Must** | The system MUST support up to **10,000 unique metric series (cardinality)** per service and up to **50 services** per tenant in v1 without requiring per-tenant resource quotas. |
| REQ-N-015 | **Must** | The system MUST support up to **50 concurrent tenants** in the v1 soft-tenancy model. |
| REQ-N-016 | **Should** | The system SHOULD be horizontally scalable beyond the v1 targets: the ingest, normalize, and correlate services SHOULD scale linearly with Kafka partition count. Scale targets for v2 are at least 10× v1. |
| REQ-N-017 | **Should** | ClickHouse sharding and replication topology SHOULD be designed to accommodate 3× data volume growth over 12 months without a schema migration or service downtime. |

### 6.4 Security & Compliance

| ID | Priority | Requirement |
|----|----------|-------------|
| REQ-N-018 | **Must** | All external API traffic MUST be encrypted in transit using TLS 1.2 minimum (TLS 1.3 preferred). Inter-service traffic within the cluster MUST use mutual TLS (mTLS) or an equivalent service mesh policy. |
| REQ-N-019 | **Must** | All secrets (vendor API keys, database credentials, signing keys) MUST be stored in a secrets manager (e.g., HashiCorp Vault, AWS Secrets Manager) and MUST NOT appear in environment variables visible to child processes, config files, or container images. |
| REQ-N-020 | **Must** | PII redaction (REQ-F-013) MUST be applied before any signal is written to any persistent store. The redaction policy MUST be configurable per tenant and MUST support at minimum: email, IPv4, IPv6, credit card number (PAN), and SSN patterns. Redaction MUST be irreversible (hash or drop, not mask) unless a reversible tokenization mode is explicitly configured and audited. |
| REQ-N-021 | **Must** | Authentication tokens MUST be short-lived JWTs (access token max lifetime: 1 hour) with refresh token rotation. Refresh tokens MUST be invalidated on logout and on password/credential change. |
| REQ-N-022 | **Must** | The system MUST implement rate limiting on all API endpoints to prevent abuse and resource exhaustion: per-principal and per-tenant limits MUST be configurable. |
| REQ-N-023 | **Must** | Container images MUST be built from minimal base images (distroless or equivalent), vulnerability-scanned in CI (e.g., Trivy), and MUST NOT contain critical/high CVEs at time of release. |
| REQ-N-024 | **Should** | The system SHOULD be architected with SOC 2 Type II controls in mind from the start: access logging, change management, availability monitoring, and logical access controls SHOULD be designed to satisfy SOC 2 criteria even if formal certification is a post-v1 milestone. |
| REQ-N-025 | **Should** | Vendor API credentials SHOULD be rotatable without service restart: the adapter configuration SHOULD support hot-reload of credentials on a configurable polling interval (default: 60 seconds). |
| REQ-N-026 | **Could** | The system COULD support field-level encryption for sensitive signal attributes (beyond PII redaction) for tenants with contractual data handling requirements. |

### 6.5 Observability of PulseGraph Itself

| ID | Priority | Requirement |
|----|----------|-------------|
| REQ-N-027 | **Must** | Every PulseGraph service MUST emit OpenTelemetry traces for all inbound requests and significant internal operations (Kafka consume/produce, database writes, external API calls). |
| REQ-N-028 | **Must** | Every PulseGraph service MUST expose a Prometheus-compatible `/metrics` endpoint, with at minimum: request count, error rate, latency histograms (p50/p95/p99), Kafka consumer lag, and database query latency by operation type. |
| REQ-N-029 | **Must** | Structured JSON logs MUST be emitted by all services at configurable log levels (default: INFO in production). Log lines MUST include: service name, trace ID (if in request context), tenant ID (if applicable), severity, and message. |
| REQ-N-030 | **Must** | A self-monitoring pipeline MUST be configured: PulseGraph's own MELT signals MUST be routable to either its own storage (dogfooding) or an external monitoring stack. The pipeline health dashboard MUST be available within the PulseGraph console. |
| REQ-N-031 | **Should** | SLO definitions for PulseGraph's own ingest latency (REQ-N-001), correlation latency (REQ-N-003), and API latency (REQ-N-006) SHOULD be encoded as burn-rate alerts on the self-monitoring pipeline. |

### 6.6 Data Retention & Lifecycle

| ID | Priority | Requirement |
|----|----------|-------------|
| REQ-N-032 | **Must** | Retention policies MUST be enforced automatically (no manual cleanup required). TTL-based expiry MUST be implemented at the store level (ClickHouse TTL expressions, OpenSearch ILM policies, object store lifecycle rules). |
| REQ-N-033 | **Must** | Incident snapshots MUST be subject to a separate, longer retention policy (default: 2 years) and MUST NOT be deleted by normal signal retention TTL processes. |
| REQ-N-034 | **Should** | Tenants SHOULD be able to configure per-service retention policies (e.g., shorter retention for high-volume debug logs, longer retention for SLO-relevant metrics). |

---

## 7. Success Metrics

The following metrics define quantitative targets for PulseGraph's business and operational impact. Baselines are measured against the team's current tooling at time of PulseGraph adoption.

| ID | Metric | Target | Measurement Method |
|----|--------|--------|--------------------|
| SM-01 | **MTTR Reduction** | ≥ 40% reduction in median MTTR for P0/P1 incidents within 90 days of a team's onboarding | Compare median MTTR (PulseGraph-assisted incidents) vs. baseline median MTTR (pre-onboarding, same team, same severity) |
| SM-02 | **Time-to-Context** | ≤ 3 minutes from PagerDuty page to correlated incident view with first hypothesis, at p50 | Measured from PulseGraph incident creation timestamp to first hypothesis creation timestamp |
| SM-03 | **RCA False Positive Rate** | ≤ 25% of top-ranked hypotheses rejected by the DRI within 30 days of launch; ≤ 15% at 6 months | Derived from hypothesis accept/reject feedback (REQ-F-051); measured per incident |
| SM-04 | **Snapshot Coverage** | ≥ 95% of P0/P1 incidents have a complete snapshot within 10 minutes of detection | Measured from incident creation to snapshot manifest completion timestamp |
| SM-05 | **Cross-Vendor Correlation Hit Rate** | ≥ 70% of incidents involving 2+ vendor sources have at least one confirmed cross-vendor `CorrelationEdge` | Measured from correlation store; cross-vendor edges identified by differing `source_vendor` attributes on linked signals |
| SM-06 | **Ingest Pipeline Availability** | ≥ 99.9% uptime (measured as: no signal drops > 60-second window) | Kafka consumer lag monitoring + dead-letter queue monitoring |
| SM-07 | **Console Adoption** | ≥ 80% of on-call incident acknowledgments initiated from PulseGraph console (vs. source vendor UI) within 6 months of team onboarding | Audit log: incident acknowledgment source tracked |
| SM-08 | **Postmortem Completeness Score** | Postmortems for PulseGraph-snapshotted incidents rated ≥ 4/5 on evidence availability by incident DRI | Post-incident survey per incident (optional but tracked) |

---

## 8. Constraints & Assumptions

### 8.1 Technical Constraints

| ID | Constraint |
|----|------------|
| C-01 | PulseGraph MUST use Go for all ingest/correlation services; TypeScript/React for the console; Python for RCA/ML workers. Divergence from this stack requires explicit architectural review. |
| C-02 | Kafka (or Redpanda in local environments) is the mandatory message bus. Direct point-to-point service communication for signal data is not permitted; all signals flow via Kafka topics. |
| C-03 | ClickHouse is the mandatory store for metrics, traces, and structured events. Alternative OLAP stores (e.g., Apache Druid, BigQuery) are not in scope for v1. |
| C-04 | OpenSearch is the mandatory store for logs. Alternative log stores (e.g., Elasticsearch, Loki) are not in scope for v1. |
| C-05 | Object storage (S3-compatible API) is required for snapshot persistence. GCS or Azure Blob may be used via S3-compatibility layer. |
| C-06 | The local development environment MUST run entirely via `docker-compose up` with no external cloud service dependencies. Vendor adapter integrations run in stub/mock mode locally. |
| C-07 | All services MUST produce OTel-native telemetry (see §6.5). Vendor-specific instrumentation SDKs within PulseGraph itself are not permitted. |

### 8.2 Assumptions

| ID | Assumption |
|----|------------|
| A-01 | Source vendors (Datadog, New Relic, Dynatrace) support outbound webhook or API-based export of all four MELT signal types in their current product tiers used by customers. This must be validated per-vendor during adapter development. |
| A-02 | Services instrumented in source vendors emit W3C TraceContext headers or a compatible vendor-proprietary correlation header that can be mapped to `trace_id` during normalization. Services with no trace context propagation cannot benefit from cross-signal log↔trace correlation. |
| A-03 | Tenants are expected to define their SLOs and burn-rate alert configurations in the source vendor platforms. PulseGraph consumes but does not define SLOs. |
| A-04 | The initial deployment environment is a managed Kubernetes cluster (EKS, GKE, or AKS). Bare-metal or VM-only deployment is not a supported production target for v1. |
| A-05 | Engineering teams adopting PulseGraph will designate a platform engineer as the integration owner for at least the first 90 days post-onboarding. |
| A-06 | Vendor API rate limits may constrain real-time signal pull frequency. Adapters are assumed to primarily use push mechanisms (webhooks, streaming exports) where available; polling is a fallback with rate-limit-aware backoff. |
| A-07 | RCA engine effectiveness is predicated on sufficient signal density: incidents with fewer than 3 correlated signals may not yield a high-confidence hypothesis. This is a known limitation and should be communicated to users. |

---

## 9. Out of Scope for v1

The following capabilities are explicitly deferred and will not be implemented in the v1 release. They are documented here to prevent scope creep and to serve as input for the v2 roadmap.

| ID | Deferred Capability |
|----|---------------------|
| OOS-01 | **Splunk adapter** — Integration with Splunk Enterprise or Splunk Cloud as a signal source. |
| OOS-02 | **Autonomous remediation** — Any action taken by PulseGraph against live infrastructure (rollback, scaling, traffic shifting). PulseGraph v1 is read-side and advisory only. |
| OOS-03 | **SLO authoring** — Creating or editing SLO definitions in PulseGraph. SLOs remain owned by source vendors. |
| OOS-04 | **On-call scheduling** — PulseGraph does not manage rotation schedules or escalation policies (delegated to PagerDuty). |
| OOS-05 | **Hard multi-tenancy** — Fully isolated infrastructure per tenant. v1 is soft tenancy with logical isolation. |
| OOS-06 | **Continuous profiling** — Integration with profiling tools (Pyroscope, Parca, Datadog Continuous Profiler). |
| OOS-07 | **Mobile application** — Native iOS or Android application for PulseGraph. |
| OOS-08 | **Email alerting** — Email as a notification channel. v1 supports PagerDuty and Slack only. |
| OOS-09 | **Microsoft Teams integration** — Deferred to v1.x. |
| OOS-10 | **Custom metric ingestion SDK** — A PulseGraph-native instrumentation SDK for sending raw metrics. v1 depends entirely on vendor/OTel sources. |
| OOS-11 | **Cost analytics** — Analysis or attribution of vendor spend (e.g., Datadog DPM cost per service). |
| OOS-12 | **Synthetic monitoring** — Scheduled uptime checks or transaction monitors. |
| OOS-13 | **ABAC / service-level RBAC** — Fine-grained attribute-based access control within a tenant. v1 uses tenant-level roles only (REQ-F-082). |
| OOS-14 | **Bi-directional vendor sync** — Writing alert silence, annotation, or configuration back to source vendor platforms. |
| OOS-15 | **Generative AI incident narrative (GA)** — LLM-assisted hypothesis summaries (REQ-F-055) are a v1 "Could" feature; if not completed in v1, they are explicitly deferred to v1.x. |

---

## 10. Resolved Decisions (formerly Open Questions)

Decisions below are **Approved for v1** from product defaults and HLD alignment. Items that still need per-customer legal/security sign-off are marked **Customer gate** (architecture is fixed; enablement is gated).

| ID | Decision | Rationale | Impact |
|----|----------|-----------|--------|
| OQ-01 | **Webhook auth = per-vendor shared HMAC secret** validated at ingest-gateway / adapter edge. Optional IP allowlist as defense-in-depth. mTLS only where the vendor supports it. | Matches HLD §9.1; broadly supported by Datadog/NR/Dynatrace webhook configs. | REQ-F-030, REQ-N-018 |
| OQ-02 | **Same cloud account, dedicated snapshots bucket** with Object Lock (WORM) and lifecycle rules. Separate account/region replication is a v1.x hardening option, not a v1 blocker. | Local MinIO path and S3-compatible production path stay simple; durability via object lock + versioning. | REQ-F-040, REQ-N-010 |
| OQ-03 | **Irreversible redaction by default** (hash or drop). Reversible tokenization is deferred (Could / post-v1) and must be explicitly configured + audited if ever enabled. | Minimizes compliance surface for v1; aligns with REQ-N-020. | REQ-N-020 |
| OQ-04 | **LLM narrative is opt-in and disabled by default** (`RCA_LLM_ENABLED=false`). Provider selection is a deployment-time config when a tenant enables it; no signal content leaves the configured region/provider boundary. | REQ-F-055 is Could; keeps core RCA deterministic without blocking v1. | REQ-F-055 |
| OQ-05 | **No commercial billing model in v1.** Soft tenancy uses signal-volume **rate limits / quotas** only (not invoices). Billing unit deferred to product packaging. | Platform is open-source / self-hosted first; quotas still protect shared infra. | REQ-N-014, REQ-F-082 |
| OQ-06 | **Hypothesis-level accept/reject** for v1 feedback. Evidence-link granularity deferred. | Lower UX complexity; still feeds Bayesian weight adjustment (REQ-F-054). | REQ-F-054 |
| OQ-07 | **Shared platform services may appear in each tenant's map** as nodes, but **signals remain tenant-scoped**. Cross-tenant raw signal access is forbidden. | Preserves blast-radius visibility without breaking soft isolation. | REQ-F-058, REQ-F-083 |
| OQ-08 | **Hard caps bind snapshot cost**: default ≤ 100k log lines, T−30m … T+15m window (REQ-F-038/039), metric resolution at store grain. No unbounded capture. | Gives a concrete ceiling without a finance model. | REQ-F-039 |
| OQ-09 | **v1 DR targets: RTO ≤ 4 hours, RPO ≤ 15 minutes** for control plane + hot stores; snapshots remain durable independently via object store. | Fits soft-tenancy overlay posture; multi-region active-active deferred. | REQ-N-008–REQ-N-012 |
| OQ-10 | **Soft tenancy is acceptable for v1** with automated isolation tests (REQ-F-088). Contractual hard isolation → hard tenancy (OOS-05 / v2). | Matches approved architecture. | REQ-F-083, REQ-F-088 |
| OQ-11 | **Customer gate:** production adapter enablement requires the customer to confirm vendor contract export/re-storage rights. Product architecture assumes export is permitted. | Legal varies by contract; cannot be globally closed in-repo. | REQ-F-001–REQ-F-003 |
| OQ-12 | **Surface runbook URL when present** on incident labels / PagerDuty payload. No wiki crawl or runbook authoring in v1. | Low effort, high value; stays advisory-only. | REQ-F-048 |

---

*End of Document*

---

**Document History**

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 0.1 | 2026-07-11 | Platform Engineering | Initial draft |
| 1.0 | 2026-09-17 | Platform Engineering | Promoted to Approved; resolved open questions from product defaults |
