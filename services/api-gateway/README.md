# api-gateway

Go edge gateway in front of `query-api`. Handles JWT/OIDC authentication, RBAC authorization (REQ-F-082), per-tenant rate limits, and reverse-proxy / WebSocket upgrade to the query plane.

## Responsibilities

- Terminate external HTTPS for the console and API clients
- Validate short-lived JWTs (access ≤ 1 h) and inject `tenant_id` / actor claims
- Enforce role checks (`viewer`, `responder`, `platform-engineer`, `tenant-admin`, `system-admin`)
- Proxy REST and WebSocket traffic to `query-api`
- Emit OTel spans and Prometheus metrics for every request

## Planned Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/healthz` | Liveness |
| `GET` | `/v1/ready` | Readiness (downstream query-api reachability) |
| `*` | `/v1/**` | Authenticated reverse-proxy to `query-api` |
| `WS` | `/v1/incidents/stream` | Live incident feed (proxied) |

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `8088` | HTTP listen port |
| `QUERY_API_URL` | `http://localhost:8090` | Upstream query-api base URL |
| `JWKS_URI` | — | OIDC JWKS endpoint (production) |
| `API_GATEWAY_AUTH_REQUIRED` | unset (auth off locally) | Set `true` to require `Authorization` header in the stub |
| `LOG_LEVEL` | `info` | Structured log level |

## Running Locally

```bash
go run ./cmd/server/main.go
```

## Building

```bash
go build -o bin/api-gateway ./cmd/server/
```

See [HLD §9](../../docs/HLD.md) and [ARCHITECTURE §2.8](../../docs/ARCHITECTURE.md).
