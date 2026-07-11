package contextgraph

import (
	"encoding/json"
	"testing"
	"time"
)

// buildTestGraph constructs:
//
//	frontend → checkout → payment
//	checkout → inventory
//	checkout → postgres (database)
//	ops-team owns checkout
func buildTestGraph() *Graph {
	g := New()

	g.UpsertNode(Node{ID: "frontend", Kind: NodeKindService})
	g.UpsertNode(Node{ID: "checkout", Kind: NodeKindService})
	g.UpsertNode(Node{ID: "payment", Kind: NodeKindService})
	g.UpsertNode(Node{ID: "inventory", Kind: NodeKindService})
	g.UpsertNode(Node{ID: "postgres", Kind: NodeKindDatabase})
	g.UpsertNode(Node{ID: "ops-team", Kind: NodeKindTeam})

	g.UpsertEdge(Edge{From: "frontend", To: "checkout", Kind: EdgeKindCalls})
	g.UpsertEdge(Edge{From: "checkout", To: "payment", Kind: EdgeKindCalls})
	g.UpsertEdge(Edge{From: "checkout", To: "inventory", Kind: EdgeKindCalls})
	g.UpsertEdge(Edge{From: "checkout", To: "postgres", Kind: EdgeKindDependsOn})
	g.UpsertEdge(Edge{From: "ops-team", To: "checkout", Kind: EdgeKindOwnedBy})

	return g
}

func TestUpsertNodePreservesFirstSeen(t *testing.T) {
	g := New()
	ts := time.Date(2024, 1, 1, 0, 0, 0, 0, time.UTC)
	g.UpsertNode(Node{ID: "svc", Kind: NodeKindService, FirstSeen: ts})
	// Update labels but FirstSeen should not change.
	g.UpsertNode(Node{ID: "svc", Kind: NodeKindService, Labels: map[string]string{"env": "prod"}})

	nodes := g.Nodes()
	if len(nodes) != 1 {
		t.Fatalf("expected 1 node, got %d", len(nodes))
	}
	if !nodes[0].FirstSeen.Equal(ts) {
		t.Errorf("FirstSeen changed: got %v, want %v", nodes[0].FirstSeen, ts)
	}
	if nodes[0].Labels["env"] != "prod" {
		t.Errorf("labels not updated: %v", nodes[0].Labels)
	}
}

func TestUpsertEdgeIdempotent(t *testing.T) {
	g := New()
	e := Edge{From: "a", To: "b", Kind: EdgeKindCalls}
	g.UpsertEdge(e)
	g.UpsertEdge(e)
	g.UpsertEdge(e)

	if g.EdgeCount() != 1 {
		t.Errorf("expected 1 edge, got %d", g.EdgeCount())
	}
}

func TestUpsertEdgeAutoCreatesNodes(t *testing.T) {
	g := New()
	g.UpsertEdge(Edge{From: "x", To: "y", Kind: EdgeKindCalls})

	if g.NodeCount() != 2 {
		t.Errorf("expected 2 auto-created nodes, got %d", g.NodeCount())
	}
}

func TestNeighbors(t *testing.T) {
	g := buildTestGraph()
	nodes, edges := g.Neighbors("checkout")

	// checkout has: frontend (incoming), payment (outgoing), inventory (outgoing), postgres (outgoing), ops-team (incoming)
	if len(edges) != 5 {
		t.Errorf("expected 5 edges, got %d", len(edges))
	}
	if len(nodes) != 5 {
		t.Errorf("expected 5 neighbor nodes, got %d", len(nodes))
	}
}

func TestSubgraph_DepthZero(t *testing.T) {
	g := buildTestGraph()
	sub := g.Subgraph([]string{"checkout"}, 0)

	// At depth 0 only the seed node itself; no expansion.
	if sub.NodeCount() != 1 {
		t.Errorf("depth-0 subgraph: expected 1 node, got %d", sub.NodeCount())
	}
	if sub.EdgeCount() != 0 {
		t.Errorf("depth-0 subgraph: expected 0 edges, got %d", sub.EdgeCount())
	}
}

func TestSubgraph_DepthOne(t *testing.T) {
	g := buildTestGraph()
	sub := g.Subgraph([]string{"checkout"}, 1)

	// checkout + its 4 direct neighbors (frontend, payment, inventory, postgres, ops-team)
	if sub.NodeCount() != 6 {
		t.Errorf("depth-1 subgraph: expected 6 nodes, got %d", sub.NodeCount())
	}
}

func TestSubgraph_UnknownSeed(t *testing.T) {
	g := buildTestGraph()
	sub := g.Subgraph([]string{"nonexistent"}, 2)
	if sub.NodeCount() != 0 {
		t.Errorf("unknown seed: expected 0 nodes, got %d", sub.NodeCount())
	}
}

func TestSubgraph_MultiSeed(t *testing.T) {
	g := buildTestGraph()
	// Both payment and inventory are depth-1 from checkout, but seed them directly.
	sub := g.Subgraph([]string{"payment", "inventory"}, 1)
	// payment neighbors: checkout (1 hop); inventory neighbors: checkout (1 hop)
	// checkout adds frontend, postgres, ops-team at depth-2 but depth cap is 1.
	if sub.NodeCount() < 2 {
		t.Errorf("multi-seed: expected at least 2 nodes, got %d", sub.NodeCount())
	}
}

func TestBlastRadius_DirectCallers(t *testing.T) {
	g := buildTestGraph()
	// If checkout fails, frontend is affected (it calls checkout).
	sub := g.BlastRadius("checkout", 1)

	nodeIDs := map[string]bool{}
	for _, n := range sub.Nodes() {
		nodeIDs[n.ID] = true
	}
	if !nodeIDs["checkout"] {
		t.Error("blast radius must include the origin node")
	}
	if !nodeIDs["frontend"] {
		t.Error("blast radius must include direct caller 'frontend'")
	}
}

func TestBlastRadius_DepthZero(t *testing.T) {
	g := buildTestGraph()
	sub := g.BlastRadius("checkout", 0)

	if sub.NodeCount() != 1 {
		t.Errorf("depth-0 blast radius: expected 1 node, got %d", sub.NodeCount())
	}
}

func TestBlastRadius_LeafNode(t *testing.T) {
	g := buildTestGraph()
	// postgres has no callers except checkout, depth-1 should include checkout.
	sub := g.BlastRadius("postgres", 1)
	nodeIDs := map[string]bool{}
	for _, n := range sub.Nodes() {
		nodeIDs[n.ID] = true
	}
	if !nodeIDs["checkout"] {
		t.Error("postgres blast radius depth-1 must include 'checkout'")
	}
	// payment is not in blast radius of postgres.
	if nodeIDs["payment"] {
		t.Error("postgres blast radius must not include 'payment'")
	}
}

func TestSerializeRoundtrip(t *testing.T) {
	g := buildTestGraph()
	data, err := MarshalSnapshot(g)
	if err != nil {
		t.Fatalf("MarshalSnapshot: %v", err)
	}
	if !json.Valid(data) {
		t.Fatal("MarshalSnapshot produced invalid JSON")
	}

	restored, err := UnmarshalSnapshot(data)
	if err != nil {
		t.Fatalf("UnmarshalSnapshot: %v", err)
	}
	if restored.NodeCount() != g.NodeCount() {
		t.Errorf("node count mismatch after roundtrip: got %d, want %d", restored.NodeCount(), g.NodeCount())
	}
	if restored.EdgeCount() != g.EdgeCount() {
		t.Errorf("edge count mismatch after roundtrip: got %d, want %d", restored.EdgeCount(), g.EdgeCount())
	}
}

func TestSerializeSubgraph(t *testing.T) {
	g := buildTestGraph()
	sub := g.BlastRadius("checkout", 2)

	data, err := MarshalSnapshot(sub)
	if err != nil {
		t.Fatalf("MarshalSnapshot subgraph: %v", err)
	}
	var snap Snapshot
	if err := json.Unmarshal(data, &snap); err != nil {
		t.Fatalf("json.Unmarshal: %v", err)
	}
	if len(snap.Nodes) == 0 {
		t.Error("snapshot has no nodes")
	}
}

func TestEdgeKindFiltering(t *testing.T) {
	g := buildTestGraph()
	edges := g.Edges()

	kinds := map[EdgeKind]int{}
	for _, e := range edges {
		kinds[e.Kind]++
	}
	if kinds[EdgeKindCalls] != 3 {
		t.Errorf("expected 3 'calls' edges, got %d", kinds[EdgeKindCalls])
	}
	if kinds[EdgeKindDependsOn] != 1 {
		t.Errorf("expected 1 'depends_on' edge, got %d", kinds[EdgeKindDependsOn])
	}
	if kinds[EdgeKindOwnedBy] != 1 {
		t.Errorf("expected 1 'owned_by' edge, got %d", kinds[EdgeKindOwnedBy])
	}
}
