# Compliant Agent Architecture

**Status:** Implemented (pilot-grade) · Java service `compliance-guardian`
**Code:** [`java/services/compliance-guardian`](../java/services/compliance-guardian) · [`java/libs/compliance`](../java/libs/compliance)

PulseGraph runs autonomous and semi-autonomous agents (RCA, correlation, remediation proposals).
To be usable inside regulated financial institutions, every agent action passes through a **Compliant
Agent control plane** that enforces identity, autonomy limits, human oversight, tamper-evident
auditing, operational safety, and evidence export.

> These controls support regulatory **alignment and auditability**. They are **not** a guarantee of
> legal compliance and do not replace institution-specific legal and supervisory review.

---

## 1. Where it sits

```
  agent (RCA / remediation)                    human operators
          │                                          │
          │ 1. POST /v1/authorize (proposed action)  │
          ▼                                          │
  ┌──────────────────────── compliance-guardian ─────────────────────┐
  │  AgentRegistry → PolicyEngine → KillSwitch                        │
  │        │            │                                             │
  │        │            ├─ ALLOW ──────────────► agent may execute    │
  │        │            ├─ REQUIRE_APPROVAL ───► ApprovalStore ◄───────┼── 2. human decide (four-eyes)
  │        │            └─ DENY                                        │
  │        ▼                                                          │
  │  AuditLog (SHA-256 hash chain, append-only)                       │
  │        │                                                          │
  │        └────────────► EvidencePack (per incident) ───────────────┼── 3. one-click export
  └──────────────────────────────────────────────────────────────────┘
```

The guardian **never executes** the action itself — it authorises, records, and gates. Execution
stays with the calling service, which must respect the returned decision.

---

## 2. Controls implemented

| # | Control | How PulseGraph implements it | API |
|---|---------|------------------------------|-----|
| 1 | **Agent identity & registry** | `AgentIdentity`: id, owner, purpose, approved tools, model + version, data classification, risk tier, max autonomy, deployment status | `POST/GET /v1/agents` |
| 2 | **Policy-based autonomy** | `AutonomyMode` = OBSERVE / RECOMMEND / REQUIRE_APPROVAL / EXECUTE, evaluated per action, environment, and risk | `POST /v1/authorize` |
| 3 | **Least privilege & SoD** | Action denied if the tool is not on the agent's approved list; same approver cannot satisfy four-eyes twice | `PolicyEngine`, `ApprovalRequest` |
| 4 | **Human oversight & dual control** | REQUIRE_APPROVAL opens a pending approval; production/destructive/customer-impacting or HIGH-risk needs sign-off; CRITICAL needs four-eyes (2 approvers) | `POST /v1/approvals/{id}/decide` |
| 5 | **Immutable decision audit trail** | Every registration, evaluation, approval, and kill-switch event is a SHA-256 hash-chained `AuditRecord`; chain is verifiable | `GET /v1/audit`, `GET /v1/audit/verify` |
| 6 | **Evidence-backed explainability** | Each decision carries the reason and the `RegulatoryControl`s that justified it; RCA hypotheses cite Context Graph paths + freeze evidence (see [CONTEXT_GRAPH.md](CONTEXT_GRAPH.md)) | decision payload |
| 7 | **Data governance** | Action denied if its `DataClassification` exceeds the agent's clearance (keeps RESTRICTED banking data out of unapproved models) | `PolicyEngine` |
| 8 | **Model & agent risk management** | Model + version + risk tier recorded per agent in the registry and audit trail (inventory + change trace) | `AgentIdentity` |
| 9 | **Operational safety** | Global kill switch + per-agent circuit breaker; tripped agents are denied immediately | `POST /v1/kill-switch/*` |
| 10 | **Regulatory evidence packs** | One call assembles incident chronology, agents involved, approvals, controls exercised, and audit-chain integrity | `GET /v1/evidence-packs/{incidentId}` |

Pilot-grade note: stores are in-memory. Production hardening (WORM/ledger persistence for the audit
chain, RBAC, secrets, agent security-testing harness, third-party/SBOM register) is on the roadmap —
tracked in [SECURITY.md](SECURITY.md) and [FEATURES.md](FEATURES.md).

---

## 3. Policy decision logic (fail-closed)

Evaluated in order; the first match wins:

1. Unknown agent → **DENY**
2. Kill switch tripped (global or per-agent) → **DENY**
3. Tool not approved for the agent → **DENY** (least privilege)
4. Data classification above the agent's clearance → **DENY** (data governance)
5. Agent `maxAutonomy = OBSERVE` → **DENY** (may not act)
6. Agent `maxAutonomy = RECOMMEND | REQUIRE_APPROVAL` → **REQUIRE_APPROVAL**
7. Agent `maxAutonomy = EXECUTE` **and** (HIGH+ risk **or** production destructive/customer-impacting) → **REQUIRE_APPROVAL** (CRITICAL → four-eyes)
8. Otherwise → **ALLOW** (autonomous, fully audited)

---

## 4. Regulatory mapping

| Framework | Clause(s) | Covered by |
|-----------|-----------|------------|
| **EU AI Act** | Art.11 tech docs, Art.12 traceability, Art.13 transparency, Art.14 human oversight | agent registry, hash-chained audit, decision reasons, approvals + kill switch |
| **DORA** | Art.5 ICT governance, Art.17–19 incident reporting, Art.24–27 resilience testing, Art.28–30 third-party risk | evidence packs, audit trail, kill-switch drills, model/provider inventory |
| **MAS FEAT / Veritas** | Fairness, Ethics, Accountability, Transparency | accountable owner per agent, documented decisions, data-governance checks |
| **MAS Project MindForge / proposed AI risk guidance** | oversight, AI inventory, lifecycle | registry with model + version + risk tier, oversight via approvals |
| **NIST AI RMF / ISO 42001** | Govern–Map–Measure–Manage; auditable AIMS | policy engine + audit trail as management-system evidence |

Regulatory controls are enumerated in code as `RegulatoryControl` so each decision and evidence pack
links back to a specific obligation.

---

## 5. The Context Graph as a *governance graph*

PulseGraph's Context Graph already models services, dependencies, deployments, and incidents. The
Compliant Agent layer extends it into a **governance graph**: each agent decision links to its
`identity → policy → model → data lineage → evidence → approval → tool call → resulting change →
regulatory control`. The audit trail is the edge set; evidence packs are graph queries scoped to an
incident. This is the differentiator for entering regulated financial services and the basis for the
architecture discussion in the AutonomOps outreach.

---

## 6. Try it

See the runnable example in
[`java/services/compliance-guardian/README.md`](../java/services/compliance-guardian/README.md):
register an agent → propose a production action (REQUIRE_APPROVAL) → approve → export the evidence
pack → verify the audit chain.
