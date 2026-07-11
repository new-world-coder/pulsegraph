# PulseGraph Documentation

This directory contains design documents, architecture decision records, and operational runbooks for the PulseGraph platform.

## Index

| Document | Status | Description |
|----------|--------|-------------|
| [REQUIREMENTS.md](REQUIREMENTS.md) | Draft v0 | Product requirements and acceptance criteria |
| [HLD.md](HLD.md) | Draft v0 | High-level design and component interactions |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Draft v0 | Detailed architecture diagrams and data flows |
| [CONTEXT_GRAPH.md](CONTEXT_GRAPH.md) | Implemented (P0 partial) | Context Graph model, API, RCA/snapshot integration, Smartscape/TKG mapping |
| [COMPETITIVE_ANALYSIS.md](COMPETITIVE_ANALYSIS.md) | Draft v0 | Competitor matrix, BF-001–052 catalog, gap analysis, roadmap |
| [adr/](adr/) | Living | Architecture Decision Records |

## Writing Conventions

- **ADRs** follow [MADR](https://adr.github.io/madr/) format and live in `adr/NNNN-title.md`.
- Use [Mermaid](https://mermaid.js.org/) for embedded diagrams wherever possible.
- Keep each document focused; cross-link liberally rather than duplicating content.

## Quick Links

- [Root README](../README.md) — how to run locally, port reference
- [docker-compose.yml](../docker-compose.yml) — local infrastructure
- [Makefile](../Makefile) — common development targets
