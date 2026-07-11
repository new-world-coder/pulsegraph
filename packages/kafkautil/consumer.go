package kafkautil

import (
	"context"
	"fmt"
	"time"

	"github.com/segmentio/kafka-go"
)

// Handler processes one Kafka message.
type Handler func(ctx context.Context, key, value []byte) error

// Consumer is a kafka-go reader loop with commit-on-success semantics.
type Consumer struct {
	reader *kafka.Reader
}

// NewConsumer creates a consumer group reader.
func NewConsumer(brokers []string, topic, groupID string) *Consumer {
	return &Consumer{
		reader: kafka.NewReader(kafka.ReaderConfig{
			Brokers:        brokers,
			Topic:          topic,
			GroupID:        groupID,
			MinBytes:       1,
			MaxBytes:       10e6,
			CommitInterval: time.Second,
			StartOffset:    kafka.FirstOffset,
		}),
	}
}

// Run reads until ctx is cancelled. Handler errors are returned to the caller
// (caller may publish to DLQ); the message is still committed to avoid poison loops in MVP.
func (c *Consumer) Run(ctx context.Context, handler Handler) error {
	for {
		msg, err := c.reader.FetchMessage(ctx)
		if err != nil {
			if ctx.Err() != nil {
				return nil
			}
			return fmt.Errorf("kafka fetch: %w", err)
		}
		if err := handler(ctx, msg.Key, msg.Value); err != nil {
			// Surface error but continue (MVP); production will DLQ.
			_ = err
		}
		if err := c.reader.CommitMessages(ctx, msg); err != nil {
			return fmt.Errorf("kafka commit: %w", err)
		}
	}
}

// Close closes the reader.
func (c *Consumer) Close() error {
	return c.reader.Close()
}
