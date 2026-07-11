# correlator

Go Kafka consumer that reads from `normalised.events`, groups related signals using a sliding time-window and service-graph proximity algorithm, and emits incident records to the `incidents` topic and ClickHouse.

## Responsibilities

- Consume from `normalised.events` topic (consumer group `correlator`)
- Maintain a sliding correlation window (configurable, default 5 minutes)
- Group events by: error fingerprint, service, propagation path, timestamp proximity
- Emit IncidentRecord to `incidents` Kafka topic
- Persist incident records to ClickHouse table `incidents.records`

## Correlation Algorithm (planned)

1. Extract error type, stack fingerprint, and service from each event
2. Look up existing open windows in memory cache
3. If fingerprint matches an open window → append event, update severity
4. If no match → open new window, start TTL timer
5. On window close → emit correlated IncidentRecord

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `KAFKA_BROKERS` | `localhost:9092` | Redpanda broker addresses |
| `KAFKA_GROUP_ID` | `correlator` | Consumer group ID |
| `CORRELATION_WINDOW_SEC` | `300` | Sliding window size in seconds |
| `CLICKHOUSE_HOST` | `localhost:9000` | ClickHouse native TCP host |
| `LOG_LEVEL` | `info` | Structured log level |

## Running Locally

```bash
go run ./cmd/server/main.go
```

## Building

```bash
go build -o bin/correlator ./cmd/server/
```
