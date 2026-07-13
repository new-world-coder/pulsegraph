# RCA Python ML Sidecar

Runs beside the **Java** `java/services/rca-engine` Spring Boot service.

| Process | Port | Role |
|---------|-----:|------|
| Java `rca-engine` | 8095 | Orchestration, rules, merge results |
| This Python sidecar | **8096** | ML / Isolation Forest / LLM hooks |

```bash
# from repo root
cd services/rca-engine
pip install fastapi uvicorn
uvicorn main:app --host 0.0.0.0 --port 8096
```

Java calls `POST {PYTHON_SIDECAR_URL}/v1/analyze` (default `http://localhost:8096`).
