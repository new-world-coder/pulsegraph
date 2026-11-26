# compliance-guardian (Java / Spring Boot)

Compliant Agent control plane. Governs every autonomous or semi-autonomous agent action in
PulseGraph so the platform can be operated inside regulated financial institutions.

Controls implemented (pilot-grade, in-memory):

- **Agent identity & registry** — `POST /v1/agents`, `GET /v1/agents`, `GET /v1/agents/{id}`
- **Policy-based autonomy & least privilege** — `POST /v1/authorize` (OBSERVE / RECOMMEND /
  REQUIRE_APPROVAL / EXECUTE, approved-tool and data-classification checks)
- **Human oversight & dual control (four-eyes)** — `GET /v1/approvals`, `POST /v1/approvals/{id}/decide`
- **Immutable, hash-chained audit trail** — `GET /v1/audit`, `GET /v1/audit/verify`
- **Operational-safety kill switch / circuit breaker** — `GET|POST /v1/kill-switch/{engage,release,pause,resume}`
- **One-click regulatory evidence packs** — `GET /v1/evidence-packs/{incidentId}`

```bash
cd java && mvn -pl services/compliance-guardian -am spring-boot:run
```

Health: `GET http://localhost:8096/healthz`

## Example flow

```bash
# 1. Register an agent (RECOMMEND autonomy)
curl -s localhost:8096/v1/agents -H 'content-type: application/json' -d '{
  "id":"agent-rca-primary","name":"RCA","owner":"sre","purpose":"root cause",
  "approvedTools":["restart-service"],"model":"llm","modelVersion":"1",
  "maxDataClassification":"CONFIDENTIAL","riskTier":"HIGH","maxAutonomy":"RECOMMEND"}'

# 2. Agent proposes a production restart -> REQUIRE_APPROVAL + approvalId
curl -s localhost:8096/v1/authorize -H 'content-type: application/json' -d '{
  "agentId":"agent-rca-primary","action":"restart","tool":"restart-service",
  "targetService":"checkout","environment":"prod","actionRisk":"HIGH",
  "customerImpacting":true,"destructive":true,"incidentId":"inc-42"}'

# 3. Human approves
curl -s localhost:8096/v1/approvals/<id>/decide -H 'content-type: application/json' -d '{
  "approver":"alice","role":"incident-commander","granted":true,"comment":"ok"}'

# 4. Export the regulatory evidence pack + verify the audit chain
curl -s localhost:8096/v1/evidence-packs/inc-42
curl -s localhost:8096/v1/audit/verify
```

See [`../../../docs/COMPLIANT_AGENT.md`](../../../docs/COMPLIANT_AGENT.md) for the control-to-regulation
mapping (EU AI Act, DORA, MAS FEAT/Veritas/MindForge, NIST AI RMF, ISO 42001).

> These controls support regulatory alignment and auditability; they are not a substitute for
> institution-specific legal and supervisory review.
