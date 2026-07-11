package main

import (
	"log/slog"
	"net/http"
	"os"
)

const defaultPort = "8081"

func main() {
	port := envOrDefault("PORT", defaultPort)
	logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	logger.Info("ingest-gateway starting",
		"port", port,
		"description", "Validates and fans out raw telemetry events to Redpanda topics",
	)

	mux := http.NewServeMux()
	mux.HandleFunc("/healthz", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte(`{"status":"ok","service":"ingest-gateway"}`))
	})
	mux.HandleFunc("/ingest", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
			return
		}
		// TODO: validate MELT envelope, route to appropriate Redpanda topic
		logger.Info("ingest request received", "remote", r.RemoteAddr)
		w.WriteHeader(http.StatusAccepted)
		_, _ = w.Write([]byte(`{"accepted":true}`))
	})

	logger.Info("listening", "addr", ":"+port)
	if err := http.ListenAndServe(":"+port, mux); err != nil {
		logger.Error("server error", "err", err)
		os.Exit(1)
	}
}

func envOrDefault(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
