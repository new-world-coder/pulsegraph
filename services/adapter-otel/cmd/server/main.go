package main

import (
	"bytes"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"os"
	"time"

	"github.com/google/uuid"
	"github.com/pulsegraph/melt"
	"github.com/pulsegraph/melt/otlpjson"
)

const defaultPort = "4318"

func main() {
	port := envOrDefault("PORT", defaultPort)
	gatewayURL := envOrDefault("INGEST_GATEWAY_URL", "http://localhost:8081")
	tenantID := envOrDefault("TENANT_ID", "default")
	logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))

	logger.Info("adapter-otel starting",
		"port", port,
		"ingest_gateway", gatewayURL,
		"description", "OTLP/HTTP JSON receiver → ingest-gateway MeltEnvelope",
	)

	client := &http.Client{Timeout: 10 * time.Second}
	mux := http.NewServeMux()
	mux.HandleFunc("/healthz", func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"status":"ok","service":"adapter-otel"}`))
	})
	mux.HandleFunc("/v1/traces", otlpHandler(logger, client, gatewayURL, tenantID, melt.SignalTrace))
	mux.HandleFunc("/v1/metrics", otlpHandler(logger, client, gatewayURL, tenantID, melt.SignalMetric))
	mux.HandleFunc("/v1/logs", otlpHandler(logger, client, gatewayURL, tenantID, melt.SignalLog))

	logger.Info("listening", "addr", ":"+port)
	if err := http.ListenAndServe(":"+port, mux); err != nil {
		logger.Error("server error", "err", err)
		os.Exit(1)
	}
}

func otlpHandler(logger *slog.Logger, client *http.Client, gatewayURL, tenantID string, signal melt.SignalType) http.HandlerFunc {
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
		ct := r.Header.Get("Content-Type")
		if len(body) == 0 {
			http.Error(w, "empty body", http.StatusBadRequest)
			return
		}
		// MVP: OTLP/JSON only. Protobuf support lands with collector-style decoder.
		if ct != "" && ct != "application/json" && ct != "application/json; charset=utf-8" {
			logger.Warn("unsupported content-type; attempting JSON parse", "content_type", ct)
		}

		service, env, labels, err := extractMeta(signal, body)
		if err != nil {
			logger.Error("otlp parse failed", "signal", signal, "err", err)
			http.Error(w, "invalid otlp json", http.StatusBadRequest)
			return
		}

		envl := &melt.Envelope{
			ID:               uuid.NewString(),
			TenantID:         tenantID,
			SourceVendor:     melt.VendorOTel,
			SignalType:       signal,
			TimestampUnixNs:  time.Now().UnixNano(),
			IngestedAtUnixNs: time.Now().UnixNano(),
			ServiceName:      service,
			Environment:      env,
			Labels:           labels,
			Payload:          json.RawMessage(body),
		}
		if signal == melt.SignalTrace {
			tid, sid, _ := otlpjson.FirstTraceIDs(body)
			envl.TraceID = tid
			envl.SpanID = sid
		}

		if err := forwardToGateway(client, gatewayURL, envl); err != nil {
			logger.Error("forward to ingest-gateway failed", "err", err)
			http.Error(w, "ingest gateway unavailable", http.StatusBadGateway)
			return
		}

		logger.Info("otlp accepted",
			"signal", signal,
			"service", service,
			"envelope_id", envl.ID,
			"bytes", len(body),
		)
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte(`{}`)) // OTLP Export*ServiceResponse empty JSON
	}
}

func extractMeta(signal melt.SignalType, body []byte) (service, env string, labels map[string]string, err error) {
	switch signal {
	case melt.SignalTrace:
		return otlpjson.FirstResourceFromTraces(body)
	case melt.SignalMetric:
		return otlpjson.FirstResourceFromMetrics(body)
	case melt.SignalLog:
		return otlpjson.FirstResourceFromLogs(body)
	default:
		return "unknown", "unknown", map[string]string{}, nil
	}
}

func forwardToGateway(client *http.Client, gatewayURL string, envl *melt.Envelope) error {
	b, err := envl.Marshal()
	if err != nil {
		return err
	}
	req, err := http.NewRequest(http.MethodPost, gatewayURL+"/v1/ingest", bytes.NewReader(b))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-PulseGraph-Tenant", envl.TenantID)
	resp, err := client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 300 {
		body, _ := io.ReadAll(io.LimitReader(resp.Body, 2048))
		return &gatewayError{status: resp.StatusCode, body: string(body)}
	}
	return nil
}

type gatewayError struct {
	status int
	body   string
}

func (e *gatewayError) Error() string {
	return "gateway status " + http.StatusText(e.status) + ": " + e.body
}

func envOrDefault(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
