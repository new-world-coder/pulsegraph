# PulseGraph — Java (Spring Boot) Microservices

**Approved stack:** Java 21 · Spring Boot 3.3 · RCA = Java orchestrator + Python sidecar  
**Docs:** [../docs/java/README.md](../docs/java/README.md)

## Modules

- **libs/** — `common`, `melt`, `kafka`, `contextgraph`, `snapshot`
- **services/** — 11 Spring Boot apps (adapters ×4, gateway, normalizer, correlator, topology-builder, snapshotter, rca-engine, query-api)

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
