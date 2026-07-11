import type { CSSProperties } from "react";
import type { GraphEdge, GraphNode } from "./types";

const pos: Record<string, { x: number; y: number }> = {};

export function BlastRadiusGraph({
  nodes,
  edges,
}: {
  nodes: GraphNode[];
  edges: GraphEdge[];
}) {
  nodes.forEach((n) => {
    pos[n.id] = { x: n.x, y: n.y };
  });

  return (
    <svg viewBox="0 0 580 220" width="100%" height="220" role="img" aria-label="Blast radius graph">
      {edges.map((e) => {
        const a = pos[e.from];
        const b = pos[e.to];
        if (!a || !b) return null;
        return (
          <line
            key={`${e.from}-${e.to}`}
            x1={a.x}
            y1={a.y}
            x2={b.x}
            y2={b.y}
            stroke="#95a8a0"
            strokeWidth={1.5}
          />
        );
      })}
      {nodes.map((n) => (
        <g key={n.id} transform={`translate(${n.x},${n.y})`}>
          <circle
            r={n.hot ? 22 : 16}
            fill={n.hot ? "#0d7377" : "#f2f5f3"}
            stroke={n.hot ? "#095456" : "#0b1f2a"}
            strokeWidth={1.5}
            className={n.hot ? "pg-pulse" : undefined}
          />
          <text
            textAnchor="middle"
            y={4}
            fill={n.hot ? "#f2f5f3" : "#0b1f2a"}
            fontSize={10}
            fontFamily="IBM Plex Mono, monospace"
          >
            {n.label}
          </text>
        </g>
      ))}
    </svg>
  );
}

export const panelStyle: CSSProperties = {
  background: "rgba(255,255,255,0.72)",
  border: "1px solid var(--pg-line)",
  padding: "16px 18px",
  backdropFilter: "blur(6px)",
};
