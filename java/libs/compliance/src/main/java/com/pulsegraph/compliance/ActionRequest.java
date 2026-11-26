package com.pulsegraph.compliance;

import java.util.List;

/**
 * A proposed action an agent wants to take, submitted to the policy engine before execution. The
 * engine returns a {@link PolicyDecision}; nothing is executed by the guardian itself.
 *
 * <p>When {@link #plan} is set, the guardian binds {@link #planHash} to that plan. An approval then
 * covers that hash only.
 */
public record ActionRequest(
    String agentId,
    String action,
    String tool,
    String targetService,
    String environment,
    RiskTier actionRisk,
    boolean customerImpacting,
    boolean destructive,
    DataClassification dataClassification,
    String incidentId,
    String justification,
    RemediationPlan plan,
    String planHash,
    List<String> serviceBoundary) {

  public ActionRequest {
    serviceBoundary = serviceBoundary == null ? List.of() : List.copyOf(serviceBoundary);
  }

  public ActionRequest(
      String agentId,
      String action,
      String tool,
      String targetService,
      String environment,
      RiskTier actionRisk,
      boolean customerImpacting,
      boolean destructive,
      DataClassification dataClassification,
      String incidentId,
      String justification) {
    this(
        agentId,
        action,
        tool,
        targetService,
        environment,
        actionRisk,
        customerImpacting,
        destructive,
        dataClassification,
        incidentId,
        justification,
        null,
        null,
        List.of());
  }

  public ActionRequest withPlan(RemediationPlan plan, String planHash, List<String> boundary) {
    return new ActionRequest(
        agentId,
        action,
        tool,
        targetService,
        environment,
        actionRisk,
        customerImpacting,
        destructive,
        dataClassification,
        incidentId,
        justification,
        plan,
        planHash,
        boundary);
  }

  public boolean isProduction() {
    return environment != null
        && (environment.equalsIgnoreCase("prod") || environment.equalsIgnoreCase("production"));
  }

  public RiskTier riskOrDefault() {
    return actionRisk == null ? RiskTier.MEDIUM : actionRisk;
  }
}
