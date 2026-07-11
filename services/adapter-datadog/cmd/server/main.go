package main

import (
	"log/slog"
	"net/http"
	"os"
)

const defaultPort = "8200"

func main() {
	port := envOrDefault("PORT", defaultPort)
	logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	logger.Info("adapter-datadog starting",
		"port", port,
		"description", "Receives Datadog webhook events, normalises to MELT, publishes to Redpanda",
	)

	mux := http.NewServeMux()
	mux.HandleFunc("/healthz", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte(`{"status":"ok","service":"adapter-datadog"}`))
	})
	mux.HandleFunc("/api/v1/intake", func(w http.ResponseWriter, r *http.Request) {
		// TODO: parse Datadog JSON payload, call otel-normalize, publish to Kafka topic raw.events
		logger.Info("received intake request", "method", r.Method, "path", r.URL.Path)
		w.WriteHeader(http.StatusAccepted)
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
