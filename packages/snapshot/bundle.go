package snapshot

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"sort"
	"time"
)

// Bundle is the in-memory freeze payload before object-store upload.
type Bundle struct {
	Manifest Manifest
	Objects  map[string][]byte // relative path → bytes
}

// NewBundle builds a bundle from freeze inputs.
func NewBundle(
	cmd Command,
	metricsJSON, logsJSON, tracesJSON, topologyJSON []byte,
	degradedReasons []string,
) (*Bundle, error) {
	cmd.Defaults()
	objects := map[string][]byte{
		"metrics.json":           ensureJSONArray(metricsJSON),
		"logs.json":              ensureJSONArray(logsJSON),
		"traces.json":            ensureJSONArray(tracesJSON),
		"topology/subgraph.json": ensureJSONObject(topologyJSON),
	}

	paths := make([]string, 0, len(objects))
	for p := range objects {
		paths = append(paths, p)
	}
	sort.Strings(paths)

	files := make([]FileRef, 0, len(paths)+1)
	for _, p := range paths {
		b := objects[p]
		files = append(files, FileRef{
			Path:   p,
			SHA256: HashBytes(b),
			Bytes:  int64(len(b)),
		})
	}

	m := Manifest{
		IncidentID:         cmd.IncidentID,
		TenantID:           cmd.TenantID,
		Trigger:            cmd.Trigger,
		ServiceName:        cmd.ServiceName,
		Environment:        cmd.Environment,
		TriggeredAtUnixNs:  cmd.TriggeredAtUnixNs,
		WindowStartRFC3339: cmd.WindowStart().Format(time.RFC3339Nano),
		WindowEndRFC3339:   cmd.WindowEnd().Format(time.RFC3339Nano),
		CreatedAtRFC3339:   time.Now().UTC().Format(time.RFC3339Nano),
		Degraded:           len(degradedReasons) > 0,
		DegradedReasons:    append([]string(nil), degradedReasons...),
		Counts: map[string]int{
			"metrics": countJSONArray(objects["metrics.json"]),
			"logs":    countJSONArray(objects["logs.json"]),
			"traces":  countJSONArray(objects["traces.json"]),
		},
		Files: files,
		Labels: map[string]string{
			"pulsegraph.snapshot_version": "1",
		},
	}

	manifestBytes, err := json.MarshalIndent(m, "", "  ")
	if err != nil {
		return nil, fmt.Errorf("marshal manifest: %w", err)
	}
	objects["manifest.json"] = manifestBytes
	m.Files = append([]FileRef{{
		Path:   "manifest.json",
		SHA256: HashBytes(manifestBytes),
		Bytes:  int64(len(manifestBytes)),
	}}, files...)
	manifestBytes, err = json.MarshalIndent(m, "", "  ")
	if err != nil {
		return nil, err
	}
	objects["manifest.json"] = manifestBytes
	m.Files[0].SHA256 = HashBytes(manifestBytes)
	m.Files[0].Bytes = int64(len(manifestBytes))

	return &Bundle{Manifest: m, Objects: objects}, nil
}

// ManifestSHA256 returns the hash of the final manifest.json object bytes.
func (b *Bundle) ManifestSHA256() string {
	return HashBytes(b.Objects["manifest.json"])
}

// ObjectPrefix returns the MinIO key prefix for a tenant/incident bundle.
func ObjectPrefix(tenantID, incidentID string) string {
	return fmt.Sprintf("%s/%s/", tenantID, incidentID)
}

// BundleURI builds an s3-style URI for the bundle root.
func BundleURI(bucket, tenantID, incidentID string) string {
	return fmt.Sprintf("s3://%s/%s", bucket, ObjectPrefix(tenantID, incidentID))
}

// HashBytes returns hex-encoded SHA-256.
func HashBytes(b []byte) string {
	sum := sha256.Sum256(b)
	return hex.EncodeToString(sum[:])
}

func ensureJSONArray(b []byte) []byte {
	if len(b) == 0 {
		return []byte("[]")
	}
	return b
}

func ensureJSONObject(b []byte) []byte {
	if len(b) == 0 {
		return []byte(`{"nodes":[],"edges":[]}`)
	}
	return b
}

func countJSONArray(b []byte) int {
	var arr []json.RawMessage
	if err := json.Unmarshal(b, &arr); err != nil {
		return 0
	}
	return len(arr)
}
