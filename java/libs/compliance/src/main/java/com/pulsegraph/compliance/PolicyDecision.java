package com.pulsegraph.compliance;

import java.util.List;

/**
 * Result of evaluating an {@link ActionRequest}. The effect is one of ALLOW, REQUIRE_APPROVAL, or
 * DENY, accompanied by a human-readable reason, the effective autonomy applied, the number of
 * approvals required (for dual/four-eyes control), and the regulatory controls that justified it.
 */
public record PolicyDecision(
    Effect effect,
    String reason,
    AutonomyMode effectiveMode,
    int requiredApprovals,
    List<RegulatoryControl> controls) {

  public enum Effect {
    ALLOW,
    REQUIRE_APPROVAL,
    DENY
  }

  public PolicyDecision {
    controls = controls == null ? List.of() : List.copyOf(controls);
  }

  public static PolicyDecision allow(String reason, AutonomyMode mode, List<RegulatoryControl> c) {
    return new PolicyDecision(Effect.ALLOW, reason, mode, 0, c);
  }

  public static PolicyDecision requireApproval(
      String reason, AutonomyMode mode, int approvals, List<RegulatoryControl> c) {
    return new PolicyDecision(Effect.REQUIRE_APPROVAL, reason, mode, approvals, c);
  }

  public static PolicyDecision deny(String reason, List<RegulatoryControl> c) {
    return new PolicyDecision(Effect.DENY, reason, AutonomyMode.OBSERVE, 0, c);
  }
}
