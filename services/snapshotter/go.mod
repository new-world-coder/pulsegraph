module github.com/pulsegraph/snapshotter

go 1.22

require (
	github.com/google/uuid v1.6.0
	github.com/minio/minio-go/v7 v7.0.69
	github.com/pulsegraph/kafkautil v0.0.0
	github.com/pulsegraph/melt v0.0.0
	github.com/pulsegraph/snapshot v0.0.0
)

require (
	github.com/dustin/go-humanize v1.0.1 // indirect
	github.com/json-iterator/go v1.1.12 // indirect
	github.com/klauspost/compress v1.17.6 // indirect
	github.com/klauspost/cpuid/v2 v2.2.6 // indirect
	github.com/minio/md5-simd v1.1.2 // indirect
	github.com/minio/sha256-simd v1.0.1 // indirect
	github.com/modern-go/concurrent v0.0.0-20180306012644-bacd9c7ef1dd // indirect
	github.com/modern-go/reflect2 v1.0.2 // indirect
	github.com/pierrec/lz4/v4 v4.1.15 // indirect
	github.com/rs/xid v1.5.0 // indirect
	github.com/segmentio/kafka-go v0.4.47 // indirect
	golang.org/x/crypto v0.19.0 // indirect
	golang.org/x/net v0.21.0 // indirect
	golang.org/x/sys v0.17.0 // indirect
	golang.org/x/text v0.14.0 // indirect
	gopkg.in/ini.v1 v1.67.0 // indirect
)

replace github.com/pulsegraph/melt => ../../packages/melt

replace github.com/pulsegraph/kafkautil => ../../packages/kafkautil

replace github.com/pulsegraph/snapshot => ../../packages/snapshot
