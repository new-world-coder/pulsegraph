package com.pulsegraph.warroom;

public record CanaryResult(
    String idempotencyKey,
    String outcome,
    boolean applied,
    boolean replay,
    int fleetSize,
    int canaryIndex,
    String detail) {

  public CanaryResult asReplay() {
    return new CanaryResult(
        idempotencyKey,
        outcome,
        false,
        true,
        fleetSize,
        canaryIndex,
        "Replay. The canary was not applied again. " + detail);
  }
}
