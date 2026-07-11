package snapshot

import "testing"

func TestNewBundleHashes(t *testing.T) {
	cmd := Command{
		IncidentID:  "inc-1",
		TenantID:    "default",
		ServiceName: "checkout",
		Trigger:     TriggerManual,
	}
	b, err := NewBundle(cmd, []byte(`[{"n":1}]`), []byte(`[]`), []byte(`[{"trace":"a"}]`), nil, nil)
	if err != nil {
		t.Fatal(err)
	}
	if b.Manifest.IncidentID != "inc-1" {
		t.Fatalf("incident id: %s", b.Manifest.IncidentID)
	}
	if b.Manifest.Counts["metrics"] != 1 || b.Manifest.Counts["traces"] != 1 {
		t.Fatalf("counts: %+v", b.Manifest.Counts)
	}
	if b.ManifestSHA256() == "" {
		t.Fatal("empty manifest sha")
	}
	if _, ok := b.Objects["manifest.json"]; !ok {
		t.Fatal("missing manifest")
	}
	if ObjectPrefix("default", "inc-1") != "default/inc-1/" {
		t.Fatal(ObjectPrefix("default", "inc-1"))
	}
}

func TestCommandWindow(t *testing.T) {
	cmd := Command{TriggeredAtUnixNs: 1_700_000_000_000_000_000, PreWindowSec: 600, PostWindowSec: 120}
	if cmd.WindowEnd().Sub(cmd.WindowStart()) != 720*1e9 {
		// duration check via seconds
		d := cmd.WindowEnd().Sub(cmd.WindowStart()).Seconds()
		if d != 720 {
			t.Fatalf("window seconds = %v", d)
		}
	}
}
