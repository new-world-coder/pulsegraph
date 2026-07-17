package com.pulsegraph.guardian;

import com.pulsegraph.compliance.ActionRequest;
import com.pulsegraph.compliance.AgentIdentity;
import com.pulsegraph.compliance.AuditRecord;
import com.pulsegraph.compliance.PolicyDecision;
import com.pulsegraph.compliance.RegulatoryControl;
import com.pulsegraph.guardian.model.Approval;
import com.pulsegraph.guardian.model.ApprovalRequest;
import com.pulsegraph.guardian.model.EvidencePack;
import com.pulsegraph.guardian.store.AgentRegistry;
import com.pulsegraph.guardian.store.ApprovalStore;
import com.pulsegraph.guardian.store.AuditLog;
import com.pulsegraph.guardian.store.KillSwitch;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Ties the control plane together. Every governed operation writes an immutable audit record, so the
 * audit trail is the single source of truth for evidence packs.
 */
@Service
public class GovernanceService {

  private final AgentRegistry registry;
  private final PolicyEngine policyEngine;
  private final ApprovalStore approvals;
  private final AuditLog audit;
  private final KillSwitch killSwitch;

  public GovernanceService(
      AgentRegistry registry,
      PolicyEngine policyEngine,
      ApprovalStore approvals,
      AuditLog audit,
      KillSwitch killSwitch) {
    this.registry = registry;
    this.policyEngine = policyEngine;
    this.approvals = approvals;
    this.audit = audit;
    this.killSwitch = killSwitch;
  }

  public AgentIdentity registerAgent(AgentIdentity agent) {
    AgentIdentity stored = registry.register(agent);
    audit.append(
        stored.id(),
        "HUMAN",
        stored.owner(),
        "AGENT_REGISTERED",
        Map.of(
            "name", stored.name(),
            "model", String.valueOf(stored.model()),
            "modelVersion", String.valueOf(stored.modelVersion()),
            "riskTier", String.valueOf(stored.riskTier()),
            "maxAutonomy", String.valueOf(stored.maxAutonomy())));
    return stored;
  }

  /**
   * Evaluates a proposed action, records the decision, and — when approval is required — opens a
   * pending {@link ApprovalRequest}. Returns both the decision and any created approval id.
   */
  public AuthorizationResult authorize(ActionRequest req) {
    PolicyDecision decision = policyEngine.evaluate(req);

    Map<String, Object> attrs = new HashMap<>();
    attrs.put("action", req.action());
    attrs.put("tool", req.tool());
    attrs.put("targetService", req.targetService());
    attrs.put("environment", req.environment());
    attrs.put("effect", decision.effect().name());
    attrs.put("reason", decision.reason());
    if (req.incidentId() != null) {
      attrs.put("incidentId", req.incidentId());
    }
    attrs.put("controls", controlNames(decision.controls()));
    audit.append(req.agentId(), "AGENT", req.agentId(), "ACTION_EVALUATED", attrs);

    String approvalId = null;
    if (decision.effect() == PolicyDecision.Effect.REQUIRE_APPROVAL) {
      ApprovalRequest ar =
          approvals.save(
              new ApprovalRequest(
                  "apr-" + UUID.randomUUID(), req, decision, decision.requiredApprovals()));
      approvalId = ar.id();
      Map<String, Object> apAttrs = new HashMap<>();
      apAttrs.put("approvalId", approvalId);
      apAttrs.put("requiredApprovals", decision.requiredApprovals());
      if (req.incidentId() != null) {
        apAttrs.put("incidentId", req.incidentId());
      }
      audit.append(req.agentId(), "SYSTEM", "guardian", "APPROVAL_REQUESTED", apAttrs);
    }
    return new AuthorizationResult(decision, approvalId);
  }

  public Optional<ApprovalRequest> decideApproval(
      String approvalId, String approver, String role, boolean granted, String comment) {
    Optional<ApprovalRequest> maybe = approvals.find(approvalId);
    if (maybe.isEmpty()) {
      return Optional.empty();
    }
    ApprovalRequest ar = maybe.get();
    ApprovalRequest.Status status =
        ar.decide(new Approval(approver, role, granted, comment, Instant.now()));

    Map<String, Object> attrs = new HashMap<>();
    attrs.put("approvalId", approvalId);
    attrs.put("granted", granted);
    attrs.put("role", role);
    attrs.put("status", status.name());
    if (ar.action().incidentId() != null) {
      attrs.put("incidentId", ar.action().incidentId());
    }
    audit.append(
        ar.action().agentId(),
        "HUMAN",
        approver,
        granted ? "APPROVAL_GRANTED" : "APPROVAL_REJECTED",
        attrs);
    return Optional.of(ar);
  }

  public void engageGlobalKill(String actor, String reason) {
    killSwitch.engageGlobal(reason);
    audit.append(null, "HUMAN", actor, "KILL_SWITCH_ENGAGED", Map.of("scope", "global", "reason", nz(reason)));
  }

  public void releaseGlobalKill(String actor) {
    killSwitch.releaseGlobal();
    audit.append(null, "HUMAN", actor, "KILL_SWITCH_RELEASED", Map.of("scope", "global"));
  }

  public void pauseAgent(String actor, String agentId, String reason) {
    killSwitch.pauseAgent(agentId);
    audit.append(agentId, "HUMAN", actor, "AGENT_PAUSED", Map.of("reason", nz(reason)));
  }

  public void resumeAgent(String actor, String agentId) {
    killSwitch.resumeAgent(agentId);
    audit.append(agentId, "HUMAN", actor, "AGENT_RESUMED", Map.of());
  }

  public EvidencePack buildEvidencePack(String incidentId) {
    List<AuditRecord> chronology = audit.byIncident(incidentId);

    LinkedHashSet<String> agents = new LinkedHashSet<>();
    LinkedHashSet<RegulatoryControl> controls = new LinkedHashSet<>();
    for (AuditRecord r : chronology) {
      if (r.agentId() != null) {
        agents.add(r.agentId());
      }
      Object c = r.attributes().get("controls");
      if (c instanceof List<?> list) {
        for (Object o : list) {
          asControl(o.toString()).ifPresent(controls::add);
        }
      }
    }

    List<EvidencePack.ApprovalSummary> approvalSummaries =
        approvals.list().stream()
            .filter(a -> incidentId.equals(a.action().incidentId()))
            .map(
                a ->
                    new EvidencePack.ApprovalSummary(
                        a.id(),
                        a.action().action(),
                        a.status().name(),
                        a.requiredApprovals(),
                        a.grantedCount()))
            .toList();

    boolean intact = audit.verify() == -1;

    return new EvidencePack(
        incidentId,
        Instant.now(),
        List.copyOf(agents),
        chronology,
        approvalSummaries,
        List.copyOf(controls),
        intact,
        "Supports regulatory alignment and auditability; not a substitute for institution-specific "
            + "legal and supervisory review.");
  }

  private static List<String> controlNames(List<RegulatoryControl> controls) {
    return controls.stream().map(Enum::name).toList();
  }

  private static Optional<RegulatoryControl> asControl(String name) {
    try {
      return Optional.of(RegulatoryControl.valueOf(name));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private static String nz(String s) {
    return s == null ? "" : s;
  }

  /** Combined result of an authorization evaluation. */
  public record AuthorizationResult(PolicyDecision decision, String approvalId) {}
}
