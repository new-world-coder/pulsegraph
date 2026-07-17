package com.pulsegraph.guardian.web;

import com.pulsegraph.guardian.GovernanceService;
import com.pulsegraph.guardian.model.ApprovalRequest;
import com.pulsegraph.guardian.store.ApprovalStore;
import java.util.Collection;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Human-in-the-loop approval API (dual control / four-eyes). */
@RestController
@RequestMapping("/v1/approvals")
public class ApprovalController {

  private final GovernanceService governance;
  private final ApprovalStore approvals;

  public ApprovalController(GovernanceService governance, ApprovalStore approvals) {
    this.governance = governance;
    this.approvals = approvals;
  }

  @GetMapping
  public Collection<ApprovalRequest> list() {
    return approvals.list();
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApprovalRequest> get(@PathVariable String id) {
    return approvals.find(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
  }

  @PostMapping("/{id}/decide")
  public ResponseEntity<ApprovalRequest> decide(
      @PathVariable String id, @RequestBody DecisionRequest req) {
    return governance
        .decideApproval(id, req.approver(), req.role(), req.granted(), req.comment())
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  public record DecisionRequest(String approver, String role, boolean granted, String comment) {}
}
