# snapshot

Go types and helpers for immutable incident snapshot (freeze-window) bundles stored in MinIO / S3.

## Module

```
github.com/pulsegraph/snapshot
```

## Contents

| File | Purpose |
|------|---------|
| `types.go` | Manifest, metadata, and freeze-window structs |
| `bundle.go` | Assemble / checksum / write bundle layout |
| `bundle_test.go` | Unit tests for manifest integrity |

## Bundle layout (summary)

```
s3://{bucket}/{tenant_id}/{incident_id}/
  manifest.json
  metadata.json
  topology/subgraph.json
  metrics/metrics.parquet
  traces/traces.parquet
  logs/logs.parquet
  rca/{run_id}.json
```

Default capture window: **T−30m … T+15m** (REQ-F-038). See [HLD §5](../../docs/HLD.md).

```bash
cd packages/snapshot && go test ./...
```
