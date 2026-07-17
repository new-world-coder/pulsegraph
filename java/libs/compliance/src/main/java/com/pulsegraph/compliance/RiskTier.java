package com.pulsegraph.compliance;

/** Risk tier for an agent or an individual action. Drives dual-control and validation depth. */
public enum RiskTier {
  LOW,
  MEDIUM,
  HIGH,
  CRITICAL;

  public boolean atLeast(RiskTier other) {
    return this.ordinal() >= other.ordinal();
  }
}
