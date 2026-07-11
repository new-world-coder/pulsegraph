package contextgraph

import "encoding/json"

// Snapshot is a point-in-time, fully serializable view of a Graph.
// It is designed for embedding in incident records, RCA reports, and
// snapshotter commands so consumers can reconstruct the exact graph state
// at the moment an event occurred without re-querying live topology.
type Snapshot struct {
	Nodes []Node `json:"nodes"`
	Edges []Edge `json:"edges"`
}

// MarshalSnapshot serializes g as a JSON Snapshot.
func MarshalSnapshot(g *Graph) ([]byte, error) {
	snap := Snapshot{
		Nodes: g.Nodes(),
		Edges: g.Edges(),
	}
	return json.Marshal(snap)
}

// UnmarshalSnapshot decodes a JSON Snapshot into a new Graph.
// Nodes and edges are upserted, so duplicate IDs from the snapshot are
// handled gracefully (last write wins on labels/metadata).
func UnmarshalSnapshot(data []byte) (*Graph, error) {
	var snap Snapshot
	if err := json.Unmarshal(data, &snap); err != nil {
		return nil, err
	}
	g := New()
	for _, n := range snap.Nodes {
		g.UpsertNode(n)
	}
	for _, e := range snap.Edges {
		g.UpsertEdge(e)
	}
	return g, nil
}
