package com.pulsegraph.warroom;

import com.pulsegraph.compliance.ActionRequest;
import com.pulsegraph.compliance.AgentIdentity;
import com.pulsegraph.compliance.AutonomyMode;
import com.pulsegraph.compliance.DataClassification;
import com.pulsegraph.compliance.RiskTier;
import com.pulsegraph.guardian.GovernanceService;
import com.pulsegraph.guardian.GovernanceService.AuthorizationResult;
import com.pulsegraph.guardian.GovernanceService.ExecutionPermit;
import com.pulsegraph.guardian.PolicyEngine;
import com.pulsegraph.guardian.store.AgentRegistry;
import com.pulsegraph.guardian.store.ApprovalStore;
import com.pulsegraph.guardian.store.AuditLog;
import com.pulsegraph.guardian.store.KillSwitch;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Runs the real guardian classes in this process when no remote URL is configured. */
public final class InProcessGovernancePort implements GovernancePort {

  private final GovernanceService governance;

  public InProcessGovernancePort(GovernanceService governance) {
    this.governance = governance;
  }

  public static InProcessGovernancePort createSeeded() {
    AgentRegistry registry = new AgentRegistry();
    KillSwitch killSwitch = new KillSwitch();
    PolicyEngine engine = new PolicyEngine(registry, killSwitch);
    GovernanceService governance =
        new GovernanceService(registry, engine, new ApprovalStore(), new AuditLog(), killSwitch);
    governance.registerAgent(
        new AgentIdentity(
            WarRoomManager.REMEDIATION_AGENT,
            "PulseGraph Remediation",
            "sre-platform-team",
            "Propose a bounded operation from a confirmed hypothesis.",
            List.of("set-connection-pool", "rollback-release", "scale-replicas", "restart-pod"),
            WarRoomManager.MODEL,
            WarRoomManager.MODEL_VERSION,
            DataClassification.CONFIDENTIAL,
            RiskTier.HIGH,
            AutonomyMode.REQUIRE_APPROVAL,
            "active",
            Instant.now()));
    return new InProcessGovernancePort(governance);
  }

  public GovernanceService governance() {
    return governance;
  }

  @Override
  public Gate authorize(ActionRequest request) {
    AuthorizationResult result = governance.authorize(request);
    return new Gate(
        result.decision().effect().name(),
        result.decision().reason(),
        result.approvalId(),
        request.plan() == null ? request.planHash() : com.pulsegraph.compliance.PlanHasher.hash(request.plan()));
  }

  @Override
  public void record(
      String agentId, String incidentId, String eventType, Map<String, String> attributes) {
    governance.recordEvent(agentId, incidentId, eventType, attributes);
  }

  @Override
  public boolean decide(String approvalId, String approver, boolean granted) {
    return governance
        .decideApproval(approvalId, approver, "incident-commander", granted, "bound to the plan hash")
        .map(approval -> granted && approval.status().name().equals("APPROVED"))
        .orElse(false);
  }

  @Override
  public Permit permit(String approvalId, String planHash, String idempotencyKey) {
    ExecutionPermit permit = governance.permit(approvalId, planHash, idempotencyKey);
    return new Permit(permit.permitted(), permit.replay(), permit.reason());
  }
}
