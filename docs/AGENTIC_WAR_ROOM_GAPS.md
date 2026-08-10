# Agentic War Room — Market Gap Analysis

**Related:** [FEATURES.md](FEATURES.md) · [COMPETITIVE_ANALYSIS.md](COMPETITIVE_ANALYSIS.md) · [CONSOLE_UX.md](CONSOLE_UX.md)

---

## 1. Positioning

The agentic SRE / incident-investigation category centers on a **multi-agent War Room**: staged autonomous investigation (triage → parallel investigate → RCA → blast radius → remediation with human approval). Adjacent patterns in the market include agent chat over telemetry, NL topology/dashboard generation, predictive intelligence, and auto-remediation playbooks.

PulseGraph remains an **overlay** (multi-vendor MELT + Context Graph + immutable freeze). We should **not** pitch “replace your observability stack”; we **should** match the War Room interaction model as the primary console experience while differentiating on evidence freeze and cross-vendor Context Graph.

---

## 2. Market capability checklist vs PulseGraph

| Capability | Priority for us | PulseGraph status | Notes |
|------------|-----------------|-------------------|-------|
| **Agentic War Room (5-step)** | P0 | 🔶 UI built; agents simulated | Console War Room ships with staged agent pipeline + freeze CTA |
| Multi-agent investigate (metrics/logs/traces/RCA/remediation) | P0 | 🧾 Backend stubs | Wire to `rca-engine` + snapshotter next |
| Blast radius visualization | P0 | 🔶 API exists | War Room embeds graph panel; live fetch TBD |
| Topology GPT / AI topology | P1 | 🔶 Context Graph (no NL yet) | Our graph is structural; NL later |
| Agent Chat (NL over logs/metrics) | P1 | ❌ | Copilot pane in War Room (UI stub) |
| Intent-based war room (“describe incident”) | P1 | 🔶 UI intent box | Backend parse TBD |
| Predictive intelligence (3–6h) | P2 | ❌ | After RCA quality |
| Dashboard GPT | P2 | ❌ | Not core overlay thesis |
| Auto-remediation + approval gates | P1 | 🧾 UI approve only | No executor yet (safe) |
| Dynamic / self-updating runbooks | P2 | ❌ | |
| Knowledge graph / institutional memory | P1 | 🔶 Context Graph + snapshots | Snapshots are our memory edge |
| SLO / error budget | P1 | 🧾 Spec | |
| Alert fatigue reduction | P1 | ❌ | Correlator stub |
| Cost optimization | P3 | ❌ | Out of scope |
| Multi-cloud unified pane | P2 | 🔶 via OTel federation | |
| Enterprise SOC2/HIPAA claims | P2 | ❌ | Don’t claim ([SECURITY.md](SECURITY.md)) |

---

## 3. War Room 5-step mapping → PulseGraph

| Step | Market pattern | PulseGraph implementation plan |
|------|----------------|--------------------------------|
| 1 Intelligent Triage | Classify / prioritize | Severity + service from webhook/freeze command |
| 2 Parallel Investigation | Metrics/Logs/Traces agents | Fan-out over freeze bundle + live APIs |
| 3 Root Cause Analysis | RCA agent | `rca-engine` hypotheses + confidence |
| 4 Impact Assessment | Blast radius | Context Graph `/blast-radius` |
| 5 Automated Remediation | Suggest/execute + HITL | Suggest only until policy engine exists; **Freeze** is our unique step 0/1 evidence lock |

**PulseGraph exclusive step:** **Evidence Freeze** (immutable MinIO bundle) — multi-vendor WORM freeze is not a common advertised War Room step elsewhere.

---

## 4. Must-have list (product backlog)

1. **Agentic War Room** as default console home.  
2. Shared investigation state across agents (timeline + evidence refs).  
3. Intent box: natural-language incident kickoff.  
4. Live blast-radius panel fed by topology-builder.  
5. Freeze-from-war-room one click.  
6. HITL remediation proposals (no silent prod mutate).  
7. Agent Chat side panel (P1).  
8. Post-incident auto timeline from freeze + agent steps (P1).

---

## 5. What we will not copy blindly

- Inflated marketing metrics without our own measurement.  
- Closed-loop auto-heal on customer prod without explicit policy.  
- Claiming to replace Datadog/Dynatrace — contradicts overlay thesis.
