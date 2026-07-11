# query-api

Go HTTP API service that exposes incident data, topology graph queries, and time-series metrics from ClickHouse and OpenSearch to the PulseGraph console UI and external integrations.

## Responsibilities

- Serve REST endpoints for the console UI
- Query ClickHouse for incidents, metric time-series, and snapshot metadata
- Query OpenSearch for topology graph traversal
- Aggregate and join results for the frontend

## Planned Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/healthz` | Health check |
| `GET` | `/api/v1/incidents` | List/filter incidents (time-range, severity, service) |
| `GET` | `/api/v1/incidents/:id` | Get single incident with timeline |
| `GET` | `/api/v1/topology` | Service graph nodes and edges |
| `GET` | `/api/v1/topology/:service/upstream` | Upstream dependencies |
| `GET` | `/api/v1/topology/:service/downstream` | Downstream dependencies |
| `GET` | `/api/v1/metrics` | Metric time-series query |
| `GET` | `/api/v1/snapshots` | List historical snapshots |
| `GET` | `/api/v1/rca/:incident_id` | Fetch RCA result for an incident |

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `8090` | HTTP listen port |
| `CLICKHOUSE_HOST` | `localhost:8123` | ClickHouse HTTP endpoint |
| `OPENSEARCH_URL` | `http://localhost:9200` | OpenSearch endpoint |
| `LOG_LEVEL` | `info` | Structured log level |

## Running Locally

```bash
go run ./cmd/server/main.go
```

## Building

```bash
go build -o bin/query-api ./cmd/server/
```
