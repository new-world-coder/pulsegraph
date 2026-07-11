# Pilot Litmus Plan — Real-World Signal Without Boiling the Ocean

**Audience:** Founder + first design-partner SRE  
**Related:** [ORACLE_SETUP.md](ORACLE_SETUP.md) · [INTEGRATION.md](INTEGRATION.md) · [TESTING.md](TESTING.md)

---

## 1. Goal

Prove, on Oracle Always Free (or local), that PulseGraph can:

1. Ingest real (or realistic) telemetry.  
2. Build a Context Graph.  
3. Freeze evidence on incident declaration.  
4. Show a path to **MTTR / time-to-context** improvement — without replacing the customer’s incident.io/Rootly/Datadog stack.

---

## 2. Success metrics (pilot)

| Metric | Target (directional) |
|--------|----------------------|
| Time-to-freeze | &lt; 30s from webhook/API call to MinIO objects |
| Time-to-context | Investigator opens subgraph + freeze in &lt; 3 minutes |
| Snapshot coverage | ≥ 1 successful non-empty freeze on a real staging service |
| Customer disruption | Zero change to prod agents beyond optional OTLP fan-out |

---

## 3. Litmus levels

### L0 — Lab (local)

- [ ] `make up && make bootstrap`  
- [ ] `make smoke-ingest`  
- [ ] Context Graph healthz  
- [ ] `make smoke-snapshot`  

### L1 — Self-dogfood on Oracle

- [ ] Oracle VM per [ORACLE_SETUP.md](ORACLE_SETUP.md)  
- [ ] Deploy per [DEPLOYMENT.md](DEPLOYMENT.md)  
- [ ] Run [OpenTelemetry Demo](https://opentelemetry.io/docs/demo/) or a tiny instrumented service → `adapter-otel`  
- [ ] Freeze `service_name` from that demo  

### L2 — Workflow hook

- [ ] PagerDuty **test** event or Rootly/incident.io **HTTP action** → `/v1/freeze`  
- [ ] Confirm `incident_id` correlation in MinIO path  

### L3 — Customer staging (one signal)

Ask for **one** of:

| Ask | Prefer |
|-----|--------|
| OTLP from staging Collector | Best |
| Datadog monitor webhook only | Good |
| Dynatrace problem notification webhook | Good |

**Do not ask for** prod API keys, full agent replacement, or SEV-1 routing on day one.

### L4 — Dual-run week

- Customer keeps existing tools.  
- PulseGraph receives a copy of staging OTLP + freeze on declared incidents.  
- Weekly review: did freeze+graph speed investigation?

---

## 4. Customer one-pager (copy/paste)

> PulseGraph is an AIOps overlay that builds a **context graph** and **freezes** metrics/logs/traces at incident time.  
> We integrate beside Datadog/Dynatrace/New Relic and incident.io/Rootly — we do not replace them.  
> For a 2-week litmus we need: (1) staging OTLP endpoint access or a monitor webhook, (2) a staging Slack/PagerDuty test channel, (3) a 30-minute readout.  
> Data stays in our pilot VM / your VPC; we redact PII on request; you can revoke access anytime.

---

## 5. Exit criteria → next build

| If litmus shows… | Build next |
|------------------|------------|
| Freeze valuable, graph empty | Richer trace edge extraction + deploy events |
| Graph good, RCA missing | `rca-engine` graph-walk (tranche C) |
| Webhooks painful | Soft auth + signed webhooks |
| Customer wants Slack | Thin bot calling `/v1/freeze` |

---

## 6. Abort criteria

- Customer cannot provide staging data within 2 weeks → stay on L1 demo.  
- Oracle A1 quota unavailable → Hetzner/local per strategy thread; revisit OCI later.  
- Legal blocks log egress → customer-side Ollama Option C + on-prem VM only.
