package com.pulsegraph.compliance;

import java.time.Instant;
import java.util.List;

/**
 * Registered identity of an AI agent. Every agent that participates in investigation or remediation
 * must have an entry: unique id, accountable owner, purpose, the tools it is approved to use, the
 * model/version behind it, the data it may touch, its risk tier, its maximum autonomy, and its
 * deployment status. Maps to EU AI Act technical documentation and MAS/NIST AI inventory obligations.
 */
public record AgentIdentity(
    String id,
    String name,
    String owner,
    String purpose,
    List<String> approvedTools,
    String model,
    String modelVersion,
    DataClassification maxDataClassification,
    RiskTier riskTier,
    AutonomyMode maxAutonomy,
    String deploymentStatus,
    Instant registeredAt) {

  public AgentIdentity {
    approvedTools = approvedTools == null ? List.of() : List.copyOf(approvedTools);
  }

  public boolean isToolApproved(String tool) {
    return tool != null && approvedTools.contains(tool);
  }
}
