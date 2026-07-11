package main

import (
	"log/slog"
	"os"
	"os/signal"
	"syscall"
)

func main() {
	logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	logger.Info("normalizer starting",
		"kafka_brokers", envOrDefault("KAFKA_BROKERS", "localhost:9092"),
		"description", "Consumes raw.events topic, normalises to MELT canonical schema, publishes to normalised.events",
	)

	// TODO: initialise Kafka consumer group on topic "raw.events"
	// TODO: for each message, call otel-normalize library, publish to "normalised.events"

	logger.Info("normalizer ready — awaiting messages from raw.events topic")

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit
	logger.Info("normalizer shutting down gracefully")
}

func envOrDefault(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
