# adapter-datadog

Go service that receives telemetry events from the Datadog agent or webhooks, normalises them into the PulseGraph canonical MELT envelope using the `otel-normalize` package, and publishes to the Redpanda `raw.events` topic.

## Responsibilities

- Accept Datadog intake format (`/api/v1/intake`) over HTTP
- Optionally poll the Datadog API for metric series or event streams
- Translate Datadog metric types (gauge, count, rate) to MELT canonical format
- Forward enriched events to the `raw.events` Kafka topic

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `8200` | HTTP listen port |
| `KAFKA_BROKERS` | `localhost:9092` | Redpanda broker addresses |
| `DD_API_KEY` | — | Datadog API key (for polling mode) |
| `LOG_LEVEL` | `info` | Structured log level |

## Running Locally

```bash
go run ./cmd/server/main.go
```

## Building

```bash
go build -o bin/adapter-datadog ./cmd/server/
```

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/healthz` | Health check |
| `POST` | `/api/v1/intake` | Datadog intake receiver |
