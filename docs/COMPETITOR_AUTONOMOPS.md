# Competitor: AutonomOps (HealR) — Gap Analysis

**Site:** [https://autonomops.ai](https://autonomops.ai)  
**Product branding:** HealR / AutonomOps AI — Agentic AI SRE  
**Related:** [FEATURES.md](FEATURES.md) · [COMPETITIVE_ANALYSIS.md](COMPETITIVE_ANALYSIS.md) · [CONSOLE_UX.md](CONSOLE_UX.md)

---

## 1. Positioning

AutonomOps markets an **Agentic SRE** platform whose headline differentiator is the **Agentic War Room**: multi-agent, 5-step autonomous investigation (triage → parallel investigate → RCA → blast radius → remediation with human approval). Adjacent features include Agent Chat, Topology GPT, Dashboard GPT, Predictive Intelligence, and auto-remediation playbooks.

PulseGraph remains an **overlay** (multi-vendor MELT + Context Graph + immutable freeze). We should **not** copy their “replace your observability stack” pitch; we **should** match the War Room interaction model as the primary console experience.

---

## 2. AutonomOps feature checklist vs PulseGraph

| AutonomOps capability | Priority for us | PulseGraph status | Notes |
|----------------------|-----------------|-------------------|-------|
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

## 3. War Room 5-step mapping (AutonomOps → PulseGraph)

| Step | AutonomOps | PulseGraph implementation plan |
|------|------------|--------------------------------|
| 1 Intelligent Triage | Classify / prioritize | Severity + service from webhook/freeze command |
| 2 Parallel Investigation | Metrics/Logs/Traces agents | Fan-out over freeze bundle + live APIs |
| 3 Root Cause Analysis | RCA agent | `rca-engine` hypotheses + confidence |
| 4 Impact Assessment | Blast radius | Context Graph `/blast-radius` |
| 5 Automated Remediation | Suggest/execute + HITL | Suggest only until policy engine exists; **Freeze** is our unique step 0/1 evidence lock |

**PulseGraph exclusive step:** **Evidence Freeze** (immutable MinIO bundle) — AutonomOps does not advertise multi-vendor WORM freeze.

---

## 4. Must-have list (product backlog from this competitor)

1. **Agentic War Room** as default console home (this PR / build).  
2. Shared investigation state across agents (timeline + evidence refs).  
3. Intent box: natural-language incident kickoff.  
4. Live blast-radius panel fed by topology-builder.  
5. Freeze-from-war-room one click.  
6. HITL remediation proposals (no silent prod mutate).  
7. Agent Chat side panel (P1).  
8. Post-incident auto timeline from freeze + agent steps (P1).

---

## 5. What we will not copy blindly

- Inflated marketing metrics (91% / 86%) without our own measurement.  
- Closed-loop auto-heal on customer prod without explicit policy.  
- Claiming to replace Datadog/Dynatrace — contradicts overlay thesis.
