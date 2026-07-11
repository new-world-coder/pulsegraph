package main

import (
	"log/slog"
	"os"
	"os/signal"
	"syscall"
)

func main() {
	logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	logger.Info("correlator starting",
		"kafka_brokers", envOrDefault("KAFKA_BROKERS", "localhost:9092"),
		"description", "Consumes normalised.events, groups related signals into incidents, publishes to incidents topic",
	)

	// TODO: initialise Kafka consumer group on topic "normalised.events"
	// TODO: implement sliding-window correlation algorithm (time + service + error-type grouping)
	// TODO: publish IncidentRecord to "incidents" topic
	// TODO: write incident summaries to ClickHouse for historical queries

	logger.Info("correlator ready — awaiting messages from normalised.events topic")

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit
	logger.Info("correlator shutting down gracefully")
}

func envOrDefault(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
