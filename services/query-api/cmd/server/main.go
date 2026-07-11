package main

import (
	"encoding/json"
	"log/slog"
	"net/http"
	"os"
	"time"
)

const defaultPort = "8090"

func main() {
	port := envOrDefault("PORT", defaultPort)
	logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	logger.Info("query-api starting",
		"port", port,
		"clickhouse_host", envOrDefault("CLICKHOUSE_HOST", "localhost:8123"),
		"opensearch_url", envOrDefault("OPENSEARCH_URL", "http://localhost:9200"),
		"description", "REST/GraphQL gateway exposing incidents, topology graph, and metric queries to the console UI",
	)

	mux := http.NewServeMux()
	mux.HandleFunc("/healthz", healthHandler(logger))
	mux.HandleFunc("/api/v1/incidents", incidentsHandler(logger))
	mux.HandleFunc("/api/v1/topology", topologyHandler(logger))
	mux.HandleFunc("/api/v1/metrics", metricsHandler(logger))

	logger.Info("listening", "addr", ":"+port)
	if err := http.ListenAndServe(":"+port, mux); err != nil {
		logger.Error("server error", "err", err)
		os.Exit(1)
	}
}

func healthHandler(logger *slog.Logger) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, http.StatusOK, map[string]any{"status": "ok", "service": "query-api", "time": time.Now().UTC()})
	}
}

func incidentsHandler(logger *slog.Logger) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		// TODO: query ClickHouse incidents table with filters from query params
		logger.Info("incidents query", "query", r.URL.RawQuery)
		writeJSON(w, http.StatusOK, map[string]any{"incidents": []any{}, "total": 0})
	}
}

func topologyHandler(logger *slog.Logger) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		// TODO: query OpenSearch topology-graph index, return nodes + edges
		logger.Info("topology query", "query", r.URL.RawQuery)
		writeJSON(w, http.StatusOK, map[string]any{"nodes": []any{}, "edges": []any{}})
	}
}

func metricsHandler(logger *slog.Logger) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		// TODO: query ClickHouse metrics table with time-range and label filters
		logger.Info("metrics query", "query", r.URL.RawQuery)
		writeJSON(w, http.StatusOK, map[string]any{"series": []any{}})
	}
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

func envOrDefault(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
