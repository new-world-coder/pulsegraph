# PulseGraph Documentation

Design docs, operator manuals, and ADRs for the PulseGraph platform.

## Start here

| If you need to… | Read |
|-----------------|------|
| Create the Oracle pilot VM | **[ORACLE_SETUP.md](ORACLE_SETUP.md)** |
| See what is implemented vs competitors | **[FEATURES.md](FEATURES.md)** |
| Use the product day-to-day | **[USER_MANUAL.md](USER_MANUAL.md)** |
| Deploy locally or on a VM | **[DEPLOYMENT.md](DEPLOYMENT.md)** |
| Run tests & smokes | **[TESTING.md](TESTING.md)** |
| Connect Datadog / OTel / PagerDuty / Rootly… | **[INTEGRATION.md](INTEGRATION.md)** |
| Run a customer litmus | **[PILOT_LITMUS.md](PILOT_LITMUS.md)** |
| Operate / backup / troubleshoot | **[OPERATIONS.md](OPERATIONS.md)** |
| Security expectations for pilots | **[SECURITY.md](SECURITY.md)** |
| Terminology | **[GLOSSARY.md](GLOSSARY.md)** |

---

## Manuals & runbooks

| Document | Status | Description |
|----------|--------|-------------|
| [ORACLE_SETUP.md](ORACLE_SETUP.md) | Active | Always Free ARM VM, VCN ports, you-vs-Cursor handoff |
| [FEATURES.md](FEATURES.md) | Active | Implemented features with competitor citations (BF mapping) |
| [USER_MANUAL.md](USER_MANUAL.md) | Active | Personas, freeze workflow, APIs for investigators |
| [DEPLOYMENT.md](DEPLOYMENT.md) | Active | Local + Oracle deploy, env vars, ports, systemd |
| [TESTING.md](TESTING.md) | Active | Unit, smoke, negative tests |
| [INTEGRATION.md](INTEGRATION.md) | Active | Third-party observability & incident tool wiring |
| [PILOT_LITMUS.md](PILOT_LITMUS.md) | Active | L0–L4 litmus plan and customer one-pager |
| [OPERATIONS.md](OPERATIONS.md) | Active | Health checks, failure modes, backups |
| [SECURITY.md](SECURITY.md) | Pilot | Data handling; what not to claim yet |
| [GLOSSARY.md](GLOSSARY.md) | Living | MTTR, Smartscape, freeze, etc. |

## Design & product

| Document | Status | Description |
|----------|--------|-------------|
| [REQUIREMENTS.md](REQUIREMENTS.md) | Draft v0 | HLRs, MoSCoW, NFRs, open questions |
| [HLD.md](HLD.md) | Draft v0 | High-level design, C4, flows |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Draft v0 | Components, topics, sequences |
| [CONTEXT_GRAPH.md](CONTEXT_GRAPH.md) | Partial impl | Context Graph model & APIs |
| [COMPETITIVE_ANALYSIS.md](COMPETITIVE_ANALYSIS.md) | Draft v0 | Competitor matrix, BF-001–052 backlog |
| [adr/](adr/) | Living | Architecture Decision Records |

## Writing conventions

- **One topic → one markdown file** (prefer new files over mega-docs).  
- **ADRs** follow [MADR](https://adr.github.io/madr/) in `adr/NNNN-title.md`.  
- Use [Mermaid](https://mermaid.js.org/) for diagrams where helpful.  
- Cross-link; do not duplicate long feature matrices — cite [FEATURES.md](FEATURES.md) / [COMPETITIVE_ANALYSIS.md](COMPETITIVE_ANALYSIS.md).  
- Keep **Implemented** claims honest (✅ / 🔶 / 🧾).

## Quick links

- [Root README](../README.md) — overview & local ports  
- [docker-compose.yml](../docker-compose.yml) — data plane  
- [Makefile](../Makefile) — `up`, `bootstrap`, `smoke-ingest`, `smoke-snapshot`
