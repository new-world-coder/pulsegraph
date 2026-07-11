package main

import (
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"os"
	"time"

	"github.com/google/uuid"
	"github.com/pulsegraph/kafkautil"
	"github.com/pulsegraph/melt"
)

const defaultPort = "8081"

func main() {
	port := envOrDefault("PORT", defaultPort)
	brokers := kafkautil.ParseBrokers(envOrDefault("KAFKA_BROKERS", "localhost:9092"))
	topic := envOrDefault("RAW_TOPIC", melt.TopicRawMelt)
	logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))

	producer := kafkautil.NewProducer(brokers, topic)
	defer producer.Close()

	logger.Info("ingest-gateway starting",
		"port", port,
		"brokers", brokers,
		"topic", topic,
	)

	mux := http.NewServeMux()
	mux.HandleFunc("/healthz", func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"status":"ok","service":"ingest-gateway"}`))
	})
	mux.HandleFunc("/v1/ingest", ingestHandler(logger, producer))
	// Back-compat stub path from early scaffold
	mux.HandleFunc("/ingest", ingestHandler(logger, producer))

	logger.Info("listening", "addr", ":"+port)
	if err := http.ListenAndServe(":"+port, mux); err != nil {
		logger.Error("server error", "err", err)
		os.Exit(1)
	}
}

func ingestHandler(logger *slog.Logger, producer *kafkautil.Producer) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
			return
		}
		body, err := io.ReadAll(io.LimitReader(r.Body, 16<<20))
		if err != nil {
			http.Error(w, "read body", http.StatusBadRequest)
			return
		}

		envl, err := melt.UnmarshalEnvelope(body)
		if err != nil {
			http.Error(w, "invalid melt envelope json", http.StatusBadRequest)
			return
		}
		if tenant := r.Header.Get("X-PulseGraph-Tenant"); tenant != "" && envl.TenantID == "" {
			envl.TenantID = tenant
		}
		if envl.ID == "" {
			envl.ID = uuid.NewString()
		}
		envl.IngestedAtUnixNs = time.Now().UnixNano()
		if err := envl.Validate(); err != nil {
			http.Error(w, err.Error(), http.StatusBadRequest)
			return
		}

		payload, err := envl.Marshal()
		if err != nil {
			http.Error(w, "marshal", http.StatusInternalServerError)
			return
		}
		key := []byte(envl.TenantID + "|" + envl.ServiceName)
		if err := producer.Publish(r.Context(), key, payload); err != nil {
			logger.Error("kafka publish failed", "err", err, "envelope_id", envl.ID)
			http.Error(w, "kafka unavailable", http.StatusServiceUnavailable)
			return
		}

		logger.Info("accepted",
			"envelope_id", envl.ID,
			"vendor", envl.SourceVendor,
			"signal", envl.SignalType,
			"service", envl.ServiceName,
		)
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusAccepted)
		_ = json.NewEncoder(w).Encode(map[string]any{
			"accepted":    true,
			"envelope_id": envl.ID,
			"topic":       melt.TopicRawMelt,
		})
	}
}

func envOrDefault(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
