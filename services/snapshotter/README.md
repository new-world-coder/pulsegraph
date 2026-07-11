# snapshotter

Go service that performs **incident-triggered telemetry freeze**: on `snapshot.commands` (or `POST /v1/freeze`), it captures a time-bounded MELT window + context-graph subgraph, writes an immutable JSON bundle to MinIO, and publishes a `snapshot.refs` pointer for RCA.

## Freeze window (HLD defaults)

- Pre-window: **10 minutes**
- Post-window: **2 minutes**
- Topology depth: **2**

## Bundle layout (MinIO)

```
s3://snapshots/{tenant_id}/{incident_id}/
  manifest.json              # SHA-256 index + window metadata
  metrics.json               # Melt envelopes (metrics) in window
  logs.json
  traces.json
  topology/subgraph.json     # from topology-builder Context Graph API
```

## Responsibilities

- Buffer recent `norm.melt` envelopes in memory (ring buffer)
- Consume `snapshot.commands` and run freeze pipeline
- Fetch subgraph from `TOPOLOGY_URL` (`topology-builder`)
- Put objects to MinIO; publish `snapshot.refs`
- HTTP:
  - `GET /healthz`
  - `POST /v1/freeze` — synchronous manual freeze
  - `GET /v1/snapshots/{incident_id}` — last in-process ref

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `8092` | HTTP listen port |
| `KAFKA_BROKERS` | `localhost:9092` | Redpanda brokers |
| `MINIO_ENDPOINT` | `http://localhost:9001` | S3 API endpoint |
| `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` | `pulsegraph` / `pulsegraph123` | Credentials |
| `MINIO_SNAPSHOTS_BUCKET` | `snapshots` | Target bucket |
| `TOPOLOGY_URL` | `http://localhost:8091` | Context Graph service |
| `SNAPSHOT_BUFFER_MAX` | `5000` | Max buffered envelopes |

## Running Locally

```bash
go run ./cmd/server
curl -s -X POST localhost:8092/v1/freeze \
  -H 'Content-Type: application/json' \
  -d '{"service_name":"checkout","trigger":"manual"}'
```

## Building

```bash
go build -o bin/snapshotter ./cmd/server/
```
