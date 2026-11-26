import { useEffect, useMemo, useState, type CSSProperties } from "react";
import { BlastRadiusGraph, panelStyle } from "./BlastRadiusGraph";
import {
  CHECKOUT_EVIDENCE,
  DEMO_EDGES,
  DEMO_NODES,
  INITIAL_AGENTS,
  type AgentStep,
  type FreezeRef,
  type Hypothesis,
  type InvestigationState,
  type Proposal,
  type WarRoomResponse,
} from "./types";

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
    return null;
  }
}

function agentsFrom(room: WarRoomResponse): AgentStep[] {
  const count = (agent: string) => room.findings.filter((f) => f.agent === agent).length;
  return [
    { id: "metrics", label: "Metrics", detail: `${count("metrics")} structured findings`, status: "done" },
    { id: "logs", label: "Logs", detail: `${count("logs")} structured findings`, status: "done" },
    { id: "topology", label: "Topology", detail: `${count("topology")} dependency findings`, status: "done" },
    { id: "deploy", label: "Deploy", detail: `${count("deploy")} change findings`, status: "done" },
    {
      id: "hypothesis",
      label: "Hypothesis",
      detail: room.hypothesis ? `${Math.round(room.hypothesis.confidence * 100)}% · round ${room.rounds}` : "not formed",
      status: room.hypothesis ? "done" : "idle",
    },
    {
      id: "gate",
      label: "Gate",
      detail: room.gateEffect ?? "no proposal",
      status: room.gateEffect ? "done" : "idle",
    },
  ];
}

export function WarRoom() {
  const [intent, setIntent] = useState(
    "Checkout p99 latency spiked after the redis pool change",
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
    proposal: null,
    planHash: null,
    gateEffect: null,
    gateReason: null,
    roomId: null,
    canaryDetail: null,
    error: null,
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
    setState((s) => ({
      ...s,
      running: true,
      intent,
      service: "checkout",
      hypotheses: [],
      proposal: null,
      planHash: null,
      gateEffect: null,
      gateReason: null,
      roomId: null,
      canaryDetail: null,
      error: null,
      approved: false,
      timeline: [`Intent captured · ${new Date().toLocaleTimeString()}`],
      agents: INITIAL_AGENTS.map((a) => ({ ...a, status: "running" as const })),
    }));

    const freeze = await tryFreeze("checkout");
    let room: WarRoomResponse;
    try {
      const res = await fetch("/war-room/v1/war-rooms", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          trigger: "alert",
          service: "checkout",
          environment: "prod",
          summary: intent,
          evidence: CHECKOUT_EVIDENCE,
        }),
      });
      if (!res.ok) {
        throw new Error(`War room returned ${res.status}`);
      }
      room = (await res.json()) as WarRoomResponse;
    } catch (err) {
      const message = err instanceof Error ? err.message : "War room unreachable";
      setState((s) => ({
        ...s,
        running: false,
        freeze,
        error: message,
        agents: INITIAL_AGENTS,
        timeline: [
          ...s.timeline,
          freeze ? `Evidence freeze · ${freeze.bundleUri}` : "Evidence freeze unavailable",
          `Manager did not run. ${message}`,
        ],
      }));
      return;
    }

    const hypothesis: Hypothesis[] = room.hypothesis
      ? [
          {
            id: room.hypothesis.id,
            title: room.hypothesis.title,
            confidence: room.hypothesis.confidence,
            supports: room.hypothesis.supports,
            contradicts: room.hypothesis.contradicts,
          },
        ]
      : [];

    setState((s) => ({
      ...s,
      running: false,
      freeze,
      roomId: room.id,
      agents: agentsFrom(room),
      hypotheses: hypothesis,
      proposal: room.proposal,
      planHash: room.planHash,
      gateEffect: room.gateEffect,
      gateReason: room.gateReason,
      approved: room.phase === "APPROVED",
      timeline: [
        ...s.timeline,
        freeze ? `Evidence freeze · ${freeze.bundleUri}` : "Evidence freeze unavailable",
        ...room.timeline,
      ],
    }));
  }

  async function approve() {
    if (!state.roomId || state.approved) return;
    const res = await fetch(`/war-room/v1/war-rooms/${state.roomId}/approve`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ approver: "oncall" }),
    });
    if (!res.ok) {
      setState((s) => ({ ...s, error: `Approval returned ${res.status}` }));
      return;
    }
    const room = (await res.json()) as WarRoomResponse;
    setState((s) => ({
      ...s,
      approved: room.phase === "APPROVED",
      gateEffect: room.gateEffect,
      error: null,
      timeline: [...s.timeline, ...room.timeline.slice(-1)],
    }));
  }

  async function runCanary() {
    if (!state.roomId || !state.planHash || !state.approved) return;
    const res = await fetch(`/war-room/v1/war-rooms/${state.roomId}/canary`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        planHash: state.planHash,
        idempotencyKey: state.roomId,
        signal: "IMPROVED",
      }),
    });
    if (!res.ok) {
      setState((s) => ({ ...s, error: `Canary returned ${res.status}` }));
      return;
    }
    const room = (await res.json()) as WarRoomResponse;
    setState((s) => ({
      ...s,
      canaryDetail: room.canary
        ? `${room.canary.outcome} · fleet ${room.canary.fleetSize} · ${room.canary.detail}`
        : "Canary returned no result",
      error: null,
      timeline: [...s.timeline, room.canary?.detail ?? "Canary finished"],
    }));
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
        <p style={{ margin: 0, maxWidth: 640, color: "var(--pg-ink-soft)", fontSize: 16 }}>
          The manager fans out metrics, logs, topology, and deploy, then the guardian gates the
          proposal. The canary runs on an in-memory fleet of five. It does not touch a cluster.
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
        {state.error ? (
          <p style={{ color: "var(--pg-amber)", fontSize: 13, margin: "10px 0 0" }}>{state.error}</p>
        ) : null}
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
          <p style={muted}>checkout highlighted — the topology agent reads the same slice</p>
          <BlastRadiusGraph nodes={DEMO_NODES} edges={DEMO_EDGES} />
        </section>

        <section style={panelStyle}>
          <h2 style={h2}>RCA hypotheses</h2>
          {state.hypotheses.length === 0 ? (
            <p style={muted}>Open the war room to rank a cause with support and contradiction.</p>
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
                  <div style={{ marginTop: 6, height: 4, background: "var(--pg-paper-2)" }}>
                    <div style={{ width: `${h.confidence * 100}%`, height: "100%", background: "var(--pg-teal)" }} />
                  </div>
                  <p style={{ ...muted, marginTop: 8 }}>Supports</p>
                  <ul style={{ margin: 0, paddingLeft: 16, color: "var(--pg-ink-soft)", fontSize: 12 }}>
                    {h.supports.map((e) => (
                      <li key={e}>{e}</li>
                    ))}
                  </ul>
                  <p style={{ ...muted, marginTop: 8 }}>Contradicts</p>
                  <ul style={{ margin: 0, paddingLeft: 16, color: "var(--pg-ink-soft)", fontSize: 12 }}>
                    {h.contradicts.map((e) => (
                      <li key={e}>{e}</li>
                    ))}
                  </ul>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section style={panelStyle}>
          <h2 style={h2}>Gate and canary</h2>
          {state.freeze ? (
            <div className="pg-reveal" style={{ fontSize: 13, marginBottom: 12 }}>
              <div>
                <span style={label}>Freeze</span> <code>{state.freeze.incidentId}</code>
              </div>
              <div style={{ marginTop: 6 }}>
                <span style={label}>SHA</span> <code>{state.freeze.manifestSha}</code>
              </div>
            </div>
          ) : (
            <p style={muted}>A freeze is requested when the investigation starts.</p>
          )}

          {state.proposal ? (
            <ProposalCard
              proposal={state.proposal}
              planHash={state.planHash}
              gateEffect={state.gateEffect}
              gateReason={state.gateReason}
              approved={state.approved}
              canaryDetail={state.canaryDetail}
              onApprove={approve}
              onCanary={runCanary}
            />
          ) : (
            <p style={muted}>A proposal appears only after the sufficiency check passes.</p>
          )}

          <h3 style={{ ...h2, fontSize: 14, marginTop: 18 }}>Investigation timeline</h3>
          <ol style={{ margin: 0, paddingLeft: 18, fontSize: 12, color: "var(--pg-ink-soft)" }}>
            {state.timeline.map((t, i) => (
              <li key={`${i}-${t}`} className="pg-reveal">
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

function ProposalCard({
  proposal,
  planHash,
  gateEffect,
  gateReason,
  approved,
  canaryDetail,
  onApprove,
  onCanary,
}: {
  proposal: Proposal;
  planHash: string | null;
  gateEffect: string | null;
  gateReason: string | null;
  approved: boolean;
  canaryDetail: string | null;
  onApprove: () => void;
  onCanary: () => void;
}) {
  const params = Object.entries(proposal.parameters)
    .map(([k, v]) => `${k}=${v}`)
    .join(", ");
  return (
    <div className="pg-reveal" style={{ fontSize: 13, lineHeight: 1.5 }}>
      <div>
        <span style={label}>Operation</span> <code>{proposal.operation}</code>
        {params ? <span> · {params}</span> : null}
      </div>
      <p style={{ margin: "8px 0" }}>{proposal.expectedEffect}</p>
      <p style={{ margin: "8px 0" }}>
        <span style={label}>Rollback</span> {proposal.rollback}
      </p>
      <p style={{ margin: "8px 0", wordBreak: "break-all" }}>
        <span style={label}>Plan hash</span> <code>{planHash}</code>
      </p>
      <p style={{ margin: "8px 0" }}>
        <span style={label}>Gate</span> {gateEffect}. {gateReason}
      </p>
      <button
        type="button"
        disabled={approved || gateEffect === "DENY"}
        onClick={onApprove}
        style={{
          marginTop: 8,
          width: "100%",
          border: "1px solid var(--pg-ink)",
          background: approved ? "var(--pg-ok)" : "#fff",
          color: approved ? "#fff" : "var(--pg-ink)",
          fontWeight: 600,
          padding: "10px 12px",
        }}
      >
        {approved ? "Approved for this plan hash" : "Approve this plan"}
      </button>
      <button
        type="button"
        disabled={!approved}
        onClick={onCanary}
        style={{
          marginTop: 8,
          width: "100%",
          border: "1px solid var(--pg-teal)",
          background: "#fff",
          color: "var(--pg-teal-deep)",
          fontWeight: 600,
          padding: "10px 12px",
        }}
      >
        Run canary on 1 of 5
      </button>
      {canaryDetail ? <p style={{ margin: "8px 0 0" }}>{canaryDetail}</p> : null}
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
