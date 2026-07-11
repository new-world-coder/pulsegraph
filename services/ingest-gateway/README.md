# ingest-gateway

Go service that acts as the central entry point for raw telemetry. It validates incoming MELT envelopes, applies schema checks, rate-limits per source, and fans events out to Redpanda partitions.

## Responsibilities

- Validate MELT envelope schema and required fields
- Apply per-source ingestion rate limits
- Route events to the correct Redpanda topic partition (by service / vendor)
- Emit ingestion metrics (events/sec, error rate) to Prometheus `/metrics`

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `8081` | HTTP listen port |
| `KAFKA_BROKERS` | `localhost:9092` | Redpanda broker addresses |
| `RATE_LIMIT_RPS` | `10000` | Max events per second per source |
| `LOG_LEVEL` | `info` | Structured log level |

## Running Locally

```bash
go run ./cmd/server/main.go
```

## Building

```bash
go build -o bin/ingest-gateway ./cmd/server/
```

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/healthz` | Health check |
| `POST` | `/ingest` | Accept MeltEnvelope batch |
| `GET` | `/metrics` | Prometheus metrics (future) |
