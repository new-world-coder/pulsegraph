export type AgentId =
  | "triage"
  | "metrics"
  | "logs"
  | "correlate"
  | "rca"
  | "remediate";

export type AgentStatus = "idle" | "running" | "done";

export interface AgentStep {
  id: AgentId;
  label: string;
  detail: string;
  status: AgentStatus;
}

export interface Hypothesis {
  id: string;
  title: string;
  confidence: number;
  evidence: string[];
}

export interface GraphNode {
  id: string;
  x: number;
  y: number;
  label: string;
  hot?: boolean;
}

export interface GraphEdge {
  from: string;
  to: string;
}

export interface FreezeRef {
  incidentId: string;
  bundleUri: string;
  manifestSha: string;
  degraded: boolean;
}

export interface InvestigationState {
  title: string;
  severity: "SEV-1" | "SEV-2" | "SEV-3";
  service: string;
  intent: string;
  running: boolean;
  agents: AgentStep[];
  hypotheses: Hypothesis[];
  timeline: string[];
  freeze: FreezeRef | null;
  remediation: string | null;
  approved: boolean;
}

export const INITIAL_AGENTS: AgentStep[] = [
  {
    id: "triage",
    label: "Triage",
    detail: "Classify severity & owning service",
    status: "idle",
  },
  {
    id: "metrics",
    label: "Metrics",
    detail: "Latency / error / saturation anomalies",
    status: "idle",
  },
  {
    id: "logs",
    label: "Logs",
    detail: "Error clusters & rare signatures",
    status: "idle",
  },
  {
    id: "correlate",
    label: "Correlate",
    detail: "Join signals + Context Graph",
    status: "idle",
  },
  {
    id: "rca",
    label: "RCA",
    detail: "Rank causal hypotheses",
    status: "idle",
  },
  {
    id: "remediate",
    label: "Remediate",
    detail: "Propose fix — human approval",
    status: "idle",
  },
];

export const DEMO_NODES: GraphNode[] = [
  { id: "edge", x: 40, y: 110, label: "edge" },
  { id: "api", x: 160, y: 60, label: "api-gw" },
  { id: "checkout", x: 280, y: 110, label: "checkout", hot: true },
  { id: "redis", x: 400, y: 50, label: "redis" },
  { id: "orders", x: 400, y: 170, label: "orders" },
  { id: "db", x: 520, y: 110, label: "orders-db" },
];

export const DEMO_EDGES: GraphEdge[] = [
  { from: "edge", to: "api" },
  { from: "api", to: "checkout" },
  { from: "checkout", to: "redis" },
  { from: "checkout", to: "orders" },
  { from: "orders", to: "db" },
];
