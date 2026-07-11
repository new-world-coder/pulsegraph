package main

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"sync"
	"syscall"
	"time"

	"github.com/google/uuid"
	"github.com/minio/minio-go/v7"
	"github.com/minio/minio-go/v7/pkg/credentials"
	"github.com/pulsegraph/kafkautil"
	"github.com/pulsegraph/melt"
	"github.com/pulsegraph/snapshot"
)

const defaultPort = "8092"

func main() {
	logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	cfg := loadConfig()

	minioClient, err := newMinio(cfg)
	if err != nil {
		logger.Error("minio client", "err", err)
		os.Exit(1)
	}
	ensureBucket(context.Background(), logger, minioClient, cfg.bucket)

	buf := newRingBuffer(cfg.bufferMax)
	refProducer := kafkautil.NewProducer(cfg.brokers, melt.TopicSnapshotRefs)
	defer refProducer.Close()

	engine := &freezeEngine{
		logger:      logger,
		cfg:         cfg,
		buf:         buf,
		minio:       minioClient,
		refProducer: refProducer,
		httpClient:  &http.Client{Timeout: 5 * time.Second},
	}

	ctx, cancel := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer cancel()

	go consumeNorm(ctx, logger, cfg, buf)
	go consumeCommands(ctx, logger, cfg, engine)

	mux := http.NewServeMux()
	mux.HandleFunc("/healthz", func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]any{
			"status":       "ok",
			"service":      "snapshotter",
			"buffer_size":  buf.Len(),
			"minio_bucket": cfg.bucket,
		})
	})
	mux.HandleFunc("/v1/freeze", freezeHandler(logger, engine))
	mux.HandleFunc("/v1/snapshots/", getSnapshotHandler(logger, engine))

	srv := &http.Server{Addr: ":" + cfg.port, Handler: mux}
	go func() {
		logger.Info("snapshotter listening",
			"addr", srv.Addr,
			"bucket", cfg.bucket,
			"topology_url", cfg.topologyURL,
		)
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			logger.Error("http server", "err", err)
			cancel()
		}
	}()

	<-ctx.Done()
	_ = srv.Shutdown(context.Background())
	logger.Info("snapshotter shut down")
}

type config struct {
	port         string
	brokers      []string
	bucket       string
	minioEndpoint string
	minioAccess  string
	minioSecret  string
	topologyURL  string
	bufferMax    int
	useSSL       bool
}

func loadConfig() config {
	endpoint := envOrDefault("MINIO_ENDPOINT", "localhost:9001")
	useSSL := false
	host := endpoint
	if len(endpoint) > 8 && (endpoint[:7] == "http://" || endpoint[:8] == "https://") {
		useSSL = endpoint[:8] == "https://"
		if useSSL {
			host = endpoint[8:]
		} else {
			host = endpoint[7:]
		}
	}
	return config{
		port:          envOrDefault("PORT", defaultPort),
		brokers:       kafkautil.ParseBrokers(envOrDefault("KAFKA_BROKERS", "localhost:9092")),
		bucket:        envOrDefault("MINIO_SNAPSHOTS_BUCKET", "snapshots"),
		minioEndpoint: host,
		minioAccess:   envOrDefault("MINIO_ROOT_USER", "pulsegraph"),
		minioSecret:   envOrDefault("MINIO_ROOT_PASSWORD", "pulsegraph123"),
		topologyURL:  envOrDefault("TOPOLOGY_URL", "http://localhost:8091"),
		bufferMax:     envInt("SNAPSHOT_BUFFER_MAX", 5000),
		useSSL:        useSSL,
	}
}

func newMinio(cfg config) (*minio.Client, error) {
	return minio.New(cfg.minioEndpoint, &minio.Options{
		Creds:  credentials.NewStaticV4(cfg.minioAccess, cfg.minioSecret, ""),
		Secure: cfg.useSSL,
	})
}

func ensureBucket(ctx context.Context, logger *slog.Logger, client *minio.Client, bucket string) {
	exists, err := client.BucketExists(ctx, bucket)
	if err != nil {
		logger.Warn("bucket check failed; will retry on write", "err", err, "bucket", bucket)
		return
	}
	if !exists {
		if err := client.MakeBucket(ctx, bucket, minio.MakeBucketOptions{}); err != nil {
			logger.Warn("make bucket failed", "err", err, "bucket", bucket)
			return
		}
		logger.Info("created minio bucket", "bucket", bucket)
	}
}

// ── Ring buffer of recent MELT envelopes ────────────────────────────────────

type ringBuffer struct {
	mu   sync.RWMutex
	max  int
	data []*melt.Envelope
}

func newRingBuffer(max int) *ringBuffer {
	if max <= 0 {
		max = 5000
	}
	return &ringBuffer{max: max, data: make([]*melt.Envelope, 0, max)}
}

func (r *ringBuffer) Add(e *melt.Envelope) {
	r.mu.Lock()
	defer r.mu.Unlock()
	if len(r.data) >= r.max {
		r.data = r.data[1:]
	}
	r.data = append(r.data, e)
}

func (r *ringBuffer) Len() int {
	r.mu.RLock()
	defer r.mu.RUnlock()
	return len(r.data)
}

func (r *ringBuffer) Window(start, end time.Time, service string) (metrics, logs, traces []json.RawMessage) {
	r.mu.RLock()
	defer r.mu.RUnlock()
	startNs := start.UnixNano()
	endNs := end.UnixNano()
	for _, e := range r.data {
		ts := e.TimestampUnixNs
		if ts == 0 {
			ts = e.IngestedAtUnixNs
		}
		if ts < startNs || ts > endNs {
			continue
		}
		if service != "" && service != "unknown" && e.ServiceName != service && e.ServiceName != "" {
			// Keep related signals later via topology; MVP filters primary service + unlabeled.
			// Also keep envelopes with empty service for degraded capture.
			continue
		}
		raw, _ := json.Marshal(e)
		switch e.SignalType {
		case melt.SignalMetric:
			metrics = append(metrics, raw)
		case melt.SignalLog:
			logs = append(logs, raw)
		case melt.SignalTrace:
			traces = append(traces, raw)
		default:
			logs = append(logs, raw)
		}
	}
	return metrics, logs, traces
}

func consumeNorm(ctx context.Context, logger *slog.Logger, cfg config, buf *ringBuffer) {
	c := kafkautil.NewConsumer(cfg.brokers, melt.TopicNormMelt, "snapshotter-buffer")
	defer c.Close()
	logger.Info("buffering norm.melt for freeze windows")
	_ = c.Run(ctx, func(_ context.Context, _, value []byte) error {
		e, err := melt.UnmarshalEnvelope(value)
		if err != nil {
			logger.Warn("bad envelope in buffer path", "err", err)
			return nil
		}
		buf.Add(e)
		return nil
	})
}

func consumeCommands(ctx context.Context, logger *slog.Logger, cfg config, engine *freezeEngine) {
	c := kafkautil.NewConsumer(cfg.brokers, melt.TopicSnapshotCmds, "snapshotter")
	defer c.Close()
	logger.Info("consuming snapshot.commands")
	_ = c.Run(ctx, func(ctx context.Context, _, value []byte) error {
		var cmd snapshot.Command
		if err := json.Unmarshal(value, &cmd); err != nil {
			logger.Error("bad snapshot command", "err", err)
			return err
		}
		ref, err := engine.Freeze(ctx, cmd)
		if err != nil {
			logger.Error("freeze failed", "err", err, "incident_id", cmd.IncidentID)
			return err
		}
		logger.Info("freeze complete",
			"incident_id", ref.IncidentID,
			"bundle_uri", ref.BundleURI,
			"degraded", ref.Degraded,
			"manifest_sha256", ref.ManifestSHA256,
		)
		return nil
	})
}

// ── Freeze engine ───────────────────────────────────────────────────────────

type freezeEngine struct {
	logger      *slog.Logger
	cfg         config
	buf         *ringBuffer
	minio       *minio.Client
	refProducer *kafkautil.Producer
	httpClient  *http.Client

	mu    sync.Mutex
	lasts map[string]*snapshot.Ref // incident_id → last ref
}

func (e *freezeEngine) Freeze(ctx context.Context, cmd snapshot.Command) (*snapshot.Ref, error) {
	cmd.Defaults()
	if cmd.IncidentID == "" {
		cmd.IncidentID = "inc-" + uuid.NewString()
	}

	var degraded []string
	metrics, logs, traces := e.buf.Window(cmd.WindowStart(), cmd.WindowEnd(), cmd.ServiceName)
	if len(metrics)+len(logs)+len(traces) == 0 {
		degraded = append(degraded, "empty_melt_window")
	}

	topo, err := e.fetchTopology(ctx, cmd.ServiceName, cmd.Depth)
	if err != nil {
		e.logger.Warn("topology fetch failed", "err", err)
		degraded = append(degraded, "topology_unavailable")
		topo = []byte(`{"nodes":[],"edges":[],"error":"topology_unavailable"}`)
	}

	metricsJSON, _ := json.Marshal(metrics)
	logsJSON, _ := json.Marshal(logs)
	tracesJSON, _ := json.Marshal(traces)

	bundle, err := snapshot.NewBundle(cmd, metricsJSON, logsJSON, tracesJSON, topo, degraded)
	if err != nil {
		return nil, err
	}

	prefix := snapshot.ObjectPrefix(cmd.TenantID, cmd.IncidentID)
	for path, body := range bundle.Objects {
		key := prefix + path
		_, err := e.minio.PutObject(ctx, e.cfg.bucket, key, bytes.NewReader(body), int64(len(body)), minio.PutObjectOptions{
			ContentType: "application/json",
		})
		if err != nil {
			return nil, fmt.Errorf("minio put %s: %w", key, err)
		}
	}

	ref := &snapshot.Ref{
		IncidentID:      cmd.IncidentID,
		TenantID:        cmd.TenantID,
		BundleURI:       snapshot.BundleURI(e.cfg.bucket, cmd.TenantID, cmd.IncidentID),
		ManifestSHA256:  bundle.ManifestSHA256(),
		Degraded:        bundle.Manifest.Degraded,
		DegradedReasons: bundle.Manifest.DegradedReasons,
		CreatedAtUnixNs: time.Now().UnixNano(),
		ServiceName:     cmd.ServiceName,
		Trigger:         cmd.Trigger,
		Files:           bundle.Manifest.Files,
	}

	payload, err := json.Marshal(ref)
	if err != nil {
		return nil, err
	}
	key := []byte(cmd.TenantID + "|" + cmd.IncidentID)
	if err := e.refProducer.Publish(ctx, key, payload); err != nil {
		e.logger.Warn("publish snapshot.refs failed", "err", err)
		ref.Degraded = true
		ref.DegradedReasons = append(ref.DegradedReasons, "refs_publish_failed")
	}

	e.mu.Lock()
	if e.lasts == nil {
		e.lasts = map[string]*snapshot.Ref{}
	}
	e.lasts[cmd.IncidentID] = ref
	e.mu.Unlock()
	return ref, nil
}

func (e *freezeEngine) fetchTopology(ctx context.Context, service string, depth int) ([]byte, error) {
	url := fmt.Sprintf("%s/v1/graph/subgraph?service=%s&depth=%d", e.cfg.topologyURL, service, depth)
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, err
	}
	resp, err := e.httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	body, err := io.ReadAll(io.LimitReader(resp.Body, 8<<20))
	if err != nil {
		return nil, err
	}
	if resp.StatusCode >= 300 {
		return nil, fmt.Errorf("topology status %d: %s", resp.StatusCode, string(body))
	}
	return body, nil
}

func (e *freezeEngine) GetRef(incidentID string) (*snapshot.Ref, bool) {
	e.mu.Lock()
	defer e.mu.Unlock()
	r, ok := e.lasts[incidentID]
	return r, ok
}

func freezeHandler(logger *slog.Logger, engine *freezeEngine) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
			return
		}
		var cmd snapshot.Command
		if err := json.NewDecoder(io.LimitReader(r.Body, 1<<20)).Decode(&cmd); err != nil && err != io.EOF {
			http.Error(w, "invalid json", http.StatusBadRequest)
			return
		}
		cmd.Defaults()
		if cmd.IncidentID == "" {
			cmd.IncidentID = "inc-" + uuid.NewString()
		}
		if cmd.Trigger == "" {
			cmd.Trigger = snapshot.TriggerManual
		}

		// Synchronous freeze for interactive MVP. Async path: publish to snapshot.commands.
		ref, err := engine.Freeze(r.Context(), cmd)
		if err != nil {
			logger.Error("manual freeze failed", "err", err)
			http.Error(w, err.Error(), http.StatusBadGateway)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusAccepted)
		_ = json.NewEncoder(w).Encode(ref)
	}
}

func getSnapshotHandler(logger *slog.Logger, engine *freezeEngine) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		id := r.URL.Path[len("/v1/snapshots/"):]
		if id == "" {
			http.Error(w, "incident id required", http.StatusBadRequest)
			return
		}
		ref, ok := engine.GetRef(id)
		if !ok {
			http.Error(w, "not found", http.StatusNotFound)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(ref)
		_ = logger
	}
}

func envOrDefault(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

func envInt(key string, fallback int) int {
	v := os.Getenv(key)
	if v == "" {
		return fallback
	}
	var n int
	if _, err := fmt.Sscanf(v, "%d", &n); err != nil || n <= 0 {
		return fallback
	}
	return n
}
