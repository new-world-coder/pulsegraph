import { useState } from "react";

const NAV_ITEMS = ["Incidents", "Topology", "Metrics", "RCA"] as const;
type NavItem = (typeof NAV_ITEMS)[number];

export default function App() {
  const [activeNav, setActiveNav] = useState<NavItem>("Incidents");

  return (
    <div style={styles.shell}>
      <header style={styles.header}>
        <div style={styles.logoArea}>
          <span style={styles.logoDot} />
          <span style={styles.logoText}>PulseGraph Console</span>
        </div>
        <nav style={styles.nav}>
          {NAV_ITEMS.map((item) => (
            <button
              key={item}
              style={activeNav === item ? { ...styles.navItem, ...styles.navItemActive } : styles.navItem}
              onClick={() => setActiveNav(item)}
            >
              {item}
            </button>
          ))}
        </nav>
        <div style={styles.statusBadge}>● Connected</div>
      </header>

      <main style={styles.main}>
        <h1 style={styles.pageTitle}>{activeNav}</h1>
        <Placeholder section={activeNav} />
      </main>
    </div>
  );
}

function Placeholder({ section }: { section: NavItem }) {
  const descriptions: Record<NavItem, string> = {
    Incidents: "Active and historical incidents grouped by correlation window. Powered by the correlator service.",
    Topology: "Live service dependency graph built from distributed trace spans. Explore upstream and downstream dependencies.",
    Metrics: "Time-series metric explorer backed by ClickHouse. Filter by service, environment, and time range.",
    RCA: "ML-powered root-cause analysis results. Each incident links to a hypothesis with confidence score and contributing factors.",
  };

  return (
    <div style={styles.placeholder}>
      <div style={styles.placeholderIcon}>⬡</div>
      <p style={styles.placeholderText}>{descriptions[section]}</p>
      <p style={styles.placeholderSub}>
        This view will be implemented once the <code>query-api</code> is wired up.
      </p>
    </div>
  );
}

const styles = {
  shell: {
    fontFamily: "'Inter', system-ui, -apple-system, sans-serif",
    minHeight: "100vh",
    backgroundColor: "#0f1117",
    color: "#e2e8f0",
    display: "flex",
    flexDirection: "column" as const,
  },
  header: {
    display: "flex",
    alignItems: "center",
    padding: "0 24px",
    height: "56px",
    backgroundColor: "#1a1d27",
    borderBottom: "1px solid #2d3147",
    gap: "24px",
  },
  logoArea: {
    display: "flex",
    alignItems: "center",
    gap: "10px",
    marginRight: "12px",
  },
  logoDot: {
    display: "inline-block",
    width: "10px",
    height: "10px",
    borderRadius: "50%",
    backgroundColor: "#6366f1",
    boxShadow: "0 0 8px #6366f1",
  },
  logoText: {
    fontWeight: 600,
    fontSize: "15px",
    letterSpacing: "-0.01em",
    color: "#f1f5f9",
  },
  nav: {
    display: "flex",
    gap: "4px",
    flex: 1,
  },
  navItem: {
    padding: "6px 14px",
    border: "none",
    borderRadius: "6px",
    background: "transparent",
    color: "#94a3b8",
    fontSize: "13px",
    fontWeight: 500,
    cursor: "pointer",
    transition: "background 0.15s",
  },
  navItemActive: {
    backgroundColor: "#2d3147",
    color: "#e2e8f0",
  },
  statusBadge: {
    fontSize: "12px",
    color: "#4ade80",
    fontWeight: 500,
  },
  main: {
    flex: 1,
    padding: "40px 32px",
    maxWidth: "1200px",
    width: "100%",
    margin: "0 auto",
  },
  pageTitle: {
    fontSize: "22px",
    fontWeight: 700,
    color: "#f1f5f9",
    marginBottom: "32px",
    letterSpacing: "-0.02em",
  },
  placeholder: {
    display: "flex",
    flexDirection: "column" as const,
    alignItems: "center",
    justifyContent: "center",
    padding: "80px 40px",
    border: "1px dashed #2d3147",
    borderRadius: "12px",
    textAlign: "center" as const,
    gap: "12px",
  },
  placeholderIcon: {
    fontSize: "40px",
    color: "#6366f1",
    marginBottom: "8px",
  },
  placeholderText: {
    fontSize: "15px",
    color: "#94a3b8",
    maxWidth: "480px",
    lineHeight: 1.6,
    margin: 0,
  },
  placeholderSub: {
    fontSize: "13px",
    color: "#475569",
    margin: 0,
  },
} as const;
