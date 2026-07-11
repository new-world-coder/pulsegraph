# topology-builder — Context Graph Builder

Go service that builds and continuously updates the **Context Graph** — an entity-relationship graph connecting services, databases, queues, deployments, incidents, and teams — by extracting caller/callee edges from distributed trace spans in normalised MELT envelopes.

The Context Graph is the vendor-agnostic equivalent of Dynatrace Smartscape, Chronosphere Temporal Knowledge Graph, and Datadog Service Map.  It feeds blast-radius overlays, incident snapshots, and RCA graph-walk hypotheses.

## Responsibilities

- Consume `norm.melt` topic (consumer group `topology-builder`); configurable via `NORM_TOPIC`
- Extract `(caller_service → callee_service, edge_kind)` from OTLP trace span payloads using three strategies:
  1. `peer.service` span attribute (most reliable for client spans)
  2. `net.peer.name` span attribute (weak peer hint)
  3. Cross-resource `parentSpanId` correlation
- Maintain a live `contextgraph.Graph` in memory
- Expose HTTP REST API on `:8091` for graph queries
- Publish edge-change events to `topology.changes` (best-effort; failures are logged and skipped)

## HTTP API

| Method | Path | Query params | Description |
|--------|------|-------------|-------------|
| `GET` | `/healthz` | — | Health check + node/edge counts |
| `GET` | `/v1/graph` | `depth` (default: all) | Full graph snapshot (or depth-sampled export) |
| `GET` | `/v1/graph/subgraph` | `service`, `depth` (default 2) | Bidirectional BFS subgraph around a service |
| `GET` | `/v1/graph/blast-radius` | `service`, `depth` (default 2) | All upstream services affected if `service` fails |

All endpoints return `application/json` with `{"nodes":[…],"edges":[…]}` shape (see `packages/contextgraph`).

### Example calls

```bash
# Health + graph stats
curl http://localhost:8091/healthz

# Full graph export
curl "http://localhost:8091/v1/graph"

# Subgraph around checkout (2 hops)
curl "http://localhost:8091/v1/graph/subgraph?service=checkout&depth=2"

# Blast radius of checkout failing (who is affected upstream?)
curl "http://localhost:8091/v1/graph/blast-radius?service=checkout&depth=3"
```

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `KAFKA_BROKERS` | `localhost:9092` | Redpanda/Kafka broker addresses (comma-separated) |
| `NORM_TOPIC` | `norm.melt` | Normalised MELT topic to consume |
| `CHANGE_TOPIC` | `topology.changes` | Topic to publish edge-change events |
| `KAFKA_GROUP_ID` | `topology-builder` | Consumer group ID |
| `HTTP_ADDR` | `:8091` | HTTP listen address |

## Running Locally

```bash
# From repo root
go run ./services/topology-builder/cmd/server/

# Or with explicit kafka
KAFKA_BROKERS=localhost:19092 go run ./services/topology-builder/cmd/server/
```

## Building

```bash
go build -o bin/topology-builder ./services/topology-builder/cmd/server/
```

## Graph Model

See [`packages/contextgraph`](../../packages/contextgraph/) and [`docs/CONTEXT_GRAPH.md`](../../docs/CONTEXT_GRAPH.md) for the full entity-relationship model, node/edge kinds, and API usage guide.
