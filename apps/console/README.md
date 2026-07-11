# console

React + TypeScript + Vite single-page application for the PulseGraph observability console.

## Features (planned)

- **Incidents** — active and historical incident list with severity, timeline, and correlation trace
- **Topology** — interactive service dependency graph with real-time edge health overlays
- **Metrics** — time-series explorer backed by ClickHouse via query-api
- **RCA** — root-cause analysis results from rca-engine with confidence scores and recommended actions

## Running Locally

```bash
# Install dependencies
npm install

# Start dev server (proxies /api/* to query-api on :8090)
npm run dev
# → http://localhost:3000
```

## Building

```bash
npm run build
# Output: dist/
```

## Configuration

The dev server proxies `/api` requests to `http://localhost:8090` (query-api). See `vite.config.ts`.

For production, set `VITE_API_BASE_URL` to the deployed query-api URL.

## Tech Stack

| Library | Version | Purpose |
|---------|---------|---------|
| React | 18 | UI framework |
| TypeScript | 5 | Type safety |
| Vite | 5 | Build tool & dev server |
