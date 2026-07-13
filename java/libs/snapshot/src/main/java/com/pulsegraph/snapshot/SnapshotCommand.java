package com.pulsegraph.snapshot;

public record SnapshotCommand(
    String incidentId,
    String tenantId,
    String trigger,
    String serviceName,
    int preWindowSec,
    int postWindowSec,
    int depth) {

  public SnapshotCommand withDefaults() {
    return new SnapshotCommand(
        incidentId == null || incidentId.isBlank() ? "inc-pending" : incidentId,
        tenantId == null || tenantId.isBlank() ? "default" : tenantId,
        trigger == null || trigger.isBlank() ? "manual" : trigger,
        serviceName == null || serviceName.isBlank() ? "unknown" : serviceName,
        preWindowSec <= 0 ? 600 : preWindowSec,
        postWindowSec < 0 ? 120 : postWindowSec,
        depth <= 0 ? 2 : depth);
  }
}
