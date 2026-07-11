package melt

// Canonical Redpanda / Kafka topic names (ADR-0002 pipeline).
const (
	TopicRawMelt         = "raw.melt"
	TopicRawMeltDLQ      = "raw.melt.dlq"
	TopicNormMelt        = "norm.melt"
	TopicNormMeltDLQ     = "norm.melt.dlq"
	TopicCorrEvents      = "corr.events"
	TopicSnapshotCmds    = "snapshot.commands"
	TopicSnapshotRefs    = "snapshot.refs"
	TopicRCAResults      = "rca.results"
	TopicTopologyChanges = "topology.changes"
	TopicIncidents       = "incidents"
)

// PipelineTopics are created by scripts/bootstrap.sh for local/dev.
var PipelineTopics = []string{
	TopicRawMelt,
	TopicRawMeltDLQ,
	TopicNormMelt,
	TopicNormMeltDLQ,
	TopicCorrEvents,
	TopicSnapshotCmds,
	TopicSnapshotRefs,
	TopicRCAResults,
	TopicTopologyChanges,
	TopicIncidents,
}
