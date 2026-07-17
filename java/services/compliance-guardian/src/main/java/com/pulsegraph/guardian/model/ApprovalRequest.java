package com.pulsegraph.guardian.model;

import com.pulsegraph.compliance.ActionRequest;
import com.pulsegraph.compliance.PolicyDecision;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A pending human-in-the-loop approval created when the policy engine returns REQUIRE_APPROVAL.
 * Supports dual control and four-eyes: it stays PENDING until {@code requiredApprovals} distinct
 * approvers have granted it, and any single rejection moves it to REJECTED.
 */
public class ApprovalRequest {

  public enum Status {
    PENDING,
    APPROVED,
    REJECTED,
    EXPIRED
  }

  private final String id;
  private final ActionRequest action;
  private final PolicyDecision decision;
  private final int requiredApprovals;
  private final Instant createdAt;
  private final List<Approval> approvals = new ArrayList<>();
  private Status status = Status.PENDING;
  private Instant decidedAt;

  public ApprovalRequest(
      String id, ActionRequest action, PolicyDecision decision, int requiredApprovals) {
    this.id = id;
    this.action = action;
    this.decision = decision;
    this.requiredApprovals = Math.max(1, requiredApprovals);
    this.createdAt = Instant.now();
  }

  public synchronized Status decide(Approval approval) {
    if (status != Status.PENDING) {
      return status;
    }
    boolean duplicateApprover =
        approvals.stream().anyMatch(a -> a.approver().equalsIgnoreCase(approval.approver()));
    if (duplicateApprover && approval.granted()) {
      // Segregation of duties: the same person cannot count twice toward four-eyes.
      return status;
    }
    approvals.add(approval);
    if (!approval.granted()) {
      status = Status.REJECTED;
      decidedAt = Instant.now();
    } else if (grantedCount() >= requiredApprovals) {
      status = Status.APPROVED;
      decidedAt = Instant.now();
    }
    return status;
  }

  public synchronized long grantedCount() {
    return approvals.stream().filter(Approval::granted).count();
  }

  public String id() {
    return id;
  }

  public ActionRequest action() {
    return action;
  }

  public PolicyDecision decision() {
    return decision;
  }

  public int requiredApprovals() {
    return requiredApprovals;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public synchronized Status status() {
    return status;
  }

  public synchronized Instant decidedAt() {
    return decidedAt;
  }

  public synchronized List<Approval> approvals() {
    return List.copyOf(approvals);
  }
}
