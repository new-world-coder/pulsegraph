# PulseGraph Console

React + TypeScript + Vite UI. **Default home = Agentic War Room** (multi-agent investigation), differentiated by PulseGraph **evidence freeze** + **Context Graph**.

## Screens

| Tab | Purpose |
|-----|---------|
| War Room | Intent box, 6-step agent rail, blast radius, RCA hypotheses, freeze + HITL remediate |
| Incidents | List (fixtures until query-api) |
| Context Graph | Topology view |
| Evidence | Freeze bundle layout |

See [docs/CONSOLE_UX.md](../../docs/CONSOLE_UX.md) and mockup [docs/assets/pulsegraph-war-room-mockup.png](../../docs/assets/pulsegraph-war-room-mockup.png).

## Run

```bash
cd apps/console
npm install
npm run dev
# http://localhost:3000
```

Proxies:

- `/freeze` → snapshotter `:8092/v1/freeze`
- `/graph` → topology-builder `:8091/v1/graph`
- `/api` → query-api `:8090`
