# normalizer

Go Kafka consumer that reads from the `raw.events` topic, applies vendor-specific normalisation via the `otel-normalize` package to produce a canonical MELT envelope, and writes the result to the `normalised.events` topic.

## Responsibilities

- Consume from Redpanda `raw.events` topic (consumer group `normalizer`)
- Detect source vendor from envelope header
- Apply appropriate normaliser (Datadog, New Relic, Dynatrace, OTEL)
- Publish canonical MeltEnvelope to `normalised.events` topic
- Write dead-letter messages to `raw.events.dlq` on normalisation failure

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `KAFKA_BROKERS` | `localhost:9092` | Redpanda broker addresses |
| `KAFKA_GROUP_ID` | `normalizer` | Consumer group ID |
| `LOG_LEVEL` | `info` | Structured log level |

## Running Locally

```bash
go run ./cmd/server/main.go
```

## Building

```bash
go build -o bin/normalizer ./cmd/server/
```

## Topics

| Topic | Direction | Description |
|-------|-----------|-------------|
| `raw.events` | consume | Raw vendor events from adapters |
| `normalised.events` | produce | Canonical MELT envelopes |
| `raw.events.dlq` | produce | Failed normalisation messages |
