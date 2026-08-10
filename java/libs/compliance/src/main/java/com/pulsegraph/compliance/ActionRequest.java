package com.pulsegraph.compliance;

/**
 * A proposed action an agent wants to take, submitted to the policy engine before execution. The
 * engine returns a {@link PolicyDecision}; nothing is executed by the guardian itself.
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
    String justification) {

  public boolean isProduction() {
    return environment != null
        && (environment.equalsIgnoreCase("prod") || environment.equalsIgnoreCase("production"));
  }

  public RiskTier riskOrDefault() {
    return actionRisk == null ? RiskTier.MEDIUM : actionRisk;
  }
}
