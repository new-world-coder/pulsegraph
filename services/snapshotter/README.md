# snapshotter

Go service that periodically captures the current state of the service topology graph and metric baselines, serialises them as compressed blobs to MinIO, and writes snapshot metadata to ClickHouse — enabling the RCA engine to replay historical state for root-cause analysis.

## Responsibilities

- Subscribe to `normalised.events` and `topology.changes` topics
- On a configurable interval (default 1 minute), snapshot:
  - Current topology adjacency list
  - Per-service metric baselines (p50/p95/p99 over past 5 min)
  - Active incident window state
- Compress and write blobs to MinIO bucket `snapshots`
- Write snapshot index record to ClickHouse table `snapshots.records`

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `KAFKA_BROKERS` | `localhost:9092` | Redpanda broker addresses |
| `MINIO_ENDPOINT` | `http://localhost:9001` | MinIO S3 endpoint |
| `MINIO_SNAPSHOTS_BUCKET` | `snapshots` | Target bucket |
| `CLICKHOUSE_HOST` | `localhost:8123` | ClickHouse HTTP endpoint |
| `SNAPSHOT_INTERVAL_SEC` | `60` | Snapshot frequency |
| `LOG_LEVEL` | `info` | Structured log level |

## Running Locally

```bash
go run ./cmd/server/main.go
```

## Building

```bash
go build -o bin/snapshotter ./cmd/server/
```
