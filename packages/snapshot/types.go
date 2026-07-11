package snapshot

import "time"

// Trigger identifies what caused a freeze.
type Trigger string

const (
	TriggerManual   Trigger = "manual"
	TriggerWebhook  Trigger = "webhook"
	TriggerAnomaly  Trigger = "anomaly"
	TriggerSLOBurn  Trigger = "slo_burn"
	TriggerIncident Trigger = "incident"
)

// Command is published on snapshot.commands to request an incident freeze.
type Command struct {
	IncidentID       string  `json:"incident_id"`
	TenantID         string  `json:"tenant_id"`
	Trigger          Trigger `json:"trigger"`
	ServiceName      string  `json:"service_name"`
	Environment      string  `json:"environment,omitempty"`
	TriggeredAtUnixNs int64  `json:"triggered_at_unix_ns"`
	PreWindowSec     int     `json:"pre_window_sec"`
	PostWindowSec    int     `json:"post_window_sec"`
	Depth            int     `json:"depth"`
}

// Defaults fills missing freeze-window fields (HLD: 10m pre + 2m post, depth 2).
func (c *Command) Defaults() {
	if c.TenantID == "" {
		c.TenantID = "default"
	}
	if c.Trigger == "" {
		c.Trigger = TriggerManual
	}
	if c.TriggeredAtUnixNs == 0 {
		c.TriggeredAtUnixNs = time.Now().UnixNano()
	}
	if c.PreWindowSec <= 0 {
		c.PreWindowSec = 600
	}
	if c.PostWindowSec < 0 {
		c.PostWindowSec = 120
	}
	if c.Depth <= 0 {
		c.Depth = 2
	}
	if c.ServiceName == "" {
		c.ServiceName = "unknown"
	}
}

// WindowStart returns the inclusive start of the freeze window.
func (c *Command) WindowStart() time.Time {
	c.Defaults()
	t := time.Unix(0, c.TriggeredAtUnixNs).UTC()
	return t.Add(-time.Duration(c.PreWindowSec) * time.Second)
}

// WindowEnd returns the exclusive end of the freeze window.
func (c *Command) WindowEnd() time.Time {
	c.Defaults()
	t := time.Unix(0, c.TriggeredAtUnixNs).UTC()
	return t.Add(time.Duration(c.PostWindowSec) * time.Second)
}

// FileRef describes one object inside the freeze bundle.
type FileRef struct {
	Path   string `json:"path"`
	SHA256 string `json:"sha256"`
	Bytes  int64  `json:"bytes"`
}

// Manifest is written to MinIO as manifest.json (immutable evidence index).
type Manifest struct {
	IncidentID        string            `json:"incident_id"`
	TenantID          string            `json:"tenant_id"`
	Trigger           Trigger           `json:"trigger"`
	ServiceName       string            `json:"service_name"`
	Environment       string            `json:"environment,omitempty"`
	TriggeredAtUnixNs int64             `json:"triggered_at_unix_ns"`
	WindowStartRFC3339 string           `json:"window_start"`
	WindowEndRFC3339   string           `json:"window_end"`
	CreatedAtRFC3339  string            `json:"created_at"`
	Degraded          bool              `json:"degraded"`
	DegradedReasons   []string          `json:"degraded_reasons,omitempty"`
	Counts            map[string]int    `json:"counts"`
	Files             []FileRef         `json:"files"`
	Labels            map[string]string `json:"labels,omitempty"`
}

// Ref is published on snapshot.refs after a successful (or degraded) freeze.
type Ref struct {
	IncidentID       string    `json:"incident_id"`
	TenantID         string    `json:"tenant_id"`
	BundleURI        string    `json:"bundle_uri"`
	ManifestSHA256   string    `json:"manifest_sha256"`
	Degraded         bool      `json:"degraded"`
	DegradedReasons  []string  `json:"degraded_reasons,omitempty"`
	CreatedAtUnixNs  int64     `json:"created_at_unix_ns"`
	ServiceName      string    `json:"service_name"`
	Trigger          Trigger   `json:"trigger"`
	Files            []FileRef `json:"files"`
}
