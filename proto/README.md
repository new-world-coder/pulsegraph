# proto — Shared Protobuf Schemas

This directory will contain the canonical `.proto` definitions shared across all PulseGraph services.

## Planned Schemas

| File | Description |
|------|-------------|
| `melt.proto` | MELT (Metrics, Events, Logs, Traces) canonical envelope |
| `incident.proto` | Incident and correlation record |
| `topology.proto` | Topology node and edge types |
| `snapshot.proto` | Snapshotter state record |

## Code Generation

Generated stubs will be emitted to `proto/gen/`:

```
proto/gen/
  go/        # generated Go types (github.com/pulsegraph/proto/gen/go)
  python/    # generated Python types (via grpc-tools)
  ts/        # generated TypeScript types (via ts-proto)
```

Add a `buf.gen.yaml` when protobuf tooling is wired in.

## MELT Canonical Schema (draft)

```protobuf
// proto/melt.proto — stub; not yet compiled
syntax = "proto3";
package pulsegraph.melt.v1;

message MeltEnvelope {
  string   source_vendor   = 1;  // "datadog" | "newrelic" | "dynatrace" | "otel"
  string   signal_type     = 2;  // "metric" | "event" | "log" | "trace"
  int64    timestamp_unix_ns = 3;
  string   service_name    = 4;
  string   environment     = 5;
  map<string, string> labels = 6;
  bytes    payload         = 7;  // vendor-normalised JSON or proto bytes
}
```
