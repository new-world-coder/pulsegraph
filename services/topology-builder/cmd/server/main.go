// topology-builder — Context Graph builder service.
//
// Consumes normalised MELT envelopes from Kafka (norm.melt), extracts
// caller→callee relationships from OTLP trace payloads, and maintains a
// live contextgraph.Graph in memory.  Exposes a REST API for graph queries
// and publishes summary change events to topology.changes.
package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"strconv"
	"syscall"
	"time"

	"github.com/pulsegraph/contextgraph"
	"github.com/pulsegraph/kafkautil"
	"github.com/pulsegraph/melt"
)

// ────────────────────────────────────────────────────────────────────────────
// Config
// ────────────────────────────────────────────────────────────────────────────

type config struct {
	KafkaBrokers string
	NormTopic    string
	ChangeTopic  string
	GroupID      string
	HTTPAddr     string
}

func loadConfig() config {
	return config{
		KafkaBrokers: envOrDefault("KAFKA_BROKERS", "localhost:9092"),
		NormTopic:    envOrDefault("NORM_TOPIC", melt.TopicNormMelt),
		ChangeTopic:  envOrDefault("CHANGE_TOPIC", melt.TopicTopologyChanges),
		GroupID:      envOrDefault("KAFKA_GROUP_ID", "topology-builder"),
		HTTPAddr:     envOrDefault("HTTP_ADDR", ":8091"),
	}
}

// ────────────────────────────────────────────────────────────────────────────
// Service
// ────────────────────────────────────────────────────────────────────────────

type service struct {
	cfg      config
	graph    *contextgraph.Graph
	producer *kafkautil.Producer
	log      *slog.Logger
}

func newService(cfg config, log *slog.Logger) *service {
	brokers := kafkautil.ParseBrokers(cfg.KafkaBrokers)
	return &service{
		cfg:      cfg,
		graph:    contextgraph.New(),
		producer: kafkautil.NewProducer(brokers, cfg.ChangeTopic),
		log:      log,
	}
}

// ────────────────────────────────────────────────────────────────────────────
// Kafka consumer loop
// ────────────────────────────────────────────────────────────────────────────

func (s *service) runConsumer(ctx context.Context) error {
	brokers := kafkautil.ParseBrokers(s.cfg.KafkaBrokers)
	consumer := kafkautil.NewConsumer(brokers, s.cfg.NormTopic, s.cfg.GroupID)
	defer consumer.Close()

	s.log.Info("context-graph consumer started",
		"topic", s.cfg.NormTopic,
		"group", s.cfg.GroupID,
	)

	return consumer.Run(ctx, func(ctx context.Context, key, value []byte) error {
		env, err := melt.UnmarshalEnvelope(value)
		if err != nil {
			s.log.Warn("envelope unmarshal failed", "err", err)
			return nil // don't DLQ on bad envelopes
		}
		if env.SignalType != melt.SignalTrace {
			return nil // only traces carry span topology
		}
		edges := extractEdges(env)
		if len(edges) == 0 {
			return nil
		}
		for _, e := range edges {
			s.graph.UpsertEdge(e)
		}
		s.publishChange(ctx, env.ServiceName, edges)
		return nil
	})
}

// ────────────────────────────────────────────────────────────────────────────
// OTLP trace edge extraction
// ────────────────────────────────────────────────────────────────────────────

// extractEdges parses an OTLP/JSON trace payload from a Melt envelope and
// returns zero or more caller→callee edges.  Two strategies are tried:
//  1. span.attributes["peer.service"] — most reliable for outbound client spans.
//  2. resource cross-correlation via parentSpanId across resourceSpans entries
//     (best-effort; requires both parent and child spans in the same payload).
func extractEdges(env *melt.Envelope) []contextgraph.Edge {
	if len(env.Payload) == 0 {
		return nil
	}

	var root map[string]any
	if err := json.Unmarshal(env.Payload, &root); err != nil {
		return nil
	}

	rss, _ := root["resourceSpans"].([]any)
	if len(rss) == 0 {
		return nil
	}

	// Build a lookup: spanID → service name for cross-resource parent matching.
	spanService := map[string]string{}
	for _, rs := range rss {
		rsm, _ := rs.(map[string]any)
		svc := resourceService(rsm)
		sss, _ := rsm["scopeSpans"].([]any)
		for _, ss := range sss {
			ssm, _ := ss.(map[string]any)
			spans, _ := ssm["spans"].([]any)
			for _, sp := range spans {
				spm, _ := sp.(map[string]any)
				if sid, ok := spm["spanId"].(string); ok && sid != "" {
					spanService[sid] = svc
				}
			}
		}
	}

	now := time.Now().UTC()
	seen := map[string]bool{}
	var edges []contextgraph.Edge

	addEdge := func(from, to string, kind contextgraph.EdgeKind) {
		if from == "" || to == "" || from == to {
			return
		}
		id := fmt.Sprintf("%s:%s:%s", from, kind, to)
		if seen[id] {
			return
		}
		seen[id] = true
		edges = append(edges, contextgraph.Edge{
			ID:       id,
			From:     from,
			To:       to,
			Kind:     kind,
			LastSeen: now,
		})
	}

	for _, rs := range rss {
		rsm, _ := rs.(map[string]any)
		localSvc := resourceService(rsm)
		if localSvc == "" {
			localSvc = env.ServiceName
		}

		sss, _ := rsm["scopeSpans"].([]any)
		for _, ss := range sss {
			ssm, _ := ss.(map[string]any)
			spans, _ := ssm["spans"].([]any)
			for _, sp := range spans {
				spm, _ := sp.(map[string]any)
				attrs := attrMap(spm["attributes"])

				// Strategy 1: peer.service attribute on outbound spans.
				if peer, ok := attrs["peer.service"]; ok && peer != "" {
					addEdge(localSvc, peer, contextgraph.EdgeKindCalls)
				}

				// Strategy 2: net.peer.name / http.host as weak peer hint.
				if host, ok := attrs["net.peer.name"]; ok && host != "" {
					addEdge(localSvc, host, contextgraph.EdgeKindCalls)
				}

				// Strategy 3: parentSpanId cross-resource matching.
				parentID, _ := spm["parentSpanId"].(string)
				if parentID != "" {
					if parentSvc, ok := spanService[parentID]; ok && parentSvc != localSvc {
						// The parent span belongs to a different service → parent called us.
						addEdge(parentSvc, localSvc, contextgraph.EdgeKindCalls)
					}
				}
			}
		}
	}

	return edges
}

// resourceService extracts service.name from a resourceSpans object's resource.
func resourceService(rsm map[string]any) string {
	res, _ := rsm["resource"].(map[string]any)
	if res == nil {
		return ""
	}
	attrs := attrMap(res["attributes"])
	return attrs["service.name"]
}

// attrMap converts an OTLP attribute array to a plain string map.
func attrMap(raw any) map[string]string {
	out := map[string]string{}
	attrs, _ := raw.([]any)
	for _, a := range attrs {
		m, ok := a.(map[string]any)
		if !ok {
			continue
		}
		key, _ := m["key"].(string)
		val, _ := m["value"].(map[string]any)
		if key == "" || val == nil {
			continue
		}
		if s, ok := val["stringValue"].(string); ok {
			out[key] = s
		}
	}
	return out
}

// ────────────────────────────────────────────────────────────────────────────
// Topology change events
// ────────────────────────────────────────────────────────────────────────────

type changeEvent struct {
	EventType string                 `json:"event_type"`
	Service   string                 `json:"service"`
	EdgeCount int                    `json:"edge_count"`
	Edges     []contextgraph.Edge    `json:"edges"`
	Timestamp time.Time              `json:"timestamp"`
}

func (s *service) publishChange(ctx context.Context, svc string, edges []contextgraph.Edge) {
	evt := changeEvent{
		EventType: "topology.edges_updated",
		Service:   svc,
		EdgeCount: len(edges),
		Edges:     edges,
		Timestamp: time.Now().UTC(),
	}
	data, err := json.Marshal(evt)
	if err != nil {
		s.log.Warn("topology change marshal failed", "err", err)
		return
	}
	if err := s.producer.Publish(ctx, []byte(svc), data); err != nil {
		// Best-effort: log and continue — graph is still updated locally.
		s.log.Warn("topology change publish failed", "err", err)
	}
}

// ────────────────────────────────────────────────────────────────────────────
// HTTP handlers
// ────────────────────────────────────────────────────────────────────────────

func (s *service) routes() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", s.handleHealthz)
	mux.HandleFunc("GET /v1/graph", s.handleGraph)
	mux.HandleFunc("GET /v1/graph/subgraph", s.handleSubgraph)
	mux.HandleFunc("GET /v1/graph/blast-radius", s.handleBlastRadius)
	return mux
}

func (s *service) handleHealthz(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]any{
		"status":     "ok",
		"node_count": s.graph.NodeCount(),
		"edge_count": s.graph.EdgeCount(),
	})
}

// handleGraph returns the full graph snapshot (or a depth-limited sample if
// the graph is large).  Query param: depth (default 99, effectively unlimited).
func (s *service) handleGraph(w http.ResponseWriter, r *http.Request) {
	depth := queryInt(r, "depth", 99)

	// For large graphs, depth limits the export to nodes reachable from any
	// service-kind node within the given hop count.
	var target *contextgraph.Graph
	if depth >= 99 {
		target = s.graph
	} else {
		seeds := []string{}
		for _, n := range s.graph.Nodes() {
			if n.Kind == contextgraph.NodeKindService {
				seeds = append(seeds, n.ID)
			}
		}
		target = s.graph.Subgraph(seeds, depth)
	}

	data, err := contextgraph.MarshalSnapshot(target)
	if err != nil {
		http.Error(w, "snapshot error", http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	w.Write(data)
}

// handleSubgraph returns the sub-graph around a service node.
// Query params: service (required), depth (default 2).
func (s *service) handleSubgraph(w http.ResponseWriter, r *http.Request) {
	svc := r.URL.Query().Get("service")
	if svc == "" {
		http.Error(w, `{"error":"service param required"}`, http.StatusBadRequest)
		return
	}
	depth := queryInt(r, "depth", 2)
	sub := s.graph.Subgraph([]string{svc}, depth)

	data, err := contextgraph.MarshalSnapshot(sub)
	if err != nil {
		http.Error(w, "snapshot error", http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	w.Write(data)
}

// handleBlastRadius returns all upstream services affected if the given service fails.
// Query params: service (required), depth (default 2).
func (s *service) handleBlastRadius(w http.ResponseWriter, r *http.Request) {
	svc := r.URL.Query().Get("service")
	if svc == "" {
		http.Error(w, `{"error":"service param required"}`, http.StatusBadRequest)
		return
	}
	depth := queryInt(r, "depth", 2)
	sub := s.graph.BlastRadius(svc, depth)

	data, err := contextgraph.MarshalSnapshot(sub)
	if err != nil {
		http.Error(w, "snapshot error", http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	w.Write(data)
}

// ────────────────────────────────────────────────────────────────────────────
// Entry point
// ────────────────────────────────────────────────────────────────────────────

func main() {
	log := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	cfg := loadConfig()

	log.Info("context-graph builder starting",
		"http_addr", cfg.HTTPAddr,
		"kafka_brokers", cfg.KafkaBrokers,
		"norm_topic", cfg.NormTopic,
		"change_topic", cfg.ChangeTopic,
	)

	svc := newService(cfg, log)

	ctx, cancel := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer cancel()

	// HTTP server.
	srv := &http.Server{
		Addr:         cfg.HTTPAddr,
		Handler:      svc.routes(),
		ReadTimeout:  10 * time.Second,
		WriteTimeout: 15 * time.Second,
	}
	go func() {
		log.Info("HTTP server listening", "addr", cfg.HTTPAddr)
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Error("HTTP server error", "err", err)
		}
	}()

	// Kafka consumer (in background; non-fatal if Kafka is unavailable on startup).
	go func() {
		if err := svc.runConsumer(ctx); err != nil {
			log.Error("consumer exited with error", "err", err)
		}
	}()

	<-ctx.Done()

	log.Info("shutting down context-graph builder")
	shutCtx, shutCancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer shutCancel()
	srv.Shutdown(shutCtx)
	svc.producer.Close()
	log.Info("context-graph builder stopped")
}

// ────────────────────────────────────────────────────────────────────────────
// Helpers
// ────────────────────────────────────────────────────────────────────────────

func envOrDefault(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

func queryInt(r *http.Request, param string, defaultVal int) int {
	s := r.URL.Query().Get(param)
	if s == "" {
		return defaultVal
	}
	n, err := strconv.Atoi(s)
	if err != nil || n < 0 {
		return defaultVal
	}
	return n
}
