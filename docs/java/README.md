# PulseGraph Java Microservices Rewrite — Documentation Index

**Status:** Design approved · **Spring Boot scaffold started** under `/java`  
**Source of truth (current):** Go + Python RCA sidecar + React console  
**Target:** Java microservices (Spring Boot 3 / Java 21) parity fork + Python ML sidecar for RCA

> Scaffold lives in `java/`. Go services remain until cutover. Consumer groups use `java-*` prefix for dual-run.

## Documents in this set

| # | Document | Contents |
|---|----------|----------|
| 1 | [MICROSERVICES_INVENTORY.md](MICROSERVICES_INVENTORY.md) | **Count**, each service’s responsibility, Go→Java mapping |
| 2 | [COMMUNICATION_PATTERNS.md](COMMUNICATION_PATTERNS.md) | Sync vs async, topics, protocols, failure modes |
| 3 | [HIGH_LEVEL_ARCHITECTURE.md](HIGH_LEVEL_ARCHITECTURE.md) | HLD, C4 context/container, deployment view |
| 4 | [SEQUENCE_AND_INTERACTION.md](SEQUENCE_AND_INTERACTION.md) | Sequence & interaction diagrams (ingest, freeze, war room, RCA) |
| 5 | [JAVA_STACK_AND_REPO_LAYOUT.md](JAVA_STACK_AND_REPO_LAYOUT.md) | Recommended Java stack, monorepo layout, shared libs |

## Related existing docs

- [../HLD.md](../HLD.md) — original HLD (language-agnostic intent)
- [../ARCHITECTURE.md](../ARCHITECTURE.md) — Go-era component detail
- [../adr/0002-event-driven-microservices.md](../adr/0002-event-driven-microservices.md)
- [../CONTEXT_GRAPH.md](../CONTEXT_GRAPH.md)
- [../CONSOLE_UX.md](../CONSOLE_UX.md) — React War Room (remains TypeScript)

## Headline numbers

| Category | Count |
|----------|------:|
| **Backend microservices (Java target)** | **11** |
| Shared Java libraries (not services) | 4–5 |
| Frontend app (stays React/TS) | 1 |
| Data-plane infra components | 5 (Redpanda, ClickHouse, OpenSearch, MinIO, Postgres) |
