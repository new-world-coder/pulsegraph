# Security & Compliance Notes (Pilot)

**Related:** [INTEGRATION.md](INTEGRATION.md) · [OPERATIONS.md](OPERATIONS.md) · [ORACLE_SETUP.md](ORACLE_SETUP.md)

---

## 1. Data classification

| Data | Sensitivity | Store |
|------|-------------|-------|
| OTLP spans/metrics | Medium | Kafka, freeze JSON |
| Logs | **High** (PII risk) | Kafka buffer, MinIO |
| Topology | Low–Medium | Memory / freeze |
| Snapshot manifests | Medium | MinIO |

---

## 2. Pilot controls (minimum)

- [ ] TLS on public HTTP  
- [ ] SSH key-only, IP allowlist  
- [ ] No public data-plane ports  
- [ ] Staging-only customer data  
- [ ] Written retention period for snapshots (e.g. 30 days)  
- [ ] Access log of who called `/v1/freeze` (add when auth lands)

---

## 3. Not yet implemented (do not claim to customers)

- SOC2 package  
- Customer-managed encryption keys  
- WORM Object Lock (BF-023)  
- Fine-grained RBAC (BF-048)  
- Hard multi-tenant isolation (BF-049)

---

## 4. LLM / Ollama note

If enabling local LLM for RCA narrative: keep prompts on-box; do not send customer logs to public LLM APIs unless contractually approved (see requirements OQ on LLM residency).
