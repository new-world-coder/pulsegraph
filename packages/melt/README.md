# melt

Canonical MELT (Metrics, Events, Logs, Traces) helpers for PulseGraph: envelopes, Kafka topic names, and OTLP/JSON parsing used on the hot path.

## Module

```
github.com/pulsegraph/melt
```

## Contents

| Path | Purpose |
|------|---------|
| `envelope.go` | Internal envelope / metadata types |
| `topics.go` | Topic name constants (`raw.melt`, `norm.melt`, …) |
| `otlpjson/` | OTLP/JSON parse helpers for the MVP ingest smoke path |

## Design notes

- OpenTelemetry remains the **canonical** data model (ADR-0001); this package is the shared Go representation before full protobuf codegen lands under `proto/`.
- Adapters translate vendor payloads → OTel/MELT before publish.

```bash
cd packages/melt && go test ./...
```
