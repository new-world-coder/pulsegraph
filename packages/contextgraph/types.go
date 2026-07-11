package contextgraph

import "time"

// NodeKind classifies a graph node by its infrastructure / observability role.
type NodeKind string

const (
	NodeKindService    NodeKind = "service"
	NodeKindDatabase   NodeKind = "database"
	NodeKindQueue      NodeKind = "queue"
	NodeKindHost       NodeKind = "host"
	NodeKindDeployment NodeKind = "deployment"
	NodeKindIncident   NodeKind = "incident"
	NodeKindAlert      NodeKind = "alert"
	NodeKindTeam       NodeKind = "team"
)

// EdgeKind describes the relationship between two graph nodes.
type EdgeKind string

const (
	// EdgeKindCalls represents a synchronous call dependency (e.g. service A calls service B).
	EdgeKindCalls EdgeKind = "calls"
	// EdgeKindDependsOn represents an async or logical dependency (queues, shared DBs).
	EdgeKindDependsOn EdgeKind = "depends_on"
	// EdgeKindRunsOn represents a service-to-host / container-to-host placement.
	EdgeKindRunsOn EdgeKind = "runs_on"
	// EdgeKindDeployedAs connects a service node to a specific deployment artifact.
	EdgeKindDeployedAs EdgeKind = "deployed_as"
	// EdgeKindTriggered connects a deployment or change event to a resulting incident/alert.
	EdgeKindTriggered EdgeKind = "triggered"
	// EdgeKindOwnedBy connects any entity to the team that owns it.
	EdgeKindOwnedBy EdgeKind = "owned_by"
)

// Node is a vertex in the context graph representing any observable entity.
type Node struct {
	ID        string            `json:"id"`
	Kind      NodeKind          `json:"kind"`
	Labels    map[string]string `json:"labels,omitempty"`
	FirstSeen time.Time         `json:"first_seen"`
	LastSeen  time.Time         `json:"last_seen"`
}

// Edge is a directed relationship between two nodes in the context graph.
// ID is deterministic: "<from>:<kind>:<to>" for call/dependency edges so
// upserting the same logical edge is idempotent.
type Edge struct {
	ID       string            `json:"id"`
	From     string            `json:"from"`
	To       string            `json:"to"`
	Kind     EdgeKind          `json:"kind"`
	Labels   map[string]string `json:"labels,omitempty"`
	LastSeen time.Time         `json:"last_seen"`
}
