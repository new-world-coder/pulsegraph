package com.pulsegraph.warroom;

import com.pulsegraph.compliance.ActionRequest;
import java.util.Map;

/** The gate the manager must pass through. The manager does not execute. */
public interface GovernancePort {

  Gate authorize(ActionRequest request);

  void record(String agentId, String incidentId, String eventType, Map<String, String> attributes);

  boolean decide(String approvalId, String approver, boolean granted);

  Permit permit(String approvalId, String planHash, String idempotencyKey);

  record Gate(String effect, String reason, String approvalId, String planHash) {}

  record Permit(boolean permitted, boolean replay, String reason) {}
}
