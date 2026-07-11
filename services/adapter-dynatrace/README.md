# adapter-dynatrace

Go service that receives Dynatrace problem notifications, MINT metric push, and OneAgent events; normalises them to MELT canonical format and publishes to Redpanda.

## Responsibilities

- Accept Dynatrace problem notification webhooks
- Parse MINT time-series metric payloads
- Map Dynatrace entity model to PulseGraph service topology IDs
- Publish MeltEnvelope records to the `raw.events` Kafka topic

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `8202` | HTTP listen port |
| `KAFKA_BROKERS` | `localhost:9092` | Redpanda broker addresses |
| `DT_API_TOKEN` | — | Dynatrace API token for webhook validation |
| `LOG_LEVEL` | `info` | Structured log level |

## Running Locally

```bash
go run ./cmd/server/main.go
```

## Building

```bash
go build -o bin/adapter-dynatrace ./cmd/server/
```

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/healthz` | Health check |
| `POST` | `/api/v1/problems` | Dynatrace problem notification receiver |
