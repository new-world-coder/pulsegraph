# contextgraph

In-memory / serialisable Context Graph model used for blast-radius and topology enrichment.

## Module

```
github.com/pulsegraph/contextgraph
```

## Contents

| File | Purpose |
|------|---------|
| `types.go` | Node / edge / graph types |
| `graph.go` | Graph operations (add, subgraph) |
| `serialize.go` | JSON serialisation for snapshots / API |
| `graph_test.go` | Unit tests |

## Docs

- Product model: [CONTEXT_GRAPH.md](../../docs/CONTEXT_GRAPH.md)
- Topology service: `services/topology-builder`

```bash
cd packages/contextgraph && go test ./...
```
