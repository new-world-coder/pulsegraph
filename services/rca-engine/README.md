# rca-engine

Python ML worker that consumes incident records from Kafka, runs graph-based root-cause analysis using historical topology snapshots and metric anomaly detection, and publishes structured RCA hypotheses back to Kafka and the query-api.

## Architecture

```
Kafka: incidents topic
         │
         ▼
  analyze_incident()
         │
    ┌────┴────────────────────────┐
    │                             │
    ▼                             ▼
MinIO snapshot              ClickHouse metrics
(topology at T)             (anomaly window)
    │                             │
    └────────────┬────────────────┘
                 ▼
         RCA hypothesis
         (root service,
          confidence score,
          contributing factors)
                 │
         ┌───────┴──────────┐
         ▼                  ▼
   Kafka: rca.results   ClickHouse
                        rca.results table
```

## Running

```bash
# Development (direct Python)
python main.py

# With FastAPI health server
uvicorn main:app --host 0.0.0.0 --port 8095

# Via virtual environment
python -m venv .venv
source .venv/bin/activate
pip install -e ".[dev]"
python main.py
```

## Testing

```bash
pytest tests/ -v
```

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `KAFKA_BROKERS` | `localhost:9092` | Redpanda broker addresses |
| `KAFKA_GROUP_ID` | `rca-engine` | Consumer group |
| `MINIO_ENDPOINT` | `http://localhost:9001` | MinIO S3 endpoint |
| `CLICKHOUSE_HOST` | `localhost` | ClickHouse host |
| `LOG_LEVEL` | `INFO` | Log level |
| `LOG_FORMAT` | `json` | `json` or `text` |

## API Endpoints (FastAPI)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/healthz` | Health check |
| `GET` | `/api/v1/rca/{incident_id}` | Fetch RCA result for incident |

## Development Notes

- The `analyze_incident()` function in `main.py` is the integration point for the ML pipeline.
- Planned model: Isolation Forest for anomaly scoring + graph traversal for upstream attribution.
- Model artefacts will be stored in MinIO bucket `models` and loaded at startup.
