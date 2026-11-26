# PulseGraph — Java (Spring Boot) Microservices

**Approved stack:** Java 21 · Spring Boot 3.3 · RCA = Java orchestrator + Python sidecar  
**Docs:** [../docs/java/README.md](../docs/java/README.md)

## Modules

- **libs/** — `common`, `melt`, `kafka`, `contextgraph`, `snapshot`, `compliance`
- **services/** — 13 Spring Boot apps (adapters ×4, gateway, normalizer, correlator, topology-builder, snapshotter, rca-engine, query-api, **compliance-guardian**, **war-room**)

The **compliance-guardian** service is the Compliant Agent control plane (agent registry, autonomy
policy, human approvals, hash-chained audit, kill switch, regulatory evidence packs). See
[`services/compliance-guardian/README.md`](services/compliance-guardian/README.md) and
[`../docs/COMPLIANT_AGENT.md`](../docs/COMPLIANT_AGENT.md).

## Build

```bash
cd java
./mvnw -q -DskipTests package   # after wrapper is generated, or use system mvn
mvn -q -DskipTests package
```

## Run (examples)

```bash
# Requires Redpanda etc. (repo root: make up && make bootstrap)
mvn -pl services/ingest-gateway -am spring-boot:run
mvn -pl services/adapter-otel -am spring-boot:run
mvn -pl services/rca-engine -am spring-boot:run
# Python sidecar (existing):
cd ../services/rca-engine && python main.py   # or uvicorn — see sidecar README
```

## Consumer groups

Java services use group IDs prefixed with `java-` during dual-run with Go (see `application.yml` in each service).
