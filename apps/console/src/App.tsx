import { useState, type CSSProperties } from "react";
import { WarRoom } from "./WarRoom";
import { BlastRadiusGraph, panelStyle } from "./BlastRadiusGraph";
import { DEMO_EDGES, DEMO_NODES } from "./types";

const NAV = ["War Room", "Incidents", "Context Graph", "Evidence"] as const;
type Nav = (typeof NAV)[number];

export default function App() {
  const [nav, setNav] = useState<Nav>("War Room");

  return (
    <div style={{ minHeight: "100vh", display: "flex", flexDirection: "column" }}>
      <header
        style={{
          display: "flex",
          alignItems: "center",
          gap: 20,
          padding: "14px 28px",
          borderBottom: "1px solid var(--pg-line)",
          background: "rgba(242,245,243,0.9)",
          backdropFilter: "blur(8px)",
          position: "sticky",
          top: 0,
          zIndex: 10,
        }}
      >
        <div style={{ fontWeight: 700, fontSize: 18, letterSpacing: "-0.02em" }}>PulseGraph</div>
        <nav style={{ display: "flex", gap: 4, flex: 1 }}>
          {NAV.map((item) => (
            <button
              key={item}
              type="button"
              onClick={() => setNav(item)}
              style={{
                border: "none",
                background: nav === item ? "var(--pg-ink)" : "transparent",
                color: nav === item ? "#f2f5f3" : "var(--pg-ink-soft)",
                padding: "8px 12px",
                fontWeight: 500,
                fontSize: 13,
              }}
            >
              {item}
            </button>
          ))}
        </nav>
        <span style={{ fontFamily: "var(--pg-mono)", fontSize: 11, color: "var(--pg-ok)" }}>
          overlay · multi-vendor
        </span>
      </header>

      <main style={{ flex: 1, padding: "28px clamp(16px, 4vw, 40px)", maxWidth: 1280, width: "100%", margin: "0 auto" }}>
        {nav === "War Room" && <WarRoom />}
        {nav === "Incidents" && <IncidentsView />}
        {nav === "Context Graph" && <GraphView />}
        {nav === "Evidence" && <EvidenceView />}
      </main>
    </div>
  );
}

function IncidentsView() {
  const rows = [
    { id: "inc-7f2a", sev: "SEV-2", title: "checkout latency", status: "investigating" },
    { id: "inc-19bc", sev: "SEV-3", title: "orders-db slow queries", status: "resolved" },
  ];
  return (
    <div style={panelStyle}>
      <h1 style={{ marginTop: 0, fontSize: 28 }}>Incidents</h1>
      <p style={{ color: "var(--pg-ink-soft)" }}>Fixture list until query-api is live. Open War Room to investigate.</p>
      <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 14 }}>
        <thead>
          <tr style={{ textAlign: "left", fontFamily: "var(--pg-mono)", fontSize: 11 }}>
            <th style={th}>ID</th>
            <th style={th}>Sev</th>
            <th style={th}>Title</th>
            <th style={th}>Status</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.id}>
              <td style={td}>
                <code>{r.id}</code>
              </td>
              <td style={td}>{r.sev}</td>
              <td style={td}>{r.title}</td>
              <td style={td}>{r.status}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function GraphView() {
  return (
    <div style={panelStyle}>
      <h1 style={{ marginTop: 0, fontSize: 28 }}>Context Graph</h1>
      <p style={{ color: "var(--pg-ink-soft)" }}>
        Live data: <code>GET topology-builder:8091/v1/graph/subgraph</code>. Demo topology below.
      </p>
      <BlastRadiusGraph nodes={DEMO_NODES} edges={DEMO_EDGES} />
    </div>
  );
}

function EvidenceView() {
  return (
    <div style={panelStyle}>
      <h1 style={{ marginTop: 0, fontSize: 28 }}>Evidence</h1>
      <p style={{ color: "var(--pg-ink-soft)", maxWidth: 520 }}>
        Immutable freeze bundles live in MinIO (<code>snapshots/&#123;tenant&#125;/&#123;incident&#125;/</code>). Trigger from War
        Room or <code>POST /v1/freeze</code> on snapshotter.
      </p>
      <ul style={{ fontFamily: "var(--pg-mono)", fontSize: 13 }}>
        <li>manifest.json</li>
        <li>metrics.json · logs.json · traces.json</li>
        <li>topology/subgraph.json</li>
      </ul>
    </div>
  );
}

const th: CSSProperties = {
  borderBottom: "1px solid var(--pg-line)",
  padding: "8px 6px",
  fontWeight: 500,
};

const td: CSSProperties = {
  borderBottom: "1px solid var(--pg-line)",
  padding: "10px 6px",
};
