package com.pulsegraph.guardian;

import com.pulsegraph.compliance.ActionPolicy;
import com.pulsegraph.compliance.ActionRequest;
import com.pulsegraph.compliance.AgentIdentity;
import com.pulsegraph.compliance.AutonomyMode;
import com.pulsegraph.compliance.DataClassification;
import com.pulsegraph.compliance.PolicyDecision;
import com.pulsegraph.compliance.RegulatoryControl;
import com.pulsegraph.compliance.RiskTier;
import com.pulsegraph.guardian.store.AgentRegistry;
import com.pulsegraph.guardian.store.KillSwitch;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Evaluates a proposed {@link ActionRequest} against the agent's registered limits and the platform
 * guardrails. The engine is deterministic and side-effect free; it does not execute anything.
 *
 * <p>Order of checks (fail-closed):
 *
 * <ol>
 *   <li>Unknown agent &rarr; DENY.
 *   <li>Kill switch engaged (global or per-agent) &rarr; DENY.
 *   <li>Tool not on the agent's approved list (least privilege) &rarr; DENY.
 *   <li>Deny list, data-store target, service boundary, or scoped allow list &rarr; DENY.
 *   <li>Data classification exceeds the agent's clearance (data governance) &rarr; DENY.
 *   <li>Agent autonomy OBSERVE &rarr; DENY (may not act).
 *   <li>Agent autonomy RECOMMEND / REQUIRE_APPROVAL &rarr; REQUIRE_APPROVAL.
 *   <li>Agent autonomy EXECUTE but the action is high-risk / destructive / customer-impacting in
 *       production &rarr; REQUIRE_APPROVAL (dual control; four-eyes for CRITICAL).
 *   <li>Otherwise &rarr; ALLOW.
 * </ol>
 */
@Component
public class PolicyEngine {

  private final AgentRegistry registry;
  private final KillSwitch killSwitch;
  private final ActionPolicy actionPolicy;

  @Autowired
  public PolicyEngine(AgentRegistry registry, KillSwitch killSwitch, ActionPolicy actionPolicy) {
    this.registry = registry;
    this.killSwitch = killSwitch;
    this.actionPolicy = actionPolicy;
  }

  /** Callers outside Spring use the packaged {@code action-policy.json}. */
  public PolicyEngine(AgentRegistry registry, KillSwitch killSwitch) {
    this(registry, killSwitch, ActionPolicy.loadDefault());
  }

  public PolicyDecision evaluate(ActionRequest req) {
    Optional<AgentIdentity> maybeAgent = registry.find(req.agentId());
    if (maybeAgent.isEmpty()) {
      return PolicyDecision.deny(
          "Unknown agent '" + req.agentId() + "'; register it before requesting actions.",
          List.of(RegulatoryControl.MAS_MINDFORGE_LIFECYCLE, RegulatoryControl.EU_AI_ACT_TECH_DOCS));
    }
    AgentIdentity agent = maybeAgent.get();

    if (killSwitch.isTripped(req.agentId())) {
      return PolicyDecision.deny(
          "Kill switch engaged; all actions for this agent are halted."
              + (killSwitch.reason().isBlank() ? "" : " Reason: " + killSwitch.reason()),
          List.of(RegulatoryControl.EU_AI_ACT_HUMAN_OVERSIGHT, RegulatoryControl.DORA_ICT_RISK_GOVERNANCE));
    }

    if (!agent.isToolApproved(req.tool())) {
      return PolicyDecision.deny(
          "Tool '" + req.tool() + "' is not in the agent's approved tool list (least privilege).",
          List.of(RegulatoryControl.DORA_ICT_RISK_GOVERNANCE, RegulatoryControl.NIST_GOVERN));
    }

    Optional<String> floor = actionPolicy.denial(req);
    if (floor.isPresent()) {
      return PolicyDecision.deny(
          floor.get(),
          List.of(RegulatoryControl.DORA_ICT_RISK_GOVERNANCE, RegulatoryControl.NIST_GOVERN));
    }

    DataClassification requested =
        req.dataClassification() == null ? DataClassification.INTERNAL : req.dataClassification();
    if (requested.ordinal() > agent.maxDataClassification().ordinal()) {
      return PolicyDecision.deny(
          "Action touches "
              + requested
              + " data but the agent is only cleared for "
              + agent.maxDataClassification()
              + " (data governance).",
          List.of(RegulatoryControl.DORA_THIRD_PARTY_RISK, RegulatoryControl.MAS_VERITAS_FAIRNESS));
    }

    if (agent.maxAutonomy() == AutonomyMode.OBSERVE) {
      return PolicyDecision.deny(
          "Agent is OBSERVE-only and may not take actions.",
          List.of(RegulatoryControl.EU_AI_ACT_HUMAN_OVERSIGHT));
    }

    if (agent.maxAutonomy() == AutonomyMode.RECOMMEND
        || agent.maxAutonomy() == AutonomyMode.REQUIRE_APPROVAL) {
      int approvals = approvalsRequired(req);
      return PolicyDecision.requireApproval(
          "Agent autonomy is "
              + agent.maxAutonomy()
              + "; a human must approve before execution.",
          agent.maxAutonomy(),
          approvals,
          List.of(
              RegulatoryControl.EU_AI_ACT_HUMAN_OVERSIGHT, RegulatoryControl.MAS_FEAT_ACCOUNTABILITY));
    }

    // agent.maxAutonomy() == EXECUTE
    boolean highRisk = req.riskOrDefault().atLeast(RiskTier.HIGH);
    boolean sensitiveProdChange = req.isProduction() && (req.destructive() || req.customerImpacting());
    if (highRisk || sensitiveProdChange) {
      int approvals = approvalsRequired(req);
      return PolicyDecision.requireApproval(
          "Autonomous execution is allowed, but this action is "
              + (sensitiveProdChange ? "a sensitive production change" : "high risk")
              + "; dual control applies.",
          AutonomyMode.EXECUTE,
          approvals,
          List.of(
              RegulatoryControl.EU_AI_ACT_HUMAN_OVERSIGHT,
              RegulatoryControl.DORA_INCIDENT_REPORTING,
              RegulatoryControl.MAS_MINDFORGE_OVERSIGHT));
    }

    return PolicyDecision.allow(
        "Within registered autonomy and risk limits; executing autonomously with full audit.",
        AutonomyMode.EXECUTE,
        List.of(RegulatoryControl.EU_AI_ACT_TRACEABILITY, RegulatoryControl.NIST_MANAGE));
  }

  /** CRITICAL actions need four-eyes (2 approvers); everything else needs one. */
  private int approvalsRequired(ActionRequest req) {
    return req.riskOrDefault() == RiskTier.CRITICAL ? 2 : 1;
  }
}
