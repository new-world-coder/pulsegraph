# adapter-otel

Go service implementing the OTLP HTTP receiver. Accepts traces, metrics, and logs from any OpenTelemetry SDK and forwards them as MeltEnvelope records to Redpanda.

## Responsibilities

- Implement OTLP/HTTP endpoints (`/v1/traces`, `/v1/metrics`, `/v1/logs`)
- Decode OTLP protobuf or JSON payloads
- Pass through signals (OTEL is already canonical) with lightweight enrichment
- Publish MeltEnvelope records to the `raw.events` Kafka topic

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `4318` | OTLP HTTP port (standard) |
| `KAFKA_BROKERS` | `localhost:9092` | Redpanda broker addresses |
| `LOG_LEVEL` | `info` | Structured log level |

## Running Locally

```bash
go run ./cmd/server/main.go
```

## Building

```bash
go build -o bin/adapter-otel ./cmd/server/
```

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/healthz` | Health check |
| `POST` | `/v1/traces` | OTLP traces |
| `POST` | `/v1/metrics` | OTLP metrics |
| `POST` | `/v1/logs` | OTLP logs |
