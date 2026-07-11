# Operations Manual

**Audience:** Operator of a PulseGraph pilot instance  
**Related:** [DEPLOYMENT.md](DEPLOYMENT.md) · [ORACLE_SETUP.md](ORACLE_SETUP.md) · [TESTING.md](TESTING.md)

---

## 1. Daily health checks

```bash
docker compose ps
curl -sf localhost:8081/healthz && echo gateway_ok
curl -sf localhost:4318/healthz && echo otel_ok
curl -sf localhost:8091/healthz && echo graph_ok
curl -sf localhost:8092/healthz && echo snap_ok
```

Optional: `rpk topic list` inside Redpanda container; MinIO console object count.

---

## 2. Logs

```bash
make logs                          # compose infra
# per service stdout / journalctl -u pulsegraph-*
```

Look for: Kafka publish errors, MinIO put failures, topology fetch failures, consumer lag (future metric).

---

## 3. Common incidents (of the platform itself)

| Symptom | Likely cause | Action |
|---------|--------------|--------|
| Ingest 502 | Gateway down / wrong `INGEST_GATEWAY_URL` | Restart gateway; check adapter env |
| Freeze degraded `empty_melt_window` | No recent `norm.melt` | Send OTLP; check normalizer |
| Freeze degraded `topology_unavailable` | topology-builder down | Restart; check `TOPOLOGY_URL` |
| OpenSearch container unhealthy | RAM pressure on small VM | Lower heap / stop unused containers |
| Disk full | MinIO snapshots + CH | Prune old bundles; expand block volume |

---

## 4. Backup & restore (pilot)

**Backup MinIO snapshots**

```bash
# Example: mc mirror to OCI Object Storage or local tarball
mc mirror local/snapshots ./backup/snapshots-$(date +%F)
```

**Compose volumes** — document host paths; snapshot with VM boot volume backup in OCI if needed.

**Restore** — recreate bucket, `mc cp` objects back; SnapshotRef Kafka history is best-effort (refs also in snapshotter memory until restart).

---

## 5. Security ops

- Rotate `MINIO_ROOT_PASSWORD` / Postgres password after pilot kickoff.  
- Restrict Security List SSH to operator IPs.  
- Before customer traffic: put basic auth or mTLS on Caddy for `/v1/freeze`.  
- Do not commit `.env`.

---

## 6. Scaling knobs (single VM)

| Pressure | Knob |
|----------|------|
| OpenSearch heap | `OPENSEARCH_JAVA_OPTS` |
| Snapshot buffer | `SNAPSHOT_BUFFER_MAX` |
| Redpanda memory | compose command `--memory=` |
| Too many containers | Run CH/OS only when needed for later phases |

Horizontal scale / K8s: explicitly **out of scope** until multi-customer.

---

## 7. On-call for PulseGraph itself

Pilot: founder/operator phone.  
Pager: optional Uptime Kuma / OCI Monitoring on `https://pilot/healthz` proxies.
