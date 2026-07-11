module github.com/pulsegraph/ingest-gateway

go 1.22

require (
	github.com/google/uuid v1.6.0
	github.com/pulsegraph/kafkautil v0.0.0
	github.com/pulsegraph/melt v0.0.0
)

replace github.com/pulsegraph/melt => ../../packages/melt

replace github.com/pulsegraph/kafkautil => ../../packages/kafkautil
