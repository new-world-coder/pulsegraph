package com.pulsegraph.compliance;

import java.util.List;
import java.util.Map;

/**
 * Schema-bound remediation proposal. Free text is not an operation. The hash of this object is what
 * a human approves; a later payload with a different hash is a different plan.
 */
public record RemediationPlan(
    String operation,
    String targetService,
    Map<String, String> parameters,
    String expectedEffect,
    String rollback,
    List<String> evidenceIds) {

  public RemediationPlan {
    parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
  }
}
