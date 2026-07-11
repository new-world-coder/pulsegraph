# otel-normalize

Shared Go library that converts vendor-specific telemetry payloads into the PulseGraph canonical MELT envelope defined in `proto/melt.proto`.

## Purpose

Each adapter service (`adapter-datadog`, `adapter-newrelic`, etc.) imports this package rather than reimplementing normalisation logic. The library is versioned independently and published as a Go module.

## Module Path

```
github.com/pulsegraph/otel-normalize
```

## Planned API

```go
package normalize

// Normalizer converts a raw vendor payload into a MeltEnvelope.
type Normalizer interface {
    Normalize(ctx context.Context, raw []byte) (*meltv1.MeltEnvelope, error)
    Vendor() string
}

// Available implementations (to be added):
//   DatadogNormalizer   — parses Datadog JSON format
//   NewRelicNormalizer  — parses New Relic NRDB format
//   DynatraceNormalizer — parses Dynatrace MINT / OneAgent format
//   OtelNormalizer      — passes through OTLP proto directly
```

## Usage (future)

```go
import normalize "github.com/pulsegraph/otel-normalize"

n := normalize.NewDatadogNormalizer(normalize.Config{...})
envelope, err := n.Normalize(ctx, rawPayload)
```

## Development

```bash
go test ./...
go vet ./...
```

This package will be initialised as a Go module once the first normaliser implementation is ready. See `proto/README.md` for the MELT envelope schema.
