package main

import (
	"log/slog"
	"net/http"
	"os"
)

const defaultPort = "4318" // standard OTLP HTTP port

func main() {
	port := envOrDefault("PORT", defaultPort)
	logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	logger.Info("adapter-otel starting",
		"port", port,
		"description", "Receives OTLP/HTTP traces, metrics and logs; publishes to Redpanda raw.events topic",
	)

	mux := http.NewServeMux()
	mux.HandleFunc("/healthz", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte(`{"status":"ok","service":"adapter-otel"}`))
	})
	// OTLP HTTP endpoints
	mux.HandleFunc("/v1/traces", otlpHandler(logger, "traces"))
	mux.HandleFunc("/v1/metrics", otlpHandler(logger, "metrics"))
	mux.HandleFunc("/v1/logs", otlpHandler(logger, "logs"))

	logger.Info("listening", "addr", ":"+port)
	if err := http.ListenAndServe(":"+port, mux); err != nil {
		logger.Error("server error", "err", err)
		os.Exit(1)
	}
}

func otlpHandler(logger *slog.Logger, signalType string) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		// TODO: decode OTLP protobuf body, publish MeltEnvelope to raw.events Kafka topic
		logger.Info("received OTLP signal", "signal", signalType, "content-type", r.Header.Get("Content-Type"))
		w.WriteHeader(http.StatusOK)
	}
}

func envOrDefault(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
