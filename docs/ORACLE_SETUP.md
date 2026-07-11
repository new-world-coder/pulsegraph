# Oracle Cloud Setup Guide (PulseGraph Pilot)

**Audience:** Founders / SREs standing up the first public pilot  
**Status:** Active runbook  
**Related:** [DEPLOYMENT.md](DEPLOYMENT.md) · [PILOT_LITMUS.md](PILOT_LITMUS.md) · [TESTING.md](TESTING.md)

---

## 1. Why Oracle for the pilot

Per the RCA deployment strategy analysis, **Oracle Cloud Always Free** is the preferred first home for PulseGraph:

| Benefit | Detail |
|---------|--------|
| Cost | Always Free tier can host a meaningful pilot at **$0/month** |
| Shape | **VM.Standard.A1.Flex** (Ampere ARM): up to **4 OCPU / 24 GB RAM** |
| Fit | Enough for Docker Compose + Go services + optional local LLM (Ollama) |
| Simplicity | One VM, no Kubernetes until customer #5–#10 |

PulseGraph’s local stack maps cleanly onto a single ARM VM. Do **not** start with OKE (Oracle Kubernetes Engine) for the pilot.

---

## 2. Oracle services used (and not used)

### Use now

| Service | Purpose |
|---------|---------|
| **Identity (IAM)** | Tenancy, users, API keys (optional) |
| **Networking – VCN** | Default VCN or a small custom VCN |
| **Networking – Security List / NSG** | Ingress: SSH 22, HTTP 80, HTTPS 443 (optional OTLP 4318) |
| **Compute – Instance** | `VM.Standard.A1.Flex` ARM shape |
| **Block Volume** | Boot volume (expand if Always Free disk is tight) |
| **Public IP** | Ephemeral or reserved public IPv4 |
| **Object Storage** (optional Phase 2) | Off-box backup of MinIO snapshot bundles |

### Defer

| Service | Why defer |
|---------|-----------|
| **OKE** | Ops overhead; compose is enough for pilot |
| **API Gateway / Functions** | Premature |
| **Autonomous Database** | We already run Postgres in Compose |
| **Streaming / Kafka (OCI)** | We run Redpanda in Compose |
| **Monitoring (OCI Monitoring)** | Nice later; dogfood PulseGraph first |

---

## 3. What you do vs what Cursor does

| Step | Who | Where |
|------|-----|--------|
| Create Oracle Cloud account + identity verification | **You** | Browser → [cloud.oracle.com](https://cloud.oracle.com) |
| Request A1 ARM quota if missing | **You** | Console → Limits / Support |
| Create VCN rules + VM + SSH key | **You** (this guide) | Oracle Console |
| Confirm `ssh` works | **You** once | Local terminal |
| Install Docker, clone repo, `make up`, run services | **Agent in Cursor** | SSH into the VM after you paste `user@ip` |
| Paste secrets / API keys | **You** when prompted | Chat / `.env` on VM (never commit) |

The agent **cannot** complete Oracle signup, 2FA, or payment-method verification for you.

---

## 4. Click-by-click: create the Always Free ARM VM

### 4.1 Account

1. Open [https://cloud.oracle.com](https://cloud.oracle.com) → **Sign up**.
2. Complete email + mobile verification.
3. Choose a **home region** close to you or your first customer (e.g. `ap-mumbai-1`, `us-ashburn-1`). Changing home region later is painful — pick carefully.
4. Wait until the tenancy shows **Active**.

### 4.2 Confirm Ampere A1 quota

1. Menu → **Governance & Administration** → **Limits, Quotas and Usage**.
2. Service: **Compute** → look for `standard-a1-core-count` / `standard-a1-memory-count`.
3. If available cores = 0, open a **Service Limit Increase** request for Always Free A1 (often approved automatically within hours).

### 4.3 Networking (quick path)

Use the **default VCN** created with the tenancy, or:

1. **Networking** → **Virtual Cloud Networks** → Create VCN (VCN Wizard: “Create VCN with Internet Connectivity”).
2. Edit the public subnet **Security List** (or NSG) ingress:

| Source | Protocol | Port | Purpose |
|--------|----------|------|---------|
| Your IP `/32` (preferred) or `0.0.0.0/0` (demo only) | TCP | 22 | SSH |
| `0.0.0.0/0` | TCP | 80 | HTTP (Caddy/nginx) |
| `0.0.0.0/0` | TCP | 443 | HTTPS |
| Your IP or VPN only | TCP | 4318 | OTLP (optional; lock down) |
| — | — | Do **not** expose 9092, 8123, 9200, 9001, 5432 publicly | Data plane stays private |

### 4.4 SSH key

On your laptop:

```bash
ssh-keygen -t ed25519 -f ~/.ssh/pulsegraph_oci -C "pulsegraph-pilot"
cat ~/.ssh/pulsegraph_oci.pub
```

Keep the private key offline; you will paste the **public** key into the Console.

### 4.5 Create the instance

1. **Compute** → **Instances** → **Create instance**.
2. Name: `pulsegraph-pilot`.
3. Placement: your home AD.
4. Image: **Canonical Ubuntu 22.04** (ARM) *or* Oracle Linux 8/9 (ARM). Ubuntu is usually easier for Docker docs.
5. Shape: **Change shape** → **Ampere** → `VM.Standard.A1.Flex` → **4 OCPU / 24 GB** (or less if quota is shared).
6. Networking: public subnet + **Assign public IPv4**.
7. Add SSH keys: paste `pulsegraph_oci.pub`.
8. Create. Wait until state = **Running**.
9. Copy the **Public IP**.

### 4.6 First SSH

Ubuntu image:

```bash
ssh -i ~/.ssh/pulsegraph_oci ubuntu@<PUBLIC_IP>
```

Oracle Linux image:

```bash
ssh -i ~/.ssh/pulsegraph_oci opc@<PUBLIC_IP>
```

If SSH fails: check Security List, correct user, and that the instance is Running.

---

## 5. Hand-off checklist for Cursor

Paste into chat when ready:

```text
Oracle VM ready
user: ubuntu|opc
host: <PUBLIC_IP>
ssh key: ~/.ssh/pulsegraph_oci
os: Ubuntu 22.04 ARM | Oracle Linux
region: <region>
```

Then ask: **“Deploy PulseGraph pilot on this VM.”**

The agent will typically:

1. Install Docker Engine + Compose plugin (ARM).
2. Clone `https://github.com/new-world-coder/pulsegraph` (or your fork).
3. Copy `.env.example` → `.env`.
4. `make up && make bootstrap`.
5. Build/run MVP Go services.
6. Optionally install Caddy for TLS.
7. Run smoke tests from [TESTING.md](TESTING.md).

---

## 6. Resource sizing (recommended)

| Workload | RAM guidance on 24 GB box |
|----------|---------------------------|
| Compose deps only (Redpanda, CH, OS, MinIO, PG) | ~8–12 GB |
| + Go MVP services | +1–2 GB |
| + Ollama 8B quantized | +6–10 GB |
| Leave headroom | ≥2 GB free |

If Ollama OOMs, run LLM on a **customer laptop/server** (transcript Option B/C) and keep the Oracle VM for the control plane only.

---

## 7. Cost & quota gotchas

- Always Free A1 capacity is **regional and contested** — create the VM early and leave it running (or reclaim carefully).
- Stopping an Always Free instance for long periods can risk reclaim in some accounts; prefer leave-on for active pilots.
- Public `0.0.0.0/0` on SSH is a demo anti-pattern; lock SSH to your IP.
- Never expose Kafka / ClickHouse / OpenSearch / MinIO / Postgres to the internet.

---

## 8. Teardown

```bash
# On VM — stop app stack
cd ~/pulsegraph && make down

# In Console — Terminate instance if abandoning pilot
# Optionally delete custom VCN
```

Object Storage buckets (if used) must be emptied before bucket delete.

---

## 9. Next documents

- After VM works → [DEPLOYMENT.md](DEPLOYMENT.md)  
- Prove pipeline → [TESTING.md](TESTING.md) / [PILOT_LITMUS.md](PILOT_LITMUS.md)  
- Wire customer telemetry → [INTEGRATION.md](INTEGRATION.md)
