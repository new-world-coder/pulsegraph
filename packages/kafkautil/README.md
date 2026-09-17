# kafkautil

Shared Go helpers for Kafka / Redpanda producers and consumers used by PulseGraph stream services.

## Module

```
github.com/pulsegraph/kafkautil
```

## Contents

| File | Purpose |
|------|---------|
| `producer.go` | Thin producer wrapper (brokers, topic publish) |
| `consumer.go` | Consumer-group helper with at-least-once offset handling |

## Usage

Imported by MVP services (`ingest-gateway`, `normalizer`, etc.) via the root `go.work`. Topics are defined in `packages/melt`.

```bash
cd packages/kafkautil && go test ./...
```

See [ARCHITECTURE §3](../../docs/ARCHITECTURE.md) for the topic catalogue and DLQ policy.
