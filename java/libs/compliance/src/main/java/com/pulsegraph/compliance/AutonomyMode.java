package com.pulsegraph.compliance;

/**
 * Configurable autonomy levels for an agent. Ordered from least to most autonomous so that policy
 * checks can reason about escalation. Maps to EU AI Act Art.14 (human oversight) and MAS FEAT
 * accountability: an agent may never act above its registered {@code maxAutonomy}.
 */
public enum AutonomyMode {
  /** Agent may only read/observe telemetry; it may not propose or take actions. */
  OBSERVE,
  /** Agent may propose hypotheses and remediation, but a human must execute. */
  RECOMMEND,
  /** Agent may execute an action only after explicit human approval. */
  REQUIRE_APPROVAL,
  /** Agent may execute autonomously within policy limits (still subject to guardrails). */
  EXECUTE;

  /** True if this mode grants at least the autonomy of {@code other}. */
  public boolean atLeast(AutonomyMode other) {
    return this.ordinal() >= other.ordinal();
  }
}
