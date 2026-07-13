# adapter-otel (Java / Spring Boot)

OTLP/HTTP ingress to MeltEnvelope then ingest-gateway

```bash
cd java && mvn -pl services/adapter-otel -am spring-boot:run
```

Health: `GET http://localhost:4318/healthz`
