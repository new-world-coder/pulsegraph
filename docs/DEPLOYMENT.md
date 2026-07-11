# Deployment Manual

**Audience:** Platform engineers deploying PulseGraph locally or on Oracle Cloud  
**Related:** [ORACLE_SETUP.md](ORACLE_SETUP.md) · [OPERATIONS.md](OPERATIONS.md) · [TESTING.md](TESTING.md)

---

## 1. Architecture deployed

```text
[OTel / collectors] → adapter-otel :4318
                         ↓ HTTP
                   ingest-gateway :8081 → Kafka raw.melt
                         ↓
                    normalizer → Kafka norm.melt
                         ↓              ↓
              topology-builder :8091   snapshotter :8092
              (Context Graph)           ↓ MinIO snapshots/
                                    snapshot.refs (Kafka)
```

**Infrastructure (Compose):** Redpanda, ClickHouse, OpenSearch, MinIO, Postgres.

---

## 2. Prerequisites

| Requirement | Notes |
|-------------|-------|
| Docker + Compose v2 | Required |
| Go 1.22+ | Build/run services |
| Python 3.11+ | Optional (rca-engine later) |
| 8 GB RAM min / 16–24 GB preferred | OpenSearch + Redpanda are hungry |
| ARM64 or amd64 | Oracle Always Free = **ARM** — use ARM images |

---

## 3. Local deployment

```bash
git clone https://github.com/new-world-coder/pulsegraph.git
cd pulsegraph
cp .env.example .env

make up
make bootstrap
make tidy          # optional; resolves Go module sums
```

### Run MVP process group

```bash
# Terminal A — or use scripts/run-mvp.sh for gateway+normalizer+adapter
make run-mvp
```

Also start Context Graph + snapshotter:

```bash
# Terminal B
cd services/topology-builder && go run ./cmd/server

# Terminal C
cd services/snapshotter && go run ./cmd/server
```

### Verify

```bash
make ps
make smoke-ingest
make smoke-snapshot   # requires snapshotter (+ ideally topology-builder)
```

---

## 4. Oracle Cloud VM deployment

Complete [ORACLE_SETUP.md](ORACLE_SETUP.md) first (account, A1 VM, SSH).

### 4.1 Host packages (Ubuntu ARM example)

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl git jq
# Install Docker Engine + compose plugin per Docker’s Ubuntu ARM docs
sudo usermod -aG docker $USER   # re-login
```

### 4.2 App install

```bash
git clone https://github.com/new-world-coder/pulsegraph.git
cd pulsegraph
cp .env.example .env
# Edit secrets: MINIO_*, POSTGRES_*, etc.

make up
make bootstrap
```

Install Go 1.22+ for ARM, then:

```bash
make tidy
# run services under systemd or a process supervisor — see §6
```

### 4.3 Reverse proxy (recommended)

Put **Caddy** or nginx on `:443` terminating TLS; proxy:

| Path / host | Upstream |
|-------------|----------|
| `/v1/traces` etc. or `otel.` host | `127.0.0.1:4318` |
| `/v1/freeze` | `127.0.0.1:8092` |
| `/v1/graph` | `127.0.0.1:8091` |
| `/` console (later) | `127.0.0.1:3000` |

Do **not** publish Redpanda/ClickHouse/OpenSearch/MinIO/Postgres ports on the VCN security list.

---

## 5. Environment variables

See `.env.example`. Critical:

| Variable | Used by |
|----------|---------|
| `KAFKA_BROKERS` | All stream services (`localhost:9092`) |
| `MINIO_ENDPOINT` | snapshotter (`http://localhost:9001`) |
| `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` | MinIO + snapshotter |
| `MINIO_SNAPSHOTS_BUCKET` | `snapshots` |
| `TOPOLOGY_URL` | snapshotter → `http://localhost:8091` |
| `INGEST_GATEWAY_URL` | adapter-otel → `http://localhost:8081` |
| `PORT` | Per-service override |

---

## 6. Process supervision (pilot)

Minimal `systemd` unit sketch per service (example `pulsegraph-snapshotter.service`):

```ini
[Unit]
Description=PulseGraph snapshotter
After=docker.service network.target

[Service]
WorkingDirectory=/home/ubuntu/pulsegraph/services/snapshotter
EnvironmentFile=/home/ubuntu/pulsegraph/.env
ExecStart=/usr/local/go/bin/go run ./cmd/server
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

Prefer compiled binaries in `/opt/pulsegraph/bin` for anything beyond a weekend pilot:

```bash
cd services/snapshotter && go build -o /opt/pulsegraph/bin/snapshotter ./cmd/server
```

---

## 7. Ports reference

| Port | Component |
|------|-----------|
| 4318 | adapter-otel (OTLP/HTTP) |
| 8081 | ingest-gateway |
| 8091 | topology-builder |
| 8092 | snapshotter |
| 8090 | query-api (stub) |
| 3000 | console (dev) |
| 9092 | Redpanda Kafka |
| 8080 | Redpanda Console |
| 8123 | ClickHouse HTTP |
| 9200 | OpenSearch |
| 9001 | MinIO S3 API |
| 9090 | MinIO Console |
| 5432 | Postgres |

---

## 8. Upgrades

```bash
cd ~/pulsegraph
git pull
make down && make up    # if compose file changed
make bootstrap          # idempotent topic/bucket create
# restart Go services
```

---

## 9. Rollback

1. `git checkout <previous-sha>`  
2. Rebuild binaries / restart units  
3. Compose volumes usually retain data; only `make down-v` wipes state (**destructive**)

---

## 10. Production-readiness checklist (not claimed yet)

- [ ] TLS everywhere  
- [ ] SSH restricted to operator IP  
- [ ] Secrets in OCI Vault or equivalent (not plaintext `.env` long-term)  
- [ ] Backups of MinIO snapshots to OCI Object Storage  
- [ ] Authn on freeze / graph APIs  
- [ ] Multi-AZ — **out of scope** for single-VM pilot
