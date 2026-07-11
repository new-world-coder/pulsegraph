package main

import (
	"log/slog"
	"os"
	"os/signal"
	"syscall"
)

func main() {
	logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	logger.Info("snapshotter starting",
		"kafka_brokers", envOrDefault("KAFKA_BROKERS", "localhost:9092"),
		"minio_endpoint", envOrDefault("MINIO_ENDPOINT", "http://localhost:9001"),
		"clickhouse_host", envOrDefault("CLICKHOUSE_HOST", "localhost:8123"),
		"description", "Periodically snapshots topology and metric state to MinIO and ClickHouse for RCA replay",
	)

	// TODO: subscribe to normalised.events and incidents topics
	// TODO: on configurable interval, serialise current service graph state
	// TODO: compress and write snapshot blobs to MinIO bucket "snapshots"
	// TODO: write snapshot metadata record to ClickHouse table snapshots.records

	logger.Info("snapshotter ready — periodic snapshot loop will start here")

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit
	logger.Info("snapshotter shutting down gracefully")
}

func envOrDefault(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
