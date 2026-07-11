package contextgraph

import (
	"fmt"
	"sync"
	"time"
)

// Graph is a thread-safe in-memory directed multigraph of observable entities.
// All public methods are safe for concurrent use.
type Graph struct {
	mu       sync.RWMutex
	nodes    map[string]Node   // nodeID → Node
	edges    map[string]Edge   // edgeID → Edge
	outgoing map[string][]string // nodeID → []edgeID  (caller side)
	incoming map[string][]string // nodeID → []edgeID  (callee side)
}

// New returns an empty, ready-to-use Graph.
func New() *Graph {
	return &Graph{
		nodes:    make(map[string]Node),
		edges:    make(map[string]Edge),
		outgoing: make(map[string][]string),
		incoming: make(map[string][]string),
	}
}

// UpsertNode inserts or updates a node. FirstSeen is preserved on update.
func (g *Graph) UpsertNode(n Node) {
	now := time.Now().UTC()
	g.mu.Lock()
	defer g.mu.Unlock()
	if existing, ok := g.nodes[n.ID]; ok {
		n.FirstSeen = existing.FirstSeen
	} else if n.FirstSeen.IsZero() {
		n.FirstSeen = now
	}
	if n.LastSeen.IsZero() {
		n.LastSeen = now
	}
	g.nodes[n.ID] = n
}

// UpsertEdge inserts or updates an edge. If e.ID is empty it is derived as
// "<from>:<kind>:<to>", making the same logical relationship idempotent.
// Auto-creates stub service nodes for unknown endpoints.
func (g *Graph) UpsertEdge(e Edge) {
	now := time.Now().UTC()
	if e.ID == "" {
		e.ID = fmt.Sprintf("%s:%s:%s", e.From, e.Kind, e.To)
	}
	if e.LastSeen.IsZero() {
		e.LastSeen = now
	}

	g.mu.Lock()
	defer g.mu.Unlock()

	// Auto-create stub nodes so the graph is always internally consistent.
	if _, ok := g.nodes[e.From]; !ok {
		g.nodes[e.From] = Node{ID: e.From, Kind: NodeKindService, FirstSeen: now, LastSeen: now}
	}
	if _, ok := g.nodes[e.To]; !ok {
		g.nodes[e.To] = Node{ID: e.To, Kind: NodeKindService, FirstSeen: now, LastSeen: now}
	}

	if _, exists := g.edges[e.ID]; !exists {
		// Register in adjacency lists only on first insert.
		g.outgoing[e.From] = append(g.outgoing[e.From], e.ID)
		g.incoming[e.To] = append(g.incoming[e.To], e.ID)
	}
	g.edges[e.ID] = e
}

// Neighbors returns all nodes one hop away from nodeID (both directions)
// and the edges that connect them.
func (g *Graph) Neighbors(nodeID string) (nodes []Node, edges []Edge) {
	g.mu.RLock()
	defer g.mu.RUnlock()

	seen := map[string]bool{}
	collect := func(id string) {
		if !seen[id] {
			seen[id] = true
			if n, ok := g.nodes[id]; ok {
				nodes = append(nodes, n)
			}
		}
	}
	for _, eid := range g.outgoing[nodeID] {
		e := g.edges[eid]
		edges = append(edges, e)
		collect(e.To)
	}
	for _, eid := range g.incoming[nodeID] {
		e := g.edges[eid]
		edges = append(edges, e)
		collect(e.From)
	}
	return nodes, edges
}

// Subgraph performs a bidirectional BFS from seedIDs up to depth hops and
// returns a new Graph containing only the reachable nodes and their edges.
func (g *Graph) Subgraph(seedIDs []string, depth int) *Graph {
	g.mu.RLock()
	defer g.mu.RUnlock()

	type item struct {
		id    string
		depth int
	}
	visited := map[string]bool{}
	queue := []item{}

	for _, id := range seedIDs {
		if _, ok := g.nodes[id]; ok {
			visited[id] = true
			queue = append(queue, item{id, 0})
		}
	}

	for len(queue) > 0 {
		cur := queue[0]
		queue = queue[1:]
		if cur.depth >= depth {
			continue
		}
		for _, eid := range g.outgoing[cur.id] {
			if to := g.edges[eid].To; !visited[to] {
				visited[to] = true
				queue = append(queue, item{to, cur.depth + 1})
			}
		}
		for _, eid := range g.incoming[cur.id] {
			if from := g.edges[eid].From; !visited[from] {
				visited[from] = true
				queue = append(queue, item{from, cur.depth + 1})
			}
		}
	}

	return g.buildSubgraph(visited)
}

// BlastRadius returns the subgraph of all nodes that are upstream callers of
// nodeID — i.e. the set of services that would be affected if nodeID fails.
// It traverses incoming ("calls"/"depends_on") edges up to depth hops.
func (g *Graph) BlastRadius(nodeID string, depth int) *Graph {
	g.mu.RLock()
	defer g.mu.RUnlock()

	type item struct {
		id    string
		depth int
	}
	visited := map[string]bool{nodeID: true}
	queue := []item{{nodeID, 0}}

	for len(queue) > 0 {
		cur := queue[0]
		queue = queue[1:]
		if cur.depth >= depth {
			continue
		}
		for _, eid := range g.incoming[cur.id] {
			if from := g.edges[eid].From; !visited[from] {
				visited[from] = true
				queue = append(queue, item{from, cur.depth + 1})
			}
		}
	}

	return g.buildSubgraph(visited)
}

// Nodes returns a snapshot of all nodes (copy; safe to mutate).
func (g *Graph) Nodes() []Node {
	g.mu.RLock()
	defer g.mu.RUnlock()
	out := make([]Node, 0, len(g.nodes))
	for _, n := range g.nodes {
		out = append(out, n)
	}
	return out
}

// Edges returns a snapshot of all edges (copy; safe to mutate).
func (g *Graph) Edges() []Edge {
	g.mu.RLock()
	defer g.mu.RUnlock()
	out := make([]Edge, 0, len(g.edges))
	for _, e := range g.edges {
		out = append(out, e)
	}
	return out
}

// NodeCount returns the number of nodes in the graph.
func (g *Graph) NodeCount() int {
	g.mu.RLock()
	defer g.mu.RUnlock()
	return len(g.nodes)
}

// EdgeCount returns the number of edges in the graph.
func (g *Graph) EdgeCount() int {
	g.mu.RLock()
	defer g.mu.RUnlock()
	return len(g.edges)
}

// buildSubgraph is called with the lock already held.
func (g *Graph) buildSubgraph(visited map[string]bool) *Graph {
	sub := New()
	for id := range visited {
		if n, ok := g.nodes[id]; ok {
			sub.nodes[id] = n
		}
	}
	for eid, e := range g.edges {
		if visited[e.From] && visited[e.To] {
			sub.edges[eid] = e
			sub.outgoing[e.From] = append(sub.outgoing[e.From], eid)
			sub.incoming[e.To] = append(sub.incoming[e.To], eid)
		}
	}
	return sub
}
