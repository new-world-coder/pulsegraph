import { useEffect, useMemo, useState, type CSSProperties } from "react";
import { BlastRadiusGraph, panelStyle } from "./BlastRadiusGraph";
import {
  DEMO_EDGES,
  DEMO_NODES,
  INITIAL_AGENTS,
  type AgentStep,
  type FreezeRef,
  type Hypothesis,
  type InvestigationState,
} from "./types";

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

async function tryFreeze(service: string): Promise<FreezeRef | null> {
  try {
    const res = await fetch("/freeze", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        service_name: service,
        trigger: "manual",
        pre_window_sec: 600,
        post_window_sec: 120,
        depth: 2,
      }),
    });
    if (!res.ok) return null;
    const data = await res.json();
    return {
      incidentId: data.incident_id ?? "inc-local",
      bundleUri: data.bundle_uri ?? "s3://snapshots/default/inc-local/",
      manifestSha: (data.manifest_sha256 ?? "pending").slice(0, 16) + "…",
      degraded: Boolean(data.degraded),
    };
  } catch {
    return {
      incidentId: "inc-demo-" + Date.now().toString(36),
      bundleUri: "s3://snapshots/default/inc-demo/",
      manifestSha: "demo-sha256-pending…",
      degraded: true,
    };
  }
}

export function WarRoom() {
  const [intent, setIntent] = useState(
    "Checkout p99 latency spiked 40% after the 14:12 redis pool deploy",
  );
  const [state, setState] = useState<InvestigationState>({
    title: "SEV-2 checkout latency",
    severity: "SEV-2",
    service: "checkout",
    intent: "",
    running: false,
    agents: INITIAL_AGENTS,
    hypotheses: [],
    timeline: [],
    freeze: null,
    remediation: null,
    approved: false,
  });

  const activeIndex = useMemo(
    () => state.agents.findIndex((a) => a.status === "running"),
    [state.agents],
  );

  useEffect(() => {
    const style = document.createElement("style");
    style.textContent = `
      @keyframes pgPulse { 0%,100% { transform: scale(1); } 50% { transform: scale(1.06); } }
      .pg-pulse { animation: pgPulse 1.6s ease-in-out infinite; transform-origin: center; }
      @keyframes pgReveal { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: none; } }
      .pg-reveal { animation: pgReveal 0.45s ease-out both; }
      @keyframes pgFreeze { 0% { box-shadow: inset 0 0 0 0 rgba(13,115,119,0.0); } 50% { box-shadow: inset 0 0 0 2px rgba(13,115,119,0.55); } 100% { box-shadow: inset 0 0 0 0 rgba(13,115,119,0.0); } }
      .pg-freeze-flash { animation: pgFreeze 1.2s ease-out 1; }
    `;
    document.head.appendChild(style);
    return () => {
      document.head.removeChild(style);
    };
  }, []);

  async function runInvestigation() {
    if (state.running) return;
    const service = "checkout";
    setState((s) => ({
      ...s,
      running: true,
      intent,
      service,
      hypotheses: [],
      remediation: null,
      approved: false,
      timeline: [`Intent captured · ${new Date().toLocaleTimeString()}`],
      agents: INITIAL_AGENTS.map((a) => ({ ...a, status: "idle" })),
    }));

    const mark = async (id: AgentStep["id"], status: AgentStep["status"], note: string) => {
      setState((s) => ({
        ...s,
        timeline: [...s.timeline, note],
        agents: s.agents.map((a) => (a.id === id ? { ...a, status } : a)),
      }));
      await sleep(status === "running" ? 700 : 350);
    };

    await mark("triage", "running", "Triage agent: classifying as SEV-2 · service=checkout");
    await mark("triage", "done", "Triage complete");

    const freeze = await tryFreeze(service);
    setState((s) => ({
      ...s,
      freeze,
      timeline: [
        ...s.timeline,
        freeze
          ? `Evidence freeze ${freeze.degraded ? "(degraded)" : "ok"} · ${freeze.bundleUri}`
          : "Evidence freeze skipped",
      ],
    }));

    await mark("metrics", "running", "Metrics agent: p99 checkout 820ms → baseline 210ms");
    await mark("metrics", "done", "Metrics anomalies attached");
    await mark("logs", "running", "Logs agent: redis connection pool exhausted × 214");
    await mark("logs", "done", "Log clusters attached");
    await mark("correlate", "running", "Correlate agent: join deploy + graph blast radius");
    await mark("correlate", "done", "Context Graph subgraph depth=2 linked");
    await mark("rca", "running", "RCA agent: ranking hypotheses");

    const hypotheses: Hypothesis[] = [
      {
        id: "h1",
        title: "Redis connection pool misconfiguration from 14:12 deploy",
        confidence: 0.92,
        evidence: [
          "deploy checkout@4.3.2 at T-12m",
          "redis pool wait timeout surge",
          "blast radius: api-gw → checkout → redis",
        ],
      },
      {
        id: "h2",
        title: "orders-db lock contention",
        confidence: 0.31,
        evidence: ["secondary p95 rise", "no matching deploy"],
      },
      {
        id: "h3",
        title: "edge CDN regional brownout",
        confidence: 0.12,
        evidence: ["edge healthy in sibling region"],
      },
    ];
    setState((s) => ({ ...s, hypotheses }));
    await mark("rca", "done", "RCA ranked 3 hypotheses");
    await mark("remediate", "running", "Remediation agent: drafting rollback plan");
    setState((s) => ({
      ...s,
      remediation:
        "Rollback checkout redis pool maxIdle to previous value; verify p99 < 300ms; keep freeze bundle for postmortem.",
    }));
    await mark("remediate", "done", "Remediation awaiting human approval");
    setState((s) => ({ ...s, running: false }));
  }

  return (
    <div>
      <section style={{ marginBottom: 28 }}>
        <p
          style={{
            margin: 0,
            fontFamily: "var(--pg-mono)",
            fontSize: 12,
            letterSpacing: "0.08em",
            textTransform: "uppercase",
            color: "var(--pg-teal-deep)",
          }}
        >
          Agentic War Room
        </p>
        <h1
          style={{
            margin: "6px 0 8px",
            fontSize: "clamp(2.4rem, 5vw, 3.6rem)",
            fontWeight: 700,
            letterSpacing: "-0.03em",
            lineHeight: 1.05,
            color: "var(--pg-ink)",
          }}
        >
          PulseGraph
        </h1>
        <p style={{ margin: 0, maxWidth: 560, color: "var(--pg-ink-soft)", fontSize: 16 }}>
          Multi-agent investigation over your Context Graph — freeze evidence, rank RCA, approve
          remediation. Overlay beside Datadog / Dynatrace / incident.io.
        </p>
      </section>

      <section style={{ ...panelStyle, marginBottom: 18 }} className={state.freeze ? "pg-freeze-flash" : undefined}>
        <label style={{ display: "block", fontSize: 13, fontWeight: 600, marginBottom: 8 }}>
          Intent — describe the incident
        </label>
        <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
          <textarea
            value={intent}
            onChange={(e) => setIntent(e.target.value)}
            rows={2}
            style={{
              flex: "1 1 320px",
              resize: "vertical",
              border: "1px solid var(--pg-line)",
              background: "#fff",
              padding: "10px 12px",
              color: "var(--pg-ink)",
            }}
          />
          <button
            type="button"
            onClick={runInvestigation}
            disabled={state.running}
            style={{
              alignSelf: "stretch",
              minWidth: 160,
              border: "none",
              background: state.running ? "#7a9a9b" : "var(--pg-teal)",
              color: "#fff",
              fontWeight: 600,
              padding: "12px 18px",
            }}
          >
            {state.running ? "Investigating…" : "Open War Room"}
          </button>
        </div>
      </section>

      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(6, minmax(0, 1fr))",
          gap: 8,
          marginBottom: 18,
        }}
      >
        {state.agents.map((a, i) => {
          const active = a.status === "running" || (activeIndex === -1 && a.status === "done" && i === state.agents.length - 1);
          return (
            <div
              key={a.id}
              className={a.status === "done" ? "pg-reveal" : undefined}
              style={{
                ...panelStyle,
                padding: "12px 10px",
                borderColor: a.status === "running" ? "var(--pg-teal)" : "var(--pg-line)",
                background: a.status === "running" ? "rgba(13,115,119,0.1)" : panelStyle.background,
              }}
            >
              <div style={{ fontFamily: "var(--pg-mono)", fontSize: 11, color: "var(--pg-teal-deep)" }}>
                0{i + 1} · {a.status}
              </div>
              <div style={{ fontWeight: 600, fontSize: 14, marginTop: 4 }}>{a.label}</div>
              <div style={{ fontSize: 12, color: "var(--pg-ink-soft)", marginTop: 4 }}>{a.detail}</div>
              {active && a.status === "running" ? (
                <div
                  style={{
                    marginTop: 8,
                    height: 3,
                    background: "var(--pg-teal)",
                    width: "100%",
                    transformOrigin: "left",
                    animation: "pgReveal 0.7s ease-out infinite alternate",
                  }}
                />
              ) : null}
            </div>
          );
        })}
      </div>

      <div
        style={{
          display: "grid",
          gridTemplateColumns: "1.1fr 1fr 0.9fr",
          gap: 14,
          alignItems: "start",
        }}
        className="pg-war-grid"
      >
        <section style={panelStyle}>
          <h2 style={h2}>Blast radius · Context Graph</h2>
          <p style={muted}>checkout highlighted — downstream dependents via topology-builder</p>
          <BlastRadiusGraph nodes={DEMO_NODES} edges={DEMO_EDGES} />
        </section>

        <section style={panelStyle}>
          <h2 style={h2}>RCA hypotheses</h2>
          {state.hypotheses.length === 0 ? (
            <p style={muted}>Run the war room to rank causes with evidence.</p>
          ) : (
            <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "grid", gap: 10 }}>
              {state.hypotheses.map((h) => (
                <li key={h.id} className="pg-reveal" style={{ borderBottom: "1px solid var(--pg-line)", paddingBottom: 10 }}>
                  <div style={{ display: "flex", justifyContent: "space-between", gap: 8 }}>
                    <strong style={{ fontSize: 14 }}>{h.title}</strong>
                    <span style={{ fontFamily: "var(--pg-mono)", fontSize: 12, color: "var(--pg-teal-deep)" }}>
                      {(h.confidence * 100).toFixed(0)}%
                    </span>
                  </div>
                  <div
                    style={{
                      marginTop: 6,
                      height: 4,
                      background: "var(--pg-paper-2)",
                    }}
                  >
                    <div style={{ width: `${h.confidence * 100}%`, height: "100%", background: "var(--pg-teal)" }} />
                  </div>
                  <ul style={{ margin: "8px 0 0", paddingLeft: 16, color: "var(--pg-ink-soft)", fontSize: 12 }}>
                    {h.evidence.map((e) => (
                      <li key={e}>{e}</li>
                    ))}
                  </ul>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section style={panelStyle}>
          <h2 style={h2}>Evidence & remediation</h2>
          {state.freeze ? (
            <div className="pg-reveal" style={{ fontSize: 13, marginBottom: 12 }}>
              <div>
                <span style={label}>Incident</span> <code>{state.freeze.incidentId}</code>
              </div>
              <div style={{ marginTop: 6 }}>
                <span style={label}>Bundle</span> <code style={{ wordBreak: "break-all" }}>{state.freeze.bundleUri}</code>
              </div>
              <div style={{ marginTop: 6 }}>
                <span style={label}>SHA</span> <code>{state.freeze.manifestSha}</code>
              </div>
              {state.freeze.degraded ? (
                <p style={{ color: "var(--pg-amber)", fontSize: 12 }}>Degraded freeze — buffer or topology limited.</p>
              ) : null}
            </div>
          ) : (
            <p style={muted}>Freeze runs automatically when investigation starts.</p>
          )}

          {state.remediation ? (
            <>
              <p style={{ fontSize: 13, lineHeight: 1.5 }}>{state.remediation}</p>
              <button
                type="button"
                disabled={state.approved}
                onClick={() =>
                  setState((s) => ({
                    ...s,
                    approved: true,
                    timeline: [...s.timeline, "Human approved remediation (no auto-exec in pilot)"],
                  }))
                }
                style={{
                  marginTop: 8,
                  width: "100%",
                  border: "1px solid var(--pg-ink)",
                  background: state.approved ? "var(--pg-ok)" : "#fff",
                  color: state.approved ? "#fff" : "var(--pg-ink)",
                  fontWeight: 600,
                  padding: "10px 12px",
                }}
              >
                {state.approved ? "Approved — awaiting executor" : "Approve remediation"}
              </button>
            </>
          ) : (
            <p style={muted}>Remediation appears after RCA.</p>
          )}

          <h3 style={{ ...h2, fontSize: 14, marginTop: 18 }}>Investigation timeline</h3>
          <ol style={{ margin: 0, paddingLeft: 18, fontSize: 12, color: "var(--pg-ink-soft)" }}>
            {state.timeline.map((t) => (
              <li key={t} className="pg-reveal">
                {t}
              </li>
            ))}
          </ol>
        </section>
      </div>

      <style>{`
        @media (max-width: 960px) {
          .pg-war-grid { grid-template-columns: 1fr !important; }
        }
      `}</style>
    </div>
  );
}

const h2: CSSProperties = {
  margin: "0 0 4px",
  fontSize: 15,
  fontWeight: 650,
  letterSpacing: "-0.01em",
};

const muted: CSSProperties = {
  margin: "0 0 10px",
  fontSize: 12,
  color: "var(--pg-ink-soft)",
};

const label: CSSProperties = {
  fontFamily: "var(--pg-mono)",
  fontSize: 11,
  textTransform: "uppercase",
  letterSpacing: "0.06em",
  color: "var(--pg-ink-soft)",
  marginRight: 6,
};
