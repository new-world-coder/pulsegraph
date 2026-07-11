# adapter-newrelic

Go service that receives New Relic event payloads (NRDB webhooks, metric API) and normalises them into the PulseGraph MELT canonical envelope before publishing to Redpanda.

## Responsibilities

- Accept New Relic Event API payloads over HTTP (`/api/v1/events`)
- Parse NR metric format and map to MELT canonical types
- Enrich events with service graph metadata
- Publish MeltEnvelope records to the `raw.events` Kafka topic

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `8201` | HTTP listen port |
| `KAFKA_BROKERS` | `localhost:9092` | Redpanda broker addresses |
| `NR_LICENSE_KEY` | — | New Relic license key (for inbound auth validation) |
| `LOG_LEVEL` | `info` | Structured log level |

## Running Locally

```bash
go run ./cmd/server/main.go
```

## Building

```bash
go build -o bin/adapter-newrelic ./cmd/server/
```

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/healthz` | Health check |
| `POST` | `/api/v1/events` | New Relic events receiver |
