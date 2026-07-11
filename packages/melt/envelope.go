package melt

import (
	"encoding/json"
	"fmt"
	"time"
)

// Vendor identifies the upstream observability platform.
type Vendor string

const (
	VendorOTel      Vendor = "otel"
	VendorDatadog   Vendor = "datadog"
	VendorNewRelic  Vendor = "newrelic"
	VendorDynatrace Vendor = "dynatrace"
)

// SignalType is one MELT signal class.
type SignalType string

const (
	SignalMetric SignalType = "metric"
	SignalEvent  SignalType = "event"
	SignalLog    SignalType = "log"
	SignalTrace  SignalType = "trace"
)

// Envelope is the platform-canonical MELT wrapper published on Kafka.
// Payload holds vendor-normalized JSON (OTLP-shaped after adapters).
type Envelope struct {
	ID              string            `json:"id"`
	TenantID        string            `json:"tenant_id"`
	SourceVendor    Vendor            `json:"source_vendor"`
	SignalType      SignalType        `json:"signal_type"`
	TimestampUnixNs int64             `json:"timestamp_unix_ns"`
	IngestedAtUnixNs int64            `json:"ingested_at_unix_ns"`
	ServiceName     string            `json:"service_name"`
	Environment     string            `json:"environment"`
	TraceID         string            `json:"trace_id,omitempty"`
	SpanID          string            `json:"span_id,omitempty"`
	Labels          map[string]string `json:"labels,omitempty"`
	// Payload is OTLP-shaped JSON for the signal (resource + data points / spans / log records).
	Payload json.RawMessage `json:"payload"`
	// Normalized is set by the normalizer when publishing to norm.melt.
	Normalized bool `json:"normalized,omitempty"`
}

// Validate performs lightweight ingest-gateway checks.
func (e *Envelope) Validate() error {
	if e.SourceVendor == "" {
		return fmt.Errorf("source_vendor is required")
	}
	if e.SignalType == "" {
		return fmt.Errorf("signal_type is required")
	}
	switch e.SignalType {
	case SignalMetric, SignalEvent, SignalLog, SignalTrace:
	default:
		return fmt.Errorf("unsupported signal_type %q", e.SignalType)
	}
	if len(e.Payload) == 0 {
		return fmt.Errorf("payload is required")
	}
	if e.TenantID == "" {
		e.TenantID = "default"
	}
	if e.Environment == "" {
		e.Environment = "unknown"
	}
	if e.ServiceName == "" {
		e.ServiceName = "unknown"
	}
	if e.TimestampUnixNs == 0 {
		e.TimestampUnixNs = time.Now().UnixNano()
	}
	if e.IngestedAtUnixNs == 0 {
		e.IngestedAtUnixNs = time.Now().UnixNano()
	}
	return nil
}

// Marshal serializes the envelope to JSON bytes for Kafka.
func (e *Envelope) Marshal() ([]byte, error) {
	return json.Marshal(e)
}

// UnmarshalEnvelope decodes a Kafka message body.
func UnmarshalEnvelope(b []byte) (*Envelope, error) {
	var e Envelope
	if err := json.Unmarshal(b, &e); err != nil {
		return nil, err
	}
	return &e, nil
}
