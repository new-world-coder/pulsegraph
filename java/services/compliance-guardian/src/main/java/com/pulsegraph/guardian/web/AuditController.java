package com.pulsegraph.guardian.web;

import com.pulsegraph.compliance.AuditRecord;
import com.pulsegraph.guardian.GovernanceService;
import com.pulsegraph.guardian.store.AuditLog;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Immutable, hash-chained audit trail API. */
@RestController
@RequestMapping("/v1/audit")
public class AuditController {

  private final AuditLog audit;
  private final GovernanceService governance;

  public AuditController(AuditLog audit, GovernanceService governance) {
    this.audit = audit;
    this.governance = governance;
  }

  @GetMapping
  public List<AuditRecord> list(@RequestParam(required = false) String incidentId) {
    return incidentId == null ? audit.all() : audit.byIncident(incidentId);
  }

  @GetMapping("/verify")
  public Map<String, Object> verify() {
    int broken = audit.verify();
    return Map.of(
        "records", audit.size(),
        "intact", broken == -1,
        "firstBrokenIndex", broken);
  }

  @PostMapping("/events")
  public ResponseEntity<AuditRecord> record(@RequestBody EventRequest req) {
    try {
      return ResponseEntity.ok(
          governance.recordEvent(req.agentId(), req.incidentId(), req.eventType(), req.attributes()));
    } catch (IllegalArgumentException ex) {
      return ResponseEntity.badRequest().build();
    }
  }

  public record EventRequest(
      String agentId, String incidentId, String eventType, Map<String, String> attributes) {}
}
