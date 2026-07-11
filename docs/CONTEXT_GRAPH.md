# Context Graph

> **Status:** Implemented — `packages/contextgraph` + `services/topology-builder`

The Context Graph is PulseGraph's entity-relationship graph that represents the live topology of an observed system.  It connects services, infrastructure entities, deployments, incidents, and ownership into a single queryable structure — enabling blast-radius estimation, incident root-cause graph-walks, and point-in-time topology snapshots.

---

## Table of Contents

1. [Motivation and Positioning](#motivation-and-positioning)
2. [Graph Model](#graph-model)
   - [Node Kinds](#node-kinds)
   - [Edge Kinds](#edge-kinds)
3. [Package: `contextgraph`](#package-contextgraph)
   - [Core Operations](#core-operations)
   - [Snapshot Serialization](#snapshot-serialization)
4. [Service: `topology-builder`](#service-topology-builder)
   - [Span Edge Extraction](#span-edge-extraction)
   - [HTTP API Reference](#http-api-reference)
   - [Topology Change Events](#topology-change-events)
5. [How Downstream Services Consume the Context Graph](#how-downstream-services-consume-the-context-graph)
   - [RCA Engine](#rca-engine)
   - [Snapshotter](#snapshotter)
   - [Correlator](#correlator)
6. [Competitive Mapping](#competitive-mapping)
7. [Extending the Graph](#extending-the-graph)

---

## Motivation and Positioning

Modern observability platforms all converge on some form of a live topology graph:

| Vendor | Feature Name | Notes |
|--------|-------------|-------|
| Dynatrace | Smartscape | Auto-discovered; includes hosts, processes, services, apps |
| Chronosphere | Temporal Knowledge Graph | Time-aware; entities linked to SLO state |
| Datadog | Service Map | Trace-derived; APM-scoped |
| Grafana | Service Graph | Prometheus-derived; OpenTelemetry-native |

PulseGraph's Context Graph is **vendor-agnostic** and **signal-driven**: edges are derived from real trace spans (not synthetic probes), enriched with deployment events, incident records, and ownership metadata from labels.  Any upstream vendor's telemetry, once normalized to MELT envelopes, contributes to the same unified graph.

---

## Graph Model

The graph is a **directed multigraph** — multiple edge kinds can exist between the same pair of nodes, and edges are directed (caller → callee).

### Node Kinds

| Kind | Description | Example ID |
|------|-------------|-----------|
| `service` | A running workload identified by `service.name` | `checkout` |
| `database` | A data store (relational, document, time-series) | `postgres-main` |
| `queue` | A message broker topic or queue | `orders.events` |
| `host` | A physical or virtual machine | `ip-10-0-1-42` |
| `deployment` | A specific rollout event for a service version | `checkout@v2.3.1-deploy-abc` |
| `incident` | An active or historical incident record | `INC-4892` |
| `alert` | A fired alert rule | `HighErrorRate:checkout` |
| `team` | An owning team | `payments-team` |

Node IDs are application-defined strings.  For services they are `service.name` from OTLP resource attributes.

### Edge Kinds

| Kind | Direction | Meaning |
|------|-----------|---------|
| `calls` | service → service/database/queue | Synchronous or async call observed in trace spans |
| `depends_on` | service → database/queue | Structural dependency (not necessarily a live call) |
| `runs_on` | service → host | Placement / scheduling |
| `deployed_as` | service → deployment | Links a service to a specific deployment artifact |
| `triggered` | deployment/change → incident/alert | Change-to-incident causation link |
| `owned_by` | any → team | Ownership / on-call routing |

---

## Package: `contextgraph`

**Module:** `github.com/pulsegraph/contextgraph`  
**Location:** `packages/contextgraph/`

The package provides an in-memory, thread-safe directed multigraph with no external dependencies beyond the Go standard library.

### Core Operations

```go
import "github.com/pulsegraph/contextgraph"

g := contextgraph.New()

// Insert / update nodes
g.UpsertNode(contextgraph.Node{
    ID:   "checkout",
    Kind: contextgraph.NodeKindService,
    Labels: map[string]string{"team": "payments", "env": "prod"},
})

// Insert / update edges (idempotent by ID "<from>:<kind>:<to>")
g.UpsertEdge(contextgraph.Edge{
    From: "frontend",
    To:   "checkout",
    Kind: contextgraph.EdgeKindCalls,
})

// Bidirectional BFS subgraph (2 hops around checkout)
sub := g.Subgraph([]string{"checkout"}, 2)

// Upstream blast radius (who is affected if checkout fails?)
blast := g.BlastRadius("checkout", 3)

// Direct neighbors + connecting edges
nodes, edges := g.Neighbors("checkout")

// Counts
fmt.Println(g.NodeCount(), g.EdgeCount())
```

**`Subgraph(seedIDs []string, depth int)`**  
Bidirectional BFS: follows both outgoing and incoming edges.  Useful for "show me the context around this service" queries.

**`BlastRadius(nodeID string, depth int)`**  
Upstream-only BFS: follows only incoming edges (`who calls me → who calls them`).  Returns the set of services that would experience degraded behavior if `nodeID` becomes unhealthy.

### Snapshot Serialization

Snapshots allow any graph (or subgraph) to be embedded in Kafka messages, stored in Postgres/ClickHouse, or returned from the REST API as a self-contained JSON document.

```go
// Serialize
data, err := contextgraph.MarshalSnapshot(blast)

// Deserialize into a new in-memory graph
restored, err := contextgraph.UnmarshalSnapshot(data)
```

Snapshot wire format:

```json
{
  "nodes": [
    {"id": "checkout", "kind": "service", "labels": {"team": "payments"}, "first_seen": "…", "last_seen": "…"},
    {"id": "frontend", "kind": "service", "labels": {}, "first_seen": "…", "last_seen": "…"}
  ],
  "edges": [
    {"id": "frontend:calls:checkout", "from": "frontend", "to": "checkout", "kind": "calls", "last_seen": "…"}
  ]
}
```

---

## Service: `topology-builder`

**Port:** `8091`  
**Location:** `services/topology-builder/`

The topology-builder service consumes `norm.melt` (or `$NORM_TOPIC`) and maintains the live Context Graph.

### Span Edge Extraction

For each normalised trace envelope, the service parses the OTLP/JSON `resourceSpans` payload and attempts three edge-extraction strategies in order:

1. **`peer.service` attribute** — Most reliable.  OpenTelemetry client instrumentation sets this attribute on outbound spans to identify the remote service.
2. **`net.peer.name` attribute** — Weaker peer hint from network-level attributes; yields a host-like node that can later be enriched to a service.
3. **`parentSpanId` cross-resource correlation** — When multiple `resourceSpans` entries appear in the same payload (e.g. a batch from different services), parent–child span relationships crossing resource boundaries indicate call direction.

Edges use `EdgeKindCalls`.  Duplicate edges (same `from:kind:to`) are deduplicated automatically by the graph's upsert logic.

### HTTP API Reference

Base URL: `http://topology-builder:8091`

#### `GET /healthz`

```json
{"status": "ok", "node_count": 42, "edge_count": 87}
```

#### `GET /v1/graph?depth=N`

Returns the full graph snapshot.  If `depth` is omitted or ≥ 99 the entire graph is returned.  Set `depth` to limit the export to nodes within N hops of any service node (useful for very large graphs).

**Response:** `{"nodes":[…], "edges":[…]}`

#### `GET /v1/graph/subgraph?service=checkout&depth=2`

Returns the bidirectional BFS subgraph centred on `service` up to `depth` hops.

| Param | Required | Default | Description |
|-------|----------|---------|-------------|
| `service` | yes | — | Node ID of the service to centre on |
| `depth` | no | `2` | BFS hop limit |

**Response:** `{"nodes":[…], "edges":[…]}`

#### `GET /v1/graph/blast-radius?service=checkout&depth=2`

Returns all upstream services that would be affected if `service` became unavailable (follows incoming `calls`/`depends_on` edges upstream).

| Param | Required | Default | Description |
|-------|----------|---------|-------------|
| `service` | yes | — | Node ID of the failing service |
| `depth` | no | `2` | BFS hop limit upstream |

**Response:** `{"nodes":[…], "edges":[…]}`

### Topology Change Events

When new edges are added, the service publishes a best-effort JSON event to the `topology.changes` Kafka topic:

```json
{
  "event_type": "topology.edges_updated",
  "service": "checkout",
  "edge_count": 2,
  "edges": [
    {"id": "frontend:calls:checkout", "from": "frontend", "to": "checkout", "kind": "calls", "last_seen": "…"}
  ],
  "timestamp": "2024-01-01T00:00:00Z"
}
```

Publish failures are logged and skipped — the in-memory graph is always updated regardless.

---

## How Downstream Services Consume the Context Graph

### RCA Engine

The `rca-engine` Python worker performs graph-walk hypothesis generation during active incidents.  It calls the topology-builder's `/v1/graph/blast-radius` endpoint with the incident's primary service and a configurable depth, then walks the returned subgraph to rank probable root causes by graph centrality.

```
incident detected (service: checkout)
  → GET /v1/graph/blast-radius?service=checkout&depth=3
  → subgraph: {frontend, checkout, payment, postgres}
  → rca-engine ranks: postgres (leaf, high fan-in) > payment > checkout
```

### Snapshotter

The `snapshotter` service embeds a topology snapshot in every incident record.  On a `snapshot.commands` message it:

1. Identifies the incident's primary service from the command payload.
2. Calls `/v1/graph/subgraph?service=<primary>&depth=2`.
3. Serializes the response (already a `contextgraph.Snapshot`) into the incident document stored in ClickHouse / Postgres.

The embedded snapshot preserves the exact topology at incident time — critical for post-incident review because the live graph changes as services recover.

### Correlator

The `correlator` listens on `topology.changes` to maintain secondary indexes (e.g. alert → service lookups, SLO impact mapping) and to re-evaluate open incidents when the topology changes (e.g. a new dependency edge appeared that was unknown at incident start).

---

## Competitive Mapping

| PulseGraph Concept | Dynatrace | Chronosphere | Datadog | Grafana |
|---------------------|-----------|-------------|---------|---------|
| Node (service) | Service entity | Service node | Service | Service |
| Node (host) | Host entity | Infrastructure | Host | — |
| Node (deployment) | Deployment event | Change event | Deployment marker | — |
| Node (incident) | Problem | Incident | Incident | Alert group |
| `calls` edge | Dependency link | Dependency | Trace edge | Service graph edge |
| `triggered` edge | Root cause indicator | Change-to-incident | — | — |
| `Subgraph` | Process flow map | Impact graph | Service Map | — |
| `BlastRadius` | Impact analysis | Blast radius | — | — |
| Snapshot | — | Temporal snapshot | — | — |

PulseGraph's key differentiators:
- **Vendor-agnostic**: edges from Datadog, Dynatrace, NewRelic, and OTel all feed the same graph.
- **Temporal snapshots**: graphs embedded in incidents are immutable point-in-time views.
- **Programmable**: `packages/contextgraph` is a pure-Go library with no external deps; any service can embed or query a graph.

---

## Extending the Graph

### Adding infrastructure nodes (host, DB, queue)

Enrich the graph at the time trace edges are extracted, or via a separate reconciler that reads from your CMDB / infrastructure inventory:

```go
g.UpsertNode(contextgraph.Node{
    ID:   "postgres-main",
    Kind: contextgraph.NodeKindDatabase,
    Labels: map[string]string{"engine": "postgres", "env": "prod"},
})
g.UpsertEdge(contextgraph.Edge{
    From: "checkout",
    To:   "postgres-main",
    Kind: contextgraph.EdgeKindDependsOn,
})
```

### Adding deployment and incident nodes

Post a `PUT /internal/graph/nodes` endpoint (future work) or have the deployment pipeline call `UpsertNode` + `UpsertEdge` directly via the topology-builder HTTP API.

### Persistence

The current implementation is in-memory and rehydrates from Kafka on startup.  For durability, implement a periodic snapshot writer that serializes `MarshalSnapshot(graph)` to Postgres or ClickHouse and restores on boot.

### TTL / stale edge eviction

A background goroutine can scan edges and evict any with `LastSeen` older than a configurable TTL (e.g. 10 minutes) by rebuilding the graph or exposing a `DeleteEdge` method on `contextgraph.Graph`.
