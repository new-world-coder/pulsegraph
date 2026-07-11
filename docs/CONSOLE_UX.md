# Console UX — Agentic War Room

**Related:** [COMPETITOR_AUTONOMOPS.md](COMPETITOR_AUTONOMOPS.md) · [USER_MANUAL.md](USER_MANUAL.md) · Mockup: `docs/assets/pulsegraph-war-room-mockup.png`

---

## 1. What frontend we provide

| Surface | Tech | Role |
|---------|------|------|
| **PulseGraph Console** | React 18 + TypeScript + Vite (`apps/console`) | Primary human UI |
| Default route | **Agentic War Room** | AutonomOps-inspired multi-agent investigation canvas |
| Secondary | Incidents, Context Graph, Evidence | Navigation tabs |

There is **no** separate mobile app. Console proxies `/api` → `query-api :8090` (when wired); War Room can also call topology `:8091` and snapshotter `:8092` via Vite proxy.

---

## 2. Screen map

1. **War Room** (home) — brand + incident intent + 5-step agent rail + blast radius + hypotheses + freeze/remediation.  
2. **Incidents** — list of freezes / SEVs (fixture data until query-api).  
3. **Context Graph** — subgraph explorer (fixture + API hook).  
4. **Evidence** — SnapshotRef / MinIO bundle summary.

---

## 3. Visual direction

- Light operational paper (not dark-mode default; not purple/indigo glow).  
- Brand **PulseGraph** is the hero signal on War Room.  
- Fonts: **IBM Plex Sans** + **IBM Plex Mono**.  
- Accent: steel teal `#0D7377`.  
- Motion: agent step progress, hypothesis reveal, freeze pulse.

---

## 4. Agentic pipeline (UI)

```text
Triage → Metrics → Logs → Correlate → RCA → Remediate
                              ↑
                     Context Graph / Blast Radius
                              ↑
                     Evidence Freeze (PulseGraph edge)
```

Agents write into a shared `InvestigationState` rendered as timeline + confidence-ranked hypotheses.
