package com.pulsegraph.guardian.web;

import com.pulsegraph.compliance.AuditRecord;
import com.pulsegraph.guardian.store.AuditLog;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Immutable, hash-chained audit trail API. */
@RestController
@RequestMapping("/v1/audit")
public class AuditController {

  private final AuditLog audit;

  public AuditController(AuditLog audit) {
    this.audit = audit;
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
}
