package com.pulsegraph.guardian;

import com.pulsegraph.compliance.ActionRequest;
import com.pulsegraph.compliance.AgentIdentity;
import com.pulsegraph.compliance.AuditRecord;
import com.pulsegraph.compliance.PlanHasher;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

/**
 * Ties the control plane together. Every governed operation writes an immutable audit record, so the
 * audit trail is the single source of truth for evidence packs.
 */
@Service
public class GovernanceService {

  static final Set<String> RECORDABLE_EVENTS =
      Set.of(
          "DETECTION",
          "FAN_OUT",
          "EVIDENCE",
          "HYPOTHESIS",
          "SUFFICIENCY",
          "PROPOSAL",
          "CANARY",
          "VERIFICATION");

  private final AgentRegistry registry;
  private final PolicyEngine policyEngine;
  private final ApprovalStore approvals;
  private final AuditLog audit;
  private final KillSwitch killSwitch;
  private final ConcurrentMap<String, ExecutionPermit> permits = new ConcurrentHashMap<>();

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
    ActionRequest bound = bindPlan(req);
    if (bound == null) {
      PolicyDecision mismatch =
          PolicyDecision.deny(
              "Plan hash does not match the canonical proposal. The approved plan cannot be swapped.",
              List.of(RegulatoryControl.EU_AI_ACT_TRACEABILITY, RegulatoryControl.NIST_GOVERN));
      auditDecision(req, mismatch);
      return new AuthorizationResult(mismatch, null);
    }

    PolicyDecision decision = policyEngine.evaluate(bound);
    auditDecision(bound, decision);

    String approvalId = null;
    if (decision.effect() == PolicyDecision.Effect.REQUIRE_APPROVAL) {
      ApprovalRequest ar =
          approvals.save(
              new ApprovalRequest(
                  "apr-" + UUID.randomUUID(), bound, decision, decision.requiredApprovals()));
      approvalId = ar.id();
      Map<String, Object> apAttrs = new HashMap<>();
      apAttrs.put("approvalId", approvalId);
      apAttrs.put("requiredApprovals", decision.requiredApprovals());
      if (bound.incidentId() != null) {
        apAttrs.put("incidentId", bound.incidentId());
      }
      if (bound.planHash() != null) {
        apAttrs.put("planHash", bound.planHash());
      }
      stampModel(bound.agentId(), apAttrs);
      audit.append(bound.agentId(), "SYSTEM", "guardian", "APPROVAL_REQUESTED", apAttrs);
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
    if (ar.action().planHash() != null) {
      attrs.put("planHash", ar.action().planHash());
    }
    stampModel(ar.action().agentId(), attrs);
    audit.append(
        ar.action().agentId(),
        "HUMAN",
        approver,
        granted ? "APPROVAL_GRANTED" : "APPROVAL_REJECTED",
        attrs);
    return Optional.of(ar);
  }

  /**
   * Authorises execution of an already-approved plan. The plan hash must match the hash stored at
   * approval time. A repeated idempotency key returns the original permit and does not authorise a
   * second execution.
   */
  public ExecutionPermit permit(String approvalId, String planHash, String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      return new ExecutionPermit(false, false, planHash, idempotencyKey, "Idempotency key is required.");
    }
    ExecutionPermit prior = permits.get(idempotencyKey);
    if (prior != null) {
      audit.append(
          null,
          "SYSTEM",
          "guardian",
          "EXECUTION_REPLAY",
          Map.of("idempotencyKey", idempotencyKey, "planHash", nz(prior.planHash())));
      return new ExecutionPermit(
          prior.permitted(),
          true,
          prior.planHash(),
          idempotencyKey,
          "Idempotent replay. The action was not applied again.");
    }
    Optional<ApprovalRequest> maybe = approvals.find(approvalId);
    if (maybe.isEmpty()) {
      return new ExecutionPermit(false, false, planHash, idempotencyKey, "Unknown approval.");
    }
    ApprovalRequest ar = maybe.get();
    if (ar.status() != ApprovalRequest.Status.APPROVED) {
      return new ExecutionPermit(
          false, false, planHash, idempotencyKey, "Approval is " + ar.status() + ".");
    }
    String boundHash = ar.action().planHash();
    if (boundHash == null || boundHash.isBlank() || !boundHash.equals(planHash)) {
      return new ExecutionPermit(
          false,
          false,
          planHash,
          idempotencyKey,
          "Plan hash does not match the approved plan.");
    }
    ExecutionPermit ok =
        new ExecutionPermit(true, false, boundHash, idempotencyKey, "Permitted for a single execution.");
    ExecutionPermit raced = permits.putIfAbsent(idempotencyKey, ok);
    if (raced != null) {
      return new ExecutionPermit(
          raced.permitted(),
          true,
          raced.planHash(),
          idempotencyKey,
          "Idempotent replay. The action was not applied again.");
    }
    Map<String, Object> attrs = new HashMap<>();
    attrs.put("approvalId", approvalId);
    attrs.put("planHash", boundHash);
    attrs.put("idempotencyKey", idempotencyKey);
    if (ar.action().incidentId() != null) {
      attrs.put("incidentId", ar.action().incidentId());
    }
    stampModel(ar.action().agentId(), attrs);
    audit.append(ar.action().agentId(), "SYSTEM", "guardian", "EXECUTION_PERMITTED", attrs);
    return ok;
  }

  /** Appends an investigation artefact onto the same hash chain as gate decisions. */
  public AuditRecord recordEvent(
      String agentId, String incidentId, String eventType, Map<String, String> attributes) {
    if (eventType == null || !RECORDABLE_EVENTS.contains(eventType)) {
      throw new IllegalArgumentException("Event type is not recordable: " + eventType);
    }
    Map<String, Object> attrs = new HashMap<>();
    if (attributes != null) {
      attributes.forEach(attrs::put);
    }
    if (incidentId != null) {
      attrs.put("incidentId", incidentId);
    }
    stampModel(agentId, attrs);
    return audit.append(
        agentId, "AGENT", agentId == null ? "war-room" : agentId, eventType, attrs);
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

  private ActionRequest bindPlan(ActionRequest req) {
    if (req.plan() == null) {
      return req;
    }
    String expected = PlanHasher.hash(req.plan());
    if (req.planHash() != null && !req.planHash().isBlank() && !req.planHash().equals(expected)) {
      return null;
    }
    return req.withPlan(req.plan(), expected, req.serviceBoundary());
  }

  private void auditDecision(ActionRequest req, PolicyDecision decision) {
    Map<String, Object> attrs = new HashMap<>();
    attrs.put("action", nz(req.action()));
    attrs.put("tool", nz(req.tool()));
    attrs.put("targetService", nz(req.targetService()));
    attrs.put("environment", nz(req.environment()));
    attrs.put("effect", decision.effect().name());
    attrs.put("reason", decision.reason());
    if (req.incidentId() != null) {
      attrs.put("incidentId", req.incidentId());
    }
    if (req.planHash() != null) {
      attrs.put("planHash", req.planHash());
    }
    attrs.put("controls", controlNames(decision.controls()));
    stampModel(req.agentId(), attrs);
    audit.append(req.agentId(), "AGENT", req.agentId(), "ACTION_EVALUATED", attrs);
  }

  private void stampModel(String agentId, Map<String, Object> attrs) {
    if (agentId == null) {
      return;
    }
    registry
        .find(agentId)
        .ifPresent(
            agent -> {
              attrs.put("model", String.valueOf(agent.model()));
              attrs.put("modelVersion", String.valueOf(agent.modelVersion()));
            });
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

  /** One-shot permission to execute an approved plan. {@code replay} means do not apply again. */
  public record ExecutionPermit(
      boolean permitted, boolean replay, String planHash, String idempotencyKey, String reason) {}
}
