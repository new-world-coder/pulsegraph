module github.com/pulsegraph/topology-builder

go 1.22

require (
	github.com/pulsegraph/contextgraph v0.0.0
	github.com/pulsegraph/kafkautil v0.0.0
	github.com/pulsegraph/melt v0.0.0
	github.com/segmentio/kafka-go v0.4.47
)

replace (
	github.com/pulsegraph/contextgraph => ../../packages/contextgraph
	github.com/pulsegraph/kafkautil => ../../packages/kafkautil
	github.com/pulsegraph/melt => ../../packages/melt
)
