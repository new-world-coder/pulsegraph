package com.pulsegraph.guardian.model;

import com.pulsegraph.compliance.AuditRecord;
import com.pulsegraph.compliance.RegulatoryControl;
import java.time.Instant;
import java.util.List;

/**
 * One-click regulatory evidence pack for an incident: chronology of governed decisions, the
 * approvals granted, the agents involved, the regulatory controls exercised, and whether the audit
 * chain verified intact. Supports DORA incident reporting and EU AI Act record-keeping exports.
 */
public record EvidencePack(
    String incidentId,
    Instant generatedAt,
    List<String> agentsInvolved,
    List<AuditRecord> chronology,
    List<ApprovalSummary> approvals,
    List<RegulatoryControl> controlsExercised,
    boolean auditChainIntact,
    String disclaimer) {

  public record ApprovalSummary(
      String approvalId,
      String action,
      String status,
      int requiredApprovals,
      long grantedApprovals) {}
}
