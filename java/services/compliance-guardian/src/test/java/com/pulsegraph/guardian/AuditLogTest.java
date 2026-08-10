package com.pulsegraph.guardian;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.pulsegraph.compliance.AuditRecord;
import com.pulsegraph.guardian.store.AuditLog;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditLogTest {

  @Test
  void chainVerifiesWhenIntact() {
    AuditLog log = new AuditLog();
    log.append("agent-1", "AGENT", "agent-1", "ACTION_EVALUATED", Map.of("incidentId", "inc-1"));
    log.append("agent-1", "HUMAN", "alice", "APPROVAL_GRANTED", Map.of("incidentId", "inc-1"));
    assertEquals(-1, log.verify());
    assertEquals(2, log.size());
  }

  @Test
  void consecutiveRecordsLinkByHash() {
    AuditLog log = new AuditLog();
    AuditRecord first = log.append("a", "AGENT", "a", "E1", Map.of());
    AuditRecord second = log.append("a", "AGENT", "a", "E2", Map.of());
    assertEquals(first.hash(), second.prevHash());
    assertNotEquals(first.hash(), second.hash());
  }

  @Test
  void incidentFilterReturnsOnlyMatching() {
    AuditLog log = new AuditLog();
    log.append("a", "AGENT", "a", "E1", Map.of("incidentId", "inc-1"));
    log.append("a", "AGENT", "a", "E2", Map.of("incidentId", "inc-2"));
    assertEquals(1, log.byIncident("inc-1").size());
  }
}
