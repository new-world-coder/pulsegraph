# Integration Manual — Third-Party Observability & Incident Tools

**Audience:** Platform engineers connecting real systems to PulseGraph  
**Related:** [USER_MANUAL.md](USER_MANUAL.md) · [FEATURES.md](FEATURES.md) · [PILOT_LITMUS.md](PILOT_LITMUS.md)

---

## 1. Integration principles

1. **Overlay, don’t rip-and-replace** — keep Datadog/NR/Dynatrace agents; keep incident.io/Rootly for Slack.  
2. Prefer **OpenTelemetry** as the first production wire.  
3. Start with **staging / non-prod** tenant data.  
4. **Redact PII** before it hits Kafka/MinIO.  
5. Lock network paths (no public Kafka).

Canonical model: OTel → `melt.Envelope` (ADR-0001).

---

## 2. Integration matrix (current readiness)

| System | Path today | Readiness | Notes |
|--------|------------|-----------|-------|
| **OpenTelemetry** (apps, Collector) | OTLP/HTTP JSON → `adapter-otel:4318` | ✅ Ready for litmus | Primary path |
| **Datadog** | Stub `adapter-datadog`; preferred: DD → OTLP export / Collector | 🔶 Interim OTLP | Native adapter TBD |
| **New Relic** | Stub `adapter-newrelic`; preferred: OTLP to us | 🔶 Interim OTLP | |
| **Dynatrace** | Stub `adapter-dynatrace`; preferred: OTLP / OpenPipeline export | 🔶 Interim OTLP | AWS estates often run DT + CloudWatch |
| **AWS CloudWatch / X-Ray / Application Signals** | Via ADOT Collector → OTLP | 🔶 Pattern ready | No native CW adapter yet |
| **Prometheus** | Via OTel Collector Prometheus receiver → OTLP | 🔶 Pattern ready | |
| **Grafana Loki** | Forwarder / Collector → OTLP logs | 🔶 Pattern ready | |
| **PagerDuty** | Webhook → `POST /v1/freeze` | 🔶 Pattern ready | Map PD incident id → `incident_id` |
| **Opsgenie** | Same webhook pattern | 🔶 Pattern ready | |
| **incident.io** | Workflow webhook / HTTP action → freeze | 🔶 Pattern ready | Do not replace `/inc` |
| **Rootly** | Workflow HTTP action → freeze | 🔶 Pattern ready | Complements DD screenshot feature |
| **Slack** | Slash command / bot → freeze (not built) | 🧾 Planned | Use PD/Rootly webhook first |
| **GitHub / CI deploys** | Deploy event → Context Graph `deployment` node | 🧾 Planned | BF-024 |

---

## 3. OpenTelemetry (recommended first)

### 3.1 Application SDK

Configure OTLP HTTP exporter:

```text
OTEL_EXPORTER_OTLP_ENDPOINT=http://<pulsegraph-host>:4318
OTEL_EXPORTER_OTLP_PROTOCOL=http/json
OTEL_SERVICE_NAME=checkout
OTEL_RESOURCE_ATTRIBUTES=deployment.environment=staging
```

> MVP adapter expects **JSON**. If your SDK defaults to protobuf, put an **OpenTelemetry Collector** in front that exports JSON HTTP, or extend the adapter (tracked gap).

### 3.2 OpenTelemetry Collector

```yaml
receivers:
  otlp:
    protocols:
      grpc:
      http:
exporters:
  otlphttp:
    endpoint: http://pulsegraph-host:4318
    # encoding/json if supported by your collector version
service:
  pipelines:
    traces:
      receivers: [otlp]
      exporters: [otlphttp]
    metrics:
      receivers: [otlp]
      exporters: [otlphttp]
    logs:
      receivers: [otlp]
      exporters: [otlphttp]
```

### 3.3 AWS Distro for OpenTelemetry (ADOT)

On EKS/ECS/EC2: scrape / receive X-Ray & app telemetry → export OTLP to PulseGraph.  
CloudWatch Metrics Streams → (future) partner path via Collector or Dynatrace; for litmus, OTLP from apps is enough.

---

## 4. Datadog

### Interim (works today)

1. Enable **OpenTelemetry** export from Datadog Agent / DD OTel collector, **or**  
2. Run OTel Collector with Datadog receiver → OTLP to PulseGraph.

### Native (scaffolded)

`services/adapter-datadog` listens for intake-like HTTP but does **not** yet publish Kafka. Do not rely on it for pilots.

### Incident trigger

Datadog monitor → Webhook:

```http
POST https://<pulsegraph>/v1/freeze
Content-Type: application/json

{
  "incident_id": "{{alert_id}}",
  "service_name": "{{service}}",
  "trigger": "webhook",
  "tenant_id": "acme-staging"
}
```

Put this behind Caddy with basic auth or mTLS.

---

## 5. New Relic

- Prefer **OTLP** export to PulseGraph.  
- Native Events API adapter is a stub.  
- Optional: NR alert webhook → `/v1/freeze` (same schema as §4).

---

## 6. Dynatrace

- Prefer **OTLP** / OpenPipeline export of spans/logs/metrics.  
- Problems API adapter is a stub — map **Problem notifications** webhook → `/v1/freeze` with `incident_id=<problemId>`.  
- Context Graph is our Smartscape-inspired layer; we do **not** require OneAgent replacement.

---

## 7. Incident workflow tools (incident.io, Rootly, FireHydrant)

| Goal | Integration |
|------|-------------|
| Freeze at incident declare | HTTP action / webhook to `/v1/freeze` |
| Attach evidence | Store `bundle_uri` + `manifest_sha256` as incident custom field / timeline comment |
| Keep Slack UX | Leave channel automation in Rootly/incident.io |

Example Rootly/incident.io HTTP body:

```json
{
  "incident_id": "{{incident.external_id}}",
  "service_name": "{{incident.service}}",
  "trigger": "incident",
  "pre_window_sec": 600,
  "post_window_sec": 120,
  "depth": 2
}
```

---

## 8. PagerDuty

1. Create **Generic V2 Webhook** or Event Orchestration → custom HTTPS endpoint.  
2. Small glue (future `adapter-pagerduty`) or serverless relay that maps PD payload → freeze JSON.  
3. For litmus, a static curl from a PD test event is enough.

---

## 9. Security checklist for customer integrations

- [ ] Staging data only for first litmus  
- [ ] TLS on public endpoints  
- [ ] Auth on `/v1/freeze` and graph APIs (add basic auth / JWT before customer traffic)  
- [ ] Network allowlists (customer collector IPs)  
- [ ] Written DPA / data handling note (logs may contain PII)  
- [ ] Document retention of MinIO snapshots  

---

## 10. Acceptance criteria per integration

| Integration | Done when |
|-------------|-----------|
| OTel | Spans visible in freeze `traces.json` for named service |
| Webhook freeze | PD/Rootly test creates MinIO bundle with matching `incident_id` |
| Graph | Subgraph returns the customer service name after traffic |
| Vendor dual-run | Customer keeps existing APM UI; PulseGraph receives a copy via OTLP |

---

## 11. Support contacts (fill for your pilot)

| Role | Name | Contact |
|------|------|---------|
| PulseGraph operator | | |
| Customer observability owner | | |
| Customer security reviewer | | |
