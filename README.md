# PulseGraph

PulseGraph is an AIOps observability platform that ingests telemetry from multiple APM vendors (Datadog, New Relic, Dynatrace, OpenTelemetry), normalises signals into a canonical MELT schema, runs real-time correlation and topology inference, and surfaces incidents with ML-powered root-cause analysis through a unified console UI.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        Adapters (Go)                            │
│  adapter-datadog | adapter-newrelic | adapter-dynatrace | otel  │
└────────────────────────┬────────────────────────────────────────┘
                         │ raw events → Redpanda
                         ▼
              ┌─────────────────────┐
              │   ingest-gateway    │  (Go) validation + fan-out
              └──────────┬──────────┘
                         │ raw.events topic
                         ▼
              ┌─────────────────────┐
              │     normalizer      │  (Go) MELT canonical schema
              └──────────┬──────────┘
                         │ normalised.events topic
              ┌──────────┴──────────┐
              │                     │
              ▼                     ▼
   ┌──────────────────┐   ┌──────────────────────┐
   │   correlator     │   │  topology-builder    │
   │   (Go)           │   │  (Go)                │
   └────────┬─────────┘   └──────────┬───────────┘
            │                        │
            ▼                        ▼
   ┌──────────────────┐   ┌──────────────────────┐
   │   snapshotter    │   │  OpenSearch (graph)  │
   │   (Go → CH/Minio)│   └──────────────────────┘
   └──────────────────┘
            │
            ▼
   ┌──────────────────┐
   │   rca-engine     │  (Python/ML) root-cause analysis
   └──────────────────┘
            │
            ▼
   ┌──────────────────┐       ┌──────────────────┐
   │   query-api      │──────▶│   console (UI)   │
   │   (Go)           │       │   (React/TS)     │
   └──────────────────┘       └──────────────────┘
```

### Data Stores

| Store       | Purpose                              | Default Port |
|-------------|--------------------------------------|-------------|
| Redpanda    | Event streaming (Kafka-compatible)   | 9092 / 9644 |
| ClickHouse  | Time-series metrics & spans          | 8123 / 9000 |
| OpenSearch  | Topology graph & log search          | 9200 / 9600 |
| MinIO       | Snapshot blobs & model artefacts     | 9001 / 9090 |
| Postgres    | Config, metadata, user state         | 5432        |

## Repository Layout

```
pulsegraph/
├── apps/console/          # React + TypeScript UI (Vite)
├── deploy/compose/        # Docker Compose overrides
├── docs/                  # Architecture, requirements, ADRs
├── packages/otel-normalize/ # Shared normalisation helpers
├── proto/                 # Protobuf / schema stubs
├── scripts/               # Dev utility scripts
└── services/
    ├── adapter-datadog/
    ├── adapter-newrelic/
    ├── adapter-dynatrace/
    ├── adapter-otel/
    ├── ingest-gateway/
    ├── normalizer/
    ├── correlator/
    ├── snapshotter/
    ├── topology-builder/
    ├── query-api/
    └── rca-engine/        # Python ML worker
```

## Running Locally

### Prerequisites

- Docker ≥ 24 with Docker Compose v2
- Go ≥ 1.22 (for service development)
- Python ≥ 3.11 (for rca-engine)
- Node ≥ 20 + pnpm ≥ 9 (for console)

### Quick Start

```bash
# 1. Copy env template
cp .env.example .env

# 2. Start all infrastructure
make up

# 3. Bootstrap topics/buckets
make bootstrap

# 4. Check everything is healthy
make ps
```

### Common Make Targets

| Target      | Description                                      |
|-------------|--------------------------------------------------|
| `make up`   | Start infrastructure (docker compose up -d)      |
| `make down` | Stop and remove containers                       |
| `make ps`   | Show container status                            |
| `make bootstrap` | Create Kafka topics and MinIO buckets       |
| `make build` | Build all Go services                           |
| `make test`  | Run all tests                                   |
| `make lint`  | Lint Go, Python, and TypeScript                 |

## Documentation

Start at the **[Docs Index](docs/README.md)**. Key manuals:

| Manual | Description |
|--------|-------------|
| [Oracle Setup](docs/ORACLE_SETUP.md) | Always Free ARM VM, ports, you vs Cursor handoff |
| [Features](docs/FEATURES.md) | Implemented features with competitor citations |
| [User Manual](docs/USER_MANUAL.md) | Day-to-day freeze / graph workflow |
| [Deployment](docs/DEPLOYMENT.md) | Local + VM deploy |
| [Testing](docs/TESTING.md) | Unit & smoke tests |
| [Integration](docs/INTEGRATION.md) | OTel, Datadog, Dynatrace, PagerDuty, Rootly… |
| [Pilot Litmus](docs/PILOT_LITMUS.md) | Customer litmus levels L0–L4 |
| [Operations](docs/OPERATIONS.md) | Health, backups, failures |
| [Security](docs/SECURITY.md) | Pilot security expectations |
| [Requirements](docs/REQUIREMENTS.md) / [HLD](docs/HLD.md) | Product & design |
