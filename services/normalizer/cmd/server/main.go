package main

import (
	"context"
	"log/slog"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/pulsegraph/kafkautil"
	"github.com/pulsegraph/melt"
)

func main() {
	logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	brokers := kafkautil.ParseBrokers(envOrDefault("KAFKA_BROKERS", "localhost:9092"))
	inTopic := envOrDefault("RAW_TOPIC", melt.TopicRawMelt)
	outTopic := envOrDefault("NORM_TOPIC", melt.TopicNormMelt)
	group := envOrDefault("CONSUMER_GROUP", "normalizer")

	producer := kafkautil.NewProducer(brokers, outTopic)
	defer producer.Close()
	consumer := kafkautil.NewConsumer(brokers, inTopic, group)
	defer consumer.Close()

	ctx, cancel := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer cancel()

	logger.Info("normalizer starting",
		"brokers", brokers,
		"in_topic", inTopic,
		"out_topic", outTopic,
		"group", group,
	)

	err := consumer.Run(ctx, func(ctx context.Context, key, value []byte) error {
		envl, err := melt.UnmarshalEnvelope(value)
		if err != nil {
			logger.Error("bad envelope", "err", err)
			return err
		}
		normalize(envl)
		out, err := envl.Marshal()
		if err != nil {
			return err
		}
		pubKey := key
		if len(pubKey) == 0 {
			pubKey = []byte(envl.TenantID + "|" + envl.ServiceName)
		}
		if err := producer.Publish(ctx, pubKey, out); err != nil {
			logger.Error("publish norm.melt failed", "err", err, "envelope_id", envl.ID)
			return err
		}
		logger.Info("normalized",
			"envelope_id", envl.ID,
			"signal", envl.SignalType,
			"service", envl.ServiceName,
			"vendor", envl.SourceVendor,
		)
		return nil
	})
	if err != nil && ctx.Err() == nil {
		logger.Error("consumer exited", "err", err)
		os.Exit(1)
	}
	logger.Info("normalizer shut down")
}

// normalize applies canonical labels and marks the envelope as platform-normalized.
// Vendor adapters already map to OTel-shaped payloads; this stage owns schema hygiene.
func normalize(e *melt.Envelope) {
	if e.Labels == nil {
		e.Labels = map[string]string{}
	}
	e.Labels["pulsegraph.signal"] = string(e.SignalType)
	e.Labels["pulsegraph.vendor"] = string(e.SourceVendor)
	e.Labels["pulsegraph.normalized_at"] = time.Now().UTC().Format(time.RFC3339Nano)
	if e.ServiceName == "" {
		e.ServiceName = "unknown"
	}
	if e.Environment == "" {
		e.Environment = "unknown"
	}
	e.Normalized = true
}

func envOrDefault(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
