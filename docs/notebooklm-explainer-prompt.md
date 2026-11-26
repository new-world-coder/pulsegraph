# Notebook LM — PulseGraph explainer

Paste this entire file in as a source. Then start a Video Overview and paste the steering block below into the custom-prompt field.

The video is a spoken briefing. The person on camera is the engineer who built the system, talking to an interviewer or a client. It is not a product advertisement.

---

## Steering block (paste this as the video prompt)

Create an explainer video, about eight minutes, in the first person. I am the engineer who built PulseGraph. I am sitting across from an interviewer or a client. Speak the way a senior engineer speaks in that room: short sentences, concrete mechanisms, no slogans, no hype adjectives, no "revolutionary" or "seamless".

Open on the problem, then the overlay, then the war room, then stop on the governance gate and the blast radius. Let the pictures carry the structure. When you show a control, say whether it is running in the repository or whether it is the target design. Never present a target-design control as already executing in production.

Draw the sketches listed in the source. Prefer simple labelled boxes and arrows on a light background. One idea per frame. Label every arrow with the artefact that moves (envelope, finding, hypothesis, proposal, decision). Include a spoken line for each frame so the picture and the sentence arrive together.

End on a single sentence I can stop on: the manager proposes, the gate refuses or permits against a deny list and a scoped allow list, high-impact changes need a human approval bound to the plan hash, execution is canary-first, and every step is written to a hash-linked audit log. Then stop. Do not add a call to action.

---

## Source narrative

### Who is speaking

I build PulseGraph. It sits beside the observability tools a company already pays for. It does not replace Datadog, Dynatrace, New Relic, or the incident desk. It takes their telemetry, freezes it when an incident opens, and reasons over a graph of which service calls which.

### The problem, in one breath

An incident arrives. Today a person opens the dashboards, reads the logs, forms a theory, and applies a fix. Mean time to recovery is dominated by that human loop, usually at the worst hour. The system I want shortens that loop. Specialist agents gather evidence in parallel. A coordinating agent forms a hypothesis. A remediation agent proposes a change. A governance layer decides whether that change is allowed to run. A language model never touches production on its own.

### What is actually running in the repository today

Say this part plainly, because it is the part I can defend with code.

Telemetry comes in as OpenTelemetry, JSON over HTTP, for traces, metrics, and logs. An ingest gateway checks the envelope and publishes it onto a Kafka-compatible log. A normalizer stamps our labels and forwards the canonical form. A topology builder reads traces and keeps a directed graph of services and the edges between them. That graph can answer a blast-radius query: from this service, who is upstream and downstream. A snapshotter, on command or on a button in the console, freezes a window of metrics, logs, traces, and the subgraph into an object-storage bundle with a SHA-256 manifest. That freeze is the evidence lock. It is the piece most war-room products do not have. They screenshot a dashboard. We keep a queryable bundle.

There is a console called the War Room. Today the investigation rail on that screen is a demonstration. The steps are timers and sample sentences, not agents reading the freeze. The RCA worker is a stub. It returns an unknown cause and a confidence of zero.

There is a separate control plane, a Java service called the compliance guardian. It is real and it is pilot-grade. An agent must be registered, with an owner, a purpose, an approved tool list, a model name, a model version, a data classification, and a maximum autonomy. Before any action, a policy engine decides, and it fails closed. Unknown agent, deny. Kill switch tripped, deny. Tool not on that agent's list, deny. Data classification above the agent's clearance, deny. Observe-only agent, deny. Recommend or approval-required, a human must sign. Critical actions need two different people. Every registration, evaluation, approval, and kill-switch event is appended to a SHA-256 hash chain. Each record includes the previous record's hash, and the chain can be verified. The store is in memory, so a restart drops it. That is the honest limit. The chain algorithm is there. The durability is not.

What I will not claim as running against a customer cluster: live queries to Prometheus, Grafana, ELK, or Splunk, and a canary that mutates Kubernetes. Those adapters are not in the repo. What is running is the control around them. The war-room manager fans four specialists out over an evidence bundle, loops once if confidence is low, and proposes only a schema-bound operation. Logs are sanitised first: secrets are redacted, and instruction-shaped text is kept as data. The guardian loads a deny list and a bounded allow list from `action-policy.json`. An approval is stored against the SHA-256 of that exact plan. A different hash is refused. The canary controller then changes one instance in an in-memory fleet of five, promotes only if the signal improves, and treats a repeated idempotency key as a no-op. The hash chain records the model version on each decision. The chain is in memory, so a restart drops it. If I am asked, I say the gate enforces those decisions, and the fleet is not a cluster.

### Frame 1 — context sketch

Title: Where PulseGraph sits.

Draw four vendor boxes on the left: Datadog, New Relic, Dynatrace, OpenTelemetry. Arrows converge on one box, PulseGraph overlay. Under the overlay, five small boxes in a row: ingest, normalizer, context graph, snapshot freeze, console. To the right, a separate box, incident desk, with a dotted line, not a solid one. Caption under the dotted line: we do not own paging or the status page.

Spoken line: We sit beside the tools you already run. We normalise their telemetry, build a graph, and freeze it when something breaks.

### The war room I am building toward

The centre is a War Room Manager. It holds the incident id, the phase, and the sequence. Around it, five specialists. Each one has one job and one tool surface.

The metrics analyst looks for the spike, the saturation, the latency shift, and returns a structured finding: metric name, value, timestamp, service. The log miner looks for the stack trace, the error code, the first time it appears. The topology agent asks the context graph who calls the failing service. The deployment agent checks recent deploys, feature flags, and configuration, because a large share of incidents start there. The remediation agent does not see the whole evidence pile. It receives the hypothesis and returns a proposed action: the exact operation, the expected effect, and the rollback.

The behaviour is a loop. The manager asks. The analysts return findings as objects, not paragraphs. The manager either asks a sharper question or writes a hypothesis. Low confidence loops. High confidence may propose. That is why it is orchestration and not a pipeline you fire once. There is one place to look when an incident is stuck.

### Frame 2 — hub and spoke

Title: War Room, hub and spoke.

Centre circle: War Room Manager. Five spokes: Metrics, Logs, Topology, Deploy and config, Remediation. Draw the first four spokes as a simultaneous fan-out, same time window, same service slice. Draw the remediation spoke later, and label its input "hypothesis only". A small loop arrow on the manager, labelled "confidence too low, ask again".

Spoken line: One manager holds the sequence. Four analysts gather evidence at once. Remediation sees the hypothesis, not the raw pile.

### Frame 3 — sequence

Title: Ten steps, one incident.

A vertical sequence, one box per step, arrow downward. Steps:

1. Detection. An alert opens the incident and creates a war room id.
2. Fan-out. Analysts dispatched in parallel, same window, same graph slice.
3. Evidence. Structured findings come back.
4. Synthesis. A ranked hypothesis, with the evidence for it and the evidence against it.
5. Sufficiency. Low confidence returns to step 2 with a sharper question.
6. Proposal. Operation, expected effect, rollback.
7. Governance gate. Deny list, allow list, and a human for high-impact classes.
8. Canary. The change hits one instance.
9. Verification. That instance against its peers. Improved promotes. Flat or worse rolls back.
10. Record. Every artefact appended to the hash chain.

Mark steps 1 through 6, 8, 9, and 10 as running against a submitted evidence bundle and an in-memory fleet. Mark the arrow into Prometheus, ELK, and Kubernetes as dashed: that adapter is not in the repo. Step 7 is the guardian. Step 10 is the hash chain, and it is in memory.

Spoken line: The sequence matters, and so does the point where we refuse to continue. We do not guess when the evidence is thin, and we do not execute when the gate says no.

### The three containment models

A manual approval gate is the safest and the slowest. Nothing runs without a person. At three in the morning that person is the bottleneck, and you have rebuilt a manual system with extra steps.

A policy engine is fast and deliberately dumb. Restart a pod, yes. Delete a persistent volume, no. Run a schema migration, never. A confident model cannot talk it out of a refusal. Its weakness is rigidity. A genuinely new fix may sit just outside the list.

A canary checks the change in the world. One instance changes. The others keep serving. If the canary improves, promote. If it is flat or worse, roll back. In a five-instance service the blast radius of a bad change is one fifth of capacity.

The defensible architecture uses all three, at different layers. The policy engine is the hard floor. A human approves high-impact classes, and that approval is bound to the hash of the specific plan, so a signed plan cannot be swapped afterwards. Anything that executes goes canary-first. Analysis may read all five instances. Execution touches one.

### Frame 4 — blast radius of execution

Title: Read five, change one.

Draw five identical instance boxes in a row. Four are labelled serving. One is labelled canary. A wide arrow labelled "analysis" covers all five. A narrow arrow labelled "execution" touches only the canary. Beneath, a small rollback arrow from the canary back to its previous state.

Spoken line: The agents can look at the whole service. The change is allowed to touch one instance, and the other four keep the traffic while we watch.

### The governance layer, said carefully

This is the part I want the interviewer to ask about. It sits between the manager and any execution. Nothing reaches an executor without passing through it.

Logs, stack traces, and alert text are attacker-influenced. A crafted log line can carry an instruction. Evidence is data. It is parsed into typed fields and sanitised before a model sees it. Embedded commands stay inert because they are not in the instruction channel.

The proposed action is checked against a deny list regardless of the model's confidence. Destructive patterns, data stores, identity and access changes, and anything outside the incident's service boundary are refused. Next to the deny list, a scoped allow list of operations with bounded parameters fails closed. If the operation is not on the list, it does not run.

High-impact classes wait for a person. The approval screen shows the evidence, the expected effect, the rollback, and the hash of the plan. The approval event stores that hash. The executor rejects a plan whose hash does not match.

The audit record is hash-linked. Evidence, hypothesis, proposal, gate decision, approver, execution result, and outcome are appended. Each entry carries the previous hash. Delete or edit one record and verification fails. That is the difference between saying it is logged and showing it was not changed.

The gate does not depend on which model wrote the hypothesis. The audit row records the model version that did. If the model changes next month, the floor does not.

### Frame 5 — the gate

Title: Nothing executes without this box.

Left: Manager, holding a proposal card with three lines: operation, effect, rollback, and a short hash. Centre: Governance, drawn as a stack of four filters, top to bottom: sanitised input, deny list, bounded allow list, human approval bound to the hash. Right: two exits. The top exit is labelled Refuse and ends. The bottom exit is labelled Permit and enters the canary box from frame 4. Under the stack, a thin log scrolling downward, each row linked to the previous row by a hash arrow. Label it hash-linked audit.

Spoken line: The manager proposes. The gate refuses or permits. Confidence is not an input to that decision.

### Frame 6 — one incident, told as a story

Use the checkout example, and label it as an illustration of the flow, not as a captured production incident.

Checkout latency jumps after a redis-pool change. The manager opens a war room. Metrics comes back with the latency shift and the time. Logs comes back with the pool-timeout signature and the first occurrence. Topology comes back with api gateway to checkout to redis. Deploy comes back with the pool change in the window. The hypothesis ranks that config change first, and it notes what would contradict it, for example a matching error in a region that did not get the change. Confidence is high enough to propose. The proposal is a bounded config rollback, with the expected effect on latency and the rollback of the rollback. The deny list allows a pool-size change and refuses anything that touches the database or identity. The approval shows the evidence and the plan hash. The canary applies it to one checkout instance. Peer latency stays the baseline. The canary recovers. The controller promotes. The chain has a record of each of those artefacts.

Spoken line: That is the whole system in one incident. Evidence, a hypothesis that can be wrong, a proposal small enough to reverse, a gate that does not trust the model, and a record you can verify.

### What I want them to remember

PulseGraph is the evidence layer: multi-vendor telemetry, a context graph, and an immutable freeze. AutonomOps is the investigation layer on top of that evidence: a manager, specialists, and a gate. The thing I am strongest on is the gate and the blast radius. The manager proposes. The governance layer refuses or permits against a deny list and a scoped allow list. High-impact changes need a human approval bound to the plan hash. Execution is canary-first. Everything is written to a hash-linked audit log.

### Frame 7 — the honesty card, hold for five seconds

Title: Designed, and shipped.

Two columns.

Shipped in the repo: OpenTelemetry ingest. Canonical pipeline. Context graph and blast radius. Incident freeze with a SHA-256 manifest. War-room manager with four specialists, a sufficiency loop, and a schema-bound proposal. Deny list and bounded allow list in action-policy.json. Plan-hash approval. Idempotent canary on an in-memory fleet of five, not a live cluster. Redaction and instruction-stripping before synthesis. Model version on each decision. Hash-chained audit, in memory.

Still say it this way, because it is not a live cluster: the specialists read a submitted evidence bundle. They do not query Prometheus, Grafana, ELK, or Splunk. The canary does not call Kubernetes. The audit chain is lost on restart.

Spoken line: We designed for the gate you just saw. Here is what already enforces a decision, and here is what is still the design. I would rather you ask about the gap than discover it.

### Pictures to produce, checklist

1. Context sketch. Vendors into the overlay. Incident desk off to the side, dotted.
2. Hub and spoke. Manager in the centre. Four analysts in parallel. Remediation later, hypothesis only. Loop arrow for low confidence.
3. Sequence of the ten steps, with the sufficiency loop drawn as a return arrow, and shipped versus target marked on steps 7 and 10.
4. Read five, change one. Analysis across all instances. Execution on one. Rollback arrow.
5. Governance stack. Sanitise, deny, allow, human hash. Refuse path and permit path. Hash-linked audit underneath.
6. Optional swimlane. Columns: Alert, Manager, Analysts, Guardian, Human, Canary. One row per step. Use this if the sequence frame is too tall to read.
7. Honesty card. Two columns. Shipped and target design.

Style for every sketch: light background, dark ink, one accent colour for the gate and for anything that refuses. No logos, no gradients, no decorative icons. Every box has a name. Every arrow has a noun. If a frame needs a legend, the legend is "solid arrow means this runs in the repo, dashed arrow means this is the target design".
