# topology-builder

Go Kafka consumer that builds and continuously updates the **context graph** (live service dependency / entity relationship graph) by extracting caller/callee edges from distributed trace spans. Nodes are services (and later resources/DBs); edges carry call-rate, error-rate, and latency. The graph feeds blast-radius overlays, incident snapshots, and RCA graph-walk hypotheses.

Stored in OpenSearch (and Postgres for durability per HLD) and exposed via `query-api`.

## Responsibilities

- Consume trace spans from `normalised.events` topic (consumer group `topology-builder`)
- Extract `(parent_service, child_service, edge_attributes)` from span parent-child links
- Maintain an in-memory adjacency list; batch upsert to OpenSearch index `topology-graph`
- Publish `topology.updated` events to `topology.changes` topic for dependent services
- Evict stale edges that haven't been seen for a configurable TTL (default 10 minutes)

## Graph Schema (OpenSearch)

Node document (`topology-graph` index, type `node`):
```json
{
  "service_name": "checkout",
  "environment": "production",
  "first_seen": "2024-01-01T00:00:00Z",
  "last_seen": "2024-01-01T01:00:00Z",
  "metadata": {}
}
```

Edge document (`topology-graph` index, type `edge`):
```json
{
  "from": "api-gateway",
  "to": "checkout",
  "call_count": 42,
  "error_rate": 0.01,
  "p99_latency_ms": 145
}
```

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `KAFKA_BROKERS` | `localhost:9092` | Redpanda broker addresses |
| `OPENSEARCH_URL` | `http://localhost:9200` | OpenSearch endpoint |
| `EDGE_TTL_SEC` | `600` | Seconds before stale edge eviction |
| `LOG_LEVEL` | `info` | Structured log level |

## Running Locally

```bash
go run ./cmd/server/main.go
```

## Building

```bash
go build -o bin/topology-builder ./cmd/server/
```
