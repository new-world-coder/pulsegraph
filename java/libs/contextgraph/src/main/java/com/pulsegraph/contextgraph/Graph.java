package com.pulsegraph.contextgraph;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Minimal directed graph scaffold — expand toward Go contextgraph parity. */
public final class Graph {
  private final Map<String, Node> nodes = new ConcurrentHashMap<>();
  private final Map<String, Edge> edges = new ConcurrentHashMap<>();

  public void upsertNode(Node node) {
    nodes.merge(node.id(), node, (a, b) -> b);
  }

  public void upsertEdge(Edge edge) {
    edges.put(edge.from() + "->" + edge.to() + ":" + edge.kind(), edge);
  }

  public int nodeCount() {
    return nodes.size();
  }

  public int edgeCount() {
    return edges.size();
  }

  public record Node(String id, String kind, String name) {}

  public record Edge(String from, String to, String kind) {}
}
