package main

import (
	"log/slog"
	"net/http"
	"os"
)

const defaultPort = "8201"

func main() {
	port := envOrDefault("PORT", defaultPort)
	logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	logger.Info("adapter-newrelic starting",
		"port", port,
		"description", "Receives New Relic NR Events / NRDB webhooks, normalises to MELT, publishes to Redpanda",
	)

	mux := http.NewServeMux()
	mux.HandleFunc("/healthz", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte(`{"status":"ok","service":"adapter-newrelic"}`))
	})
	mux.HandleFunc("/api/v1/events", func(w http.ResponseWriter, r *http.Request) {
		// TODO: parse New Relic event payload, normalise, publish to raw.events topic
		logger.Info("received events request", "method", r.Method, "path", r.URL.Path)
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
