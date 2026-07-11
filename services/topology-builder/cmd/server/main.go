package main

import (
	"log/slog"
	"os"
	"os/signal"
	"syscall"
)

func main() {
	logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	logger.Info("topology-builder starting",
		"kafka_brokers", envOrDefault("KAFKA_BROKERS", "localhost:9092"),
		"opensearch_url", envOrDefault("OPENSEARCH_URL", "http://localhost:9200"),
		"description", "Builds and maintains the live service dependency graph from trace spans; writes to OpenSearch",
	)

	// TODO: consume normalised.events topic, extract span parent-child relationships
	// TODO: maintain an in-memory adjacency list of (caller → callee) edges
	// TODO: upsert node and edge documents to OpenSearch index "topology-graph"
	// TODO: publish topology.updated events to topology.changes topic

	logger.Info("topology-builder ready — graph construction loop will start here")

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit
	logger.Info("topology-builder shutting down gracefully")
}

func envOrDefault(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
